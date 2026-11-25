package com.example.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import com.example.model.AudioQualityMode
import com.example.model.WalkieSettings
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Manages live low-latency microphone capture (AudioRecord) and voice playback (AudioTrack).
 * Never writes audio to disk and strictly enforces half-duplex (capture and playback are mutually exclusive).
 */
class WalkieAudioEngine(
  private val context: Context,
  private val onAudioInterruption: () -> Unit,
) {
  private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
  private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private val _txAudioLevel = MutableStateFlow(0f)
  val txAudioLevel: StateFlow<Float> = _txAudioLevel.asStateFlow()

  private val _rxAudioLevel = MutableStateFlow(0f)
  val rxAudioLevel: StateFlow<Float> = _rxAudioLevel.asStateFlow()

  private var captureJob: Job? = null
  private var audioRecord: AudioRecord? = null
  private var echoCanceler: AcousticEchoCanceler? = null
  private var noiseSuppressor: NoiseSuppressor? = null

  private var audioTrack: AudioTrack? = null
  private var activePlaybackSampleRate: Int = 0
  private val playbackLock = Any()

  private var focusRequest: AudioFocusRequest? = null
  private var hasAudioFocus = false

  private val audioFocusListener =
    AudioManager.OnAudioFocusChangeListener { focusChange ->
      when (focusChange) {
        AudioManager.AUDIOFOCUS_LOSS,
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
          hasAudioFocus = false
          stopCapture()
          stopPlayback()
          onAudioInterruption()
        }
        AudioManager.AUDIOFOCUS_GAIN -> {
          hasAudioFocus = true
        }
      }
    }

  @SuppressLint("MissingPermission")
  fun startCapture(
    settings: WalkieSettings,
    onEncodedFrame: (mode: AudioQualityMode, payload: ByteArray) -> Unit,
    onCaptureError: (String) -> Unit,
  ): Boolean {
    stopPlayback()
    stopCapture()

    if (!requestAudioFocus()) {
      onCaptureError("Audio focus unavailable due to another active call or media session.")
      return false
    }

    val quality = settings.audioQuality
    val sampleRate = quality.sampleRate
    val frameSamples = sampleRate / 50 // 20 ms frame (320 samples @ 16kHz, 160 @ 8kHz)
    val minBufferBytes =
      AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
      )
    if (minBufferBytes <= 0) {
      onCaptureError("Microphone format ($sampleRate Hz) is not supported on this hardware.")
      return false
    }

    val bufferSizeBytes = max(minBufferBytes * 2, frameSamples * 4)
    val record =
      createAudioRecord(sampleRate, bufferSizeBytes)
        ?: run {
          onCaptureError("Failed to initialize microphone AudioRecord.")
          return false
        }

    audioRecord = record
    attachAudioEffects(record.audioSessionId)

    try {
      record.startRecording()
    } catch (e: SecurityException) {
      releaseCaptureResources()
      onCaptureError("Microphone permission denied.")
      return false
    } catch (e: IllegalStateException) {
      releaseCaptureResources()
      onCaptureError("Microphone busy or unavailable: ${e.message ?: "illegal state"}")
      return false
    }

    captureJob =
      engineScope.launch {
        val pcmBuffer = ShortArray(frameSamples)
        try {
          while (isActive && record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            val readCount = readFullFrame(record, pcmBuffer, frameSamples)
            if (readCount <= 0) continue

            // Apply mic gain and calculate normalized RMS level
            val rms = processGainAndMeasureRms(pcmBuffer, readCount, settings.micGain)
            val normalizedLevel = min(1f, rms * 3.2f)
            _txAudioLevel.value = normalizedLevel

            // Noise gate / squelch suppression
            if (rms < settings.noiseGateThreshold) {
              pcmBuffer.fill(0, 0, readCount)
            }

            val encodedPayload =
              if (quality.isAdpcm) {
                ImaAdpcmCodec.encode(pcmBuffer, readCount)
              } else {
                ImaAdpcmCodec.shortsToBytes(pcmBuffer, readCount)
              }
            onEncodedFrame(quality, encodedPayload)
          }
        } catch (_: Throwable) {
          // Capture loop exited cleanly or interrupted
        } finally {
          _txAudioLevel.value = 0f
          releaseCaptureResources()
        }
      }

    return true
  }

  fun stopCapture() {
    captureJob?.cancel()
    captureJob = null
    _txAudioLevel.value = 0f
    releaseCaptureResources()
  }

  fun preparePlayback(mode: AudioQualityMode, speakerphoneEnabled: Boolean) {
    stopCapture()
    requestAudioFocus()
    applyAudioRouting(speakerphoneEnabled)

    synchronized(playbackLock) {
      if (audioTrack != null && activePlaybackSampleRate == mode.sampleRate) {
        if (audioTrack?.playState != AudioTrack.PLAYSTATE_PLAYING) {
          audioTrack?.play()
        }
        return
      }
      releasePlaybackLocked()

      val minBufferBytes =
        AudioTrack.getMinBufferSize(
          mode.sampleRate,
          AudioFormat.CHANNEL_OUT_MONO,
          AudioFormat.ENCODING_PCM_16BIT,
        )
      if (minBufferBytes <= 0) return

      val bufferBytes = max(minBufferBytes * 2, (mode.sampleRate / 50) * 4)
      val attributes =
        AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
          .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
          .build()

      val format =
        AudioFormat.Builder()
          .setSampleRate(mode.sampleRate)
          .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
          .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
          .build()

      val track =
        AudioTrack.Builder()
          .setAudioAttributes(attributes)
          .setAudioFormat(format)
          .setTransferMode(AudioTrack.MODE_STREAM)
          .setBufferSizeInBytes(bufferBytes)
          .build()

      if (track.state == AudioTrack.STATE_INITIALIZED) {
        track.play()
        audioTrack = track
        activePlaybackSampleRate = mode.sampleRate
      } else {
        track.release()
      }
    }
  }

  fun playEncodedFrame(mode: AudioQualityMode, payload: ByteArray, speakerphoneEnabled: Boolean) {
    if (payload.isEmpty()) return
    val pcmSamples =
      if (mode.isAdpcm) {
        ImaAdpcmCodec.decode(payload)
      } else {
        ImaAdpcmCodec.bytesToShorts(payload)
      }
    if (pcmSamples.isEmpty()) return

    // Measure RX RMS for visual indicator
    val rms = computeRms(pcmSamples, pcmSamples.size)
    _rxAudioLevel.value = min(1f, rms * 3.2f)

    synchronized(playbackLock) {
      if (audioTrack == null || activePlaybackSampleRate != mode.sampleRate) {
        preparePlayback(mode, speakerphoneEnabled)
      }
      val track = audioTrack ?: return
      try {
        if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
          track.play()
        }
        track.write(pcmSamples, 0, pcmSamples.size)
      } catch (_: IllegalStateException) {
        // Track was released mid-stream
      }
    }
  }

  fun playRogerBeep(speakerphoneEnabled: Boolean) {
    engineScope.launch {
      applyAudioRouting(speakerphoneEnabled)
      val sampleRate = 16000
      val toneSamples = generateRogerBeepPcm(sampleRate)
      val attributes =
        AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
          .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
          .build()
      val format =
        AudioFormat.Builder()
          .setSampleRate(sampleRate)
          .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
          .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
          .build()
      var beepTrack: AudioTrack? = null
      try {
        beepTrack =
          AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(toneSamples.size * 2)
            .build()
        if (beepTrack.state == AudioTrack.STATE_INITIALIZED) {
          beepTrack.write(toneSamples, 0, toneSamples.size)
          beepTrack.play()
          Thread.sleep(110)
        }
      } catch (_: Throwable) {
        // Non-critical audio cue
      } finally {
        try {
          beepTrack?.stop()
          beepTrack?.release()
        } catch (_: Throwable) {}
      }
    }
  }

  fun stopPlayback() {
    _rxAudioLevel.value = 0f
    synchronized(playbackLock) {
      releasePlaybackLocked()
    }
  }

  fun applyAudioRouting(speakerphoneEnabled: Boolean) {
    try {
      audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val targetType =
          if (speakerphoneEnabled) AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
          else AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
        val targetDevice =
          audioManager.availableCommunicationDevices.firstOrNull { it.type == targetType }
        if (targetDevice != null) {
          audioManager.setCommunicationDevice(targetDevice)
        } else {
          audioManager.clearCommunicationDevice()
        }
      } else {
        @Suppress("DEPRECATION")
        audioManager.isSpeakerphoneOn = speakerphoneEnabled
      }
    } catch (_: Throwable) {
      // Some devices restrict communication device changes when not in call
    }
  }

  fun releaseAll() {
    stopCapture()
    stopPlayback()
    abandonAudioFocus()
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        audioManager.clearCommunicationDevice()
      } else {
        @Suppress("DEPRECATION")
        audioManager.isSpeakerphoneOn = false
      }
      audioManager.mode = AudioManager.MODE_NORMAL
    } catch (_: Throwable) {}
  }

  @SuppressLint("MissingPermission")
  private fun createAudioRecord(sampleRate: Int, bufferSizeBytes: Int): AudioRecord? {
    val sources =
      listOf(MediaRecorder.AudioSource.VOICE_COMMUNICATION, MediaRecorder.AudioSource.MIC)
    for (source in sources) {
      try {
        val candidate =
          AudioRecord(
            source,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSizeBytes,
          )
        if (candidate.state == AudioRecord.STATE_INITIALIZED) {
          return candidate
        }
        candidate.release()
      } catch (_: SecurityException) {
        return null
      } catch (_: Throwable) {
        // Try fallback audio source
      }
    }
    return null
  }

  private fun attachAudioEffects(audioSessionId: Int) {
    try {
      if (AcousticEchoCanceler.isAvailable()) {
        echoCanceler = AcousticEchoCanceler.create(audioSessionId)?.apply { enabled = true }
      }
      if (NoiseSuppressor.isAvailable()) {
        noiseSuppressor = NoiseSuppressor.create(audioSessionId)?.apply { enabled = true }
      }
    } catch (_: Throwable) {
      // Hardware audio effects optional
    }
  }

  private fun readFullFrame(record: AudioRecord, buffer: ShortArray, targetSamples: Int): Int {
    var totalRead = 0
    while (totalRead < targetSamples) {
      val read = record.read(buffer, totalRead, targetSamples - totalRead)
      if (read <= 0) {
        return if (totalRead > 0) totalRead else read
      }
      totalRead += read
    }
    return totalRead
  }

  private fun processGainAndMeasureRms(
    samples: ShortArray,
    count: Int,
    gain: Float,
  ): Float {
    var sumSquares = 0.0
    for (i in 0 until count) {
      val amplified = (samples[i] * gain).toInt().coerceIn(-32768, 32767)
      samples[i] = amplified.toShort()
      val norm = amplified / 32768.0
      sumSquares += norm * norm
    }
    return sqrt(sumSquares / max(1, count)).toFloat()
  }

  private fun computeRms(samples: ShortArray, count: Int): Float {
    var sumSquares = 0.0
    for (i in 0 until count) {
      val norm = abs(samples[i].toInt()) / 32768.0
      sumSquares += norm * norm
    }
    return sqrt(sumSquares / max(1, count)).toFloat()
  }

  private fun generateRogerBeepPcm(sampleRate: Int): ShortArray {
    val firstToneSamples = (sampleRate * 0.04).toInt()
    val secondToneSamples = (sampleRate * 0.05).toInt()
    val total = firstToneSamples + secondToneSamples
    val pcm = ShortArray(total)
    for (i in 0 until total) {
      val freq = if (i < firstToneSamples) 1280.0 else 1760.0
      val localIdx = if (i < firstToneSamples) i else i - firstToneSamples
      val localLen = if (i < firstToneSamples) firstToneSamples else secondToneSamples
      val env = min(1.0, min(localIdx / 40.0, (localLen - localIdx) / 40.0))
      val sample = sin(2.0 * PI * freq * i / sampleRate) * 7000.0 * env
      pcm[i] = sample.toInt().toShort()
    }
    return pcm
  }

  private fun releaseCaptureResources() {
    try {
      echoCanceler?.release()
    } catch (_: Throwable) {}
    echoCanceler = null

    try {
      noiseSuppressor?.release()
    } catch (_: Throwable) {}
    noiseSuppressor = null

    try {
      audioRecord?.stop()
    } catch (_: Throwable) {}
    try {
      audioRecord?.release()
    } catch (_: Throwable) {}
    audioRecord = null
  }

  private fun releasePlaybackLocked() {
    try {
      audioTrack?.pause()
      audioTrack?.flush()
      audioTrack?.stop()
    } catch (_: Throwable) {}
    try {
      audioTrack?.release()
    } catch (_: Throwable) {}
    audioTrack = null
    activePlaybackSampleRate = 0
  }

  private fun requestAudioFocus(): Boolean {
    if (hasAudioFocus) return true
    val result =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val attrs =
          AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val req =
          AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener(audioFocusListener)
            .build()
        focusRequest = req
        audioManager.requestAudioFocus(req)
      } else {
        @Suppress("DEPRECATION")
        audioManager.requestAudioFocus(
          audioFocusListener,
          AudioManager.STREAM_VOICE_CALL,
          AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE,
        )
      }
    hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    return hasAudioFocus
  }

  private fun abandonAudioFocus() {
    hasAudioFocus = false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
      focusRequest = null
    } else {
      @Suppress("DEPRECATION")
      audioManager.abandonAudioFocus(audioFocusListener)
    }
  }
}
