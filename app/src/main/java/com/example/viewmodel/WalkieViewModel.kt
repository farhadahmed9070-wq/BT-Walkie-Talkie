package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.WalkieApplication
import com.example.bluetooth.BluetoothPermissionHelper
import com.example.data.LastDeviceRecord
import com.example.model.AudioQualityMode
import com.example.model.BluetoothAvailability
import com.example.model.ChannelState
import com.example.model.ConnectedDeviceInfo
import com.example.model.ConnectionState
import com.example.model.DiscoveredBtDevice
import com.example.model.PermissionStatus
import com.example.model.WalkieSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WalkieUiState(
  val availability: BluetoothAvailability = BluetoothAvailability.AVAILABLE,
  val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
  val channelState: ChannelState = ChannelState.STANDBY,
  val isScanning: Boolean = false,
  val pairedDevices: List<DiscoveredBtDevice> = emptyList(),
  val nearbyDevices: List<DiscoveredBtDevice> = emptyList(),
  val connectedDevice: ConnectedDeviceInfo? = null,
  val lastDevice: LastDeviceRecord? = null,
  val activeRemoteCodec: AudioQualityMode? = null,
  val txAudioLevel: Float = 0f,
  val rxAudioLevel: Float = 0f,
  val lastError: String? = null,
  val permissions: PermissionStatus = PermissionStatus(),
  val settings: WalkieSettings = WalkieSettings(),
)

class WalkieViewModel(application: Application) : AndroidViewModel(application) {
  private val app = application as WalkieApplication
  private val btManager = app.bluetoothWalkieManager
  private val settingsRepo = app.settingsRepository

  private val permissionStatusFlow =
    MutableStateFlow(BluetoothPermissionHelper.getPermissionStatus(application))

  val statusEvents = btManager.statusEvents

  val uiState: StateFlow<WalkieUiState> =
    combine(
      btManager.availability,
      btManager.connectionState,
      btManager.channelState,
      btManager.isScanning,
      btManager.pairedDevices,
      btManager.nearbyDevices,
      btManager.connectedDevice,
      settingsRepo.lastDeviceFlow,
      btManager.activeRemoteCodec,
      btManager.audioEngine.txAudioLevel,
      btManager.audioEngine.rxAudioLevel,
      btManager.lastError,
      permissionStatusFlow,
      settingsRepo.settingsFlow,
    ) { values ->
      @Suppress("UNCHECKED_CAST")
      WalkieUiState(
        availability = values[0] as BluetoothAvailability,
        connectionState = values[1] as ConnectionState,
        channelState = values[2] as ChannelState,
        isScanning = values[3] as Boolean,
        pairedDevices = values[4] as List<DiscoveredBtDevice>,
        nearbyDevices = values[5] as List<DiscoveredBtDevice>,
        connectedDevice = values[6] as ConnectedDeviceInfo?,
        lastDevice = values[7] as LastDeviceRecord?,
        activeRemoteCodec = values[8] as AudioQualityMode?,
        txAudioLevel = values[9] as Float,
        rxAudioLevel = values[10] as Float,
        lastError = values[11] as String?,
        permissions = values[12] as PermissionStatus,
        settings = values[13] as WalkieSettings,
      )
    }.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue =
        WalkieUiState(
          permissions = BluetoothPermissionHelper.getPermissionStatus(application),
        ),
    )

  init {
    viewModelScope.launch {
      settingsRepo.settingsFlow.collect { settings ->
        btManager.updateSettings(settings)
      }
    }
    viewModelScope.launch {
      btManager.incomingTransmissionStartEvents.collect {
        if (uiState.value.settings.vibrationEnabled) {
          vibratePulse(durationMs = 28L)
        }
      }
    }
  }

  fun refreshPermissionsAndBluetooth() {
    permissionStatusFlow.value = BluetoothPermissionHelper.getPermissionStatus(getApplication())
    btManager.refreshState()
  }

  fun startScan() {
    refreshPermissionsAndBluetooth()
    btManager.startScan()
  }

  fun stopScan() {
    btManager.stopScan()
  }

  fun pairDevice(address: String) {
    btManager.pairDevice(address)
  }

  fun connectToDevice(device: DiscoveredBtDevice) {
    btManager.connectToDevice(device.address, device.name)
  }

  fun reconnectLastDevice() {
    val last = uiState.value.lastDevice
    btManager.reconnect(last?.address, last?.name)
  }

  fun disconnect() {
    btManager.disconnect()
  }

  fun onPushToTalkPressed() {
    val started = btManager.startTransmitting()
    if (uiState.value.settings.vibrationEnabled) {
      vibratePulse(if (started) 38L else 18L)
    }
  }

  fun onPushToTalkReleased() {
    val wasTransmitting = uiState.value.channelState == ChannelState.TRANSMITTING
    btManager.stopTransmitting()
    if (wasTransmitting && uiState.value.settings.vibrationEnabled) {
      vibratePulse(22L)
    }
  }

  fun clearError() {
    btManager.clearError()
  }

  fun setVibrationEnabled(enabled: Boolean) {
    viewModelScope.launch {
      settingsRepo.updateSettings { it.copy(vibrationEnabled = enabled) }
    }
  }

  fun setRogerBeepEnabled(enabled: Boolean) {
    viewModelScope.launch {
      settingsRepo.updateSettings { it.copy(rogerBeepEnabled = enabled) }
    }
  }

  fun setSpeakerphoneEnabled(enabled: Boolean) {
    viewModelScope.launch {
      settingsRepo.updateSettings { it.copy(speakerphoneEnabled = enabled) }
    }
  }

  fun setAudioQuality(mode: AudioQualityMode) {
    viewModelScope.launch {
      settingsRepo.updateSettings { it.copy(audioQuality = mode) }
    }
  }

  fun setMicGain(gain: Float) {
    viewModelScope.launch {
      settingsRepo.updateSettings { it.copy(micGain = gain.coerceIn(0.8f, 2.0f)) }
    }
  }

  fun setNoiseGate(threshold: Float) {
    viewModelScope.launch {
      settingsRepo.updateSettings { it.copy(noiseGateThreshold = threshold.coerceIn(0f, 0.1f)) }
    }
  }

  fun setAutoListenEnabled(enabled: Boolean) {
    viewModelScope.launch {
      settingsRepo.updateSettings { it.copy(autoListenEnabled = enabled) }
    }
  }

  private fun vibratePulse(durationMs: Long) {
    try {
      val ctx = getApplication<Application>()
      val vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          val manager =
            ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
          manager?.defaultVibrator
        } else {
          @Suppress("DEPRECATION")
          ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return

      if (!vibrator.hasVibrator()) return
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(
          VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE),
        )
      } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(durationMs)
      }
    } catch (_: Throwable) {
      // Vibration optional on devices without haptics
    }
  }
}
