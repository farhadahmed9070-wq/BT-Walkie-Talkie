package com.example.audio

import kotlin.math.max
import kotlin.math.min

/**
 * Self-contained IMA-ADPCM (4:1 compression) encoder and decoder.
 * Each encoded block embeds its initial 16-bit predictor and step index in a 4-byte header
 * so every Bluetooth RFCOMM audio packet can be decoded independently without drift.
 */
object ImaAdpcmCodec {

  private val INDEX_TABLE =
    intArrayOf(-1, -1, -1, -1, 2, 4, 6, 8, -1, -1, -1, -1, 2, 4, 6, 8)

  private val STEP_TABLE =
    intArrayOf(
      7, 8, 9, 10, 11, 12, 13, 14, 16, 17, 19, 21, 23, 25, 28, 31, 34, 37, 41, 45, 50, 55, 60, 66,
      73, 80, 88, 97, 107, 118, 130, 143, 157, 173, 190, 209, 230, 253, 279, 307, 337, 371, 408,
      449, 494, 544, 598, 658, 724, 796, 876, 963, 1060, 1166, 1282, 1411, 1552, 1707, 1878, 2066,
      2272, 2499, 2749, 3024, 3327, 3660, 4026, 4428, 4871, 5358, 5894, 6484, 7132, 7845, 8630,
      9493, 10442, 11487, 12635, 13899, 15289, 16818, 18500, 20350, 22385, 24623, 27086, 29794,
      32767,
    )

  /**
   * Encodes [sampleCount] 16-bit PCM samples into a byte array:
   * Bytes 0..1: initial predictor (little-endian Short)
   * Byte 2: initial step index (0..88)
   * Byte 3: odd-sample flag (1 if sampleCount is odd, else 0) — wait: we store sampleCount - 1 low byte or odd flag,
   * actually let's store `(sampleCount and 1)` in byte 3 so exact sample count is reconstructed!
   * Bytes 4..N: packed 4-bit ADPCM nibbles (low nibble first, high nibble second).
   */
  fun encode(pcm: ShortArray, sampleCount: Int = pcm.size): ByteArray {
    if (sampleCount <= 0) return ByteArray(0)
    var predictor = pcm[0].toInt()
    var stepIndex = estimateInitialStepIndex(pcm, sampleCount)
    val remainingSamples = sampleCount - 1
    val packedBytes = (remainingSamples + 1) / 2
    val out = ByteArray(4 + packedBytes)

    out[0] = (predictor and 0xFF).toByte()
    out[1] = ((predictor shr 8) and 0xFF).toByte()
    out[2] = stepIndex.toByte()
    out[3] = (remainingSamples and 0x01).toByte()

    var outIdx = 4
    var lowNibble = 0
    var hasLow = false

    for (i in 1 until sampleCount) {
      val sample = pcm[i].toInt()
      val step = STEP_TABLE[stepIndex]
      var diff = sample - predictor
      var nibble = 0
      if (diff < 0) {
        nibble = 8
        diff = -diff
      }

      var vpdiff = step shr 3
      if (diff >= step) {
        nibble = nibble or 4
        diff -= step
        vpdiff += step
      }
      val stepHalf = step shr 1
      if (diff >= stepHalf) {
        nibble = nibble or 2
        diff -= stepHalf
        vpdiff += stepHalf
      }
      val stepQuarter = step shr 2
      if (diff >= stepQuarter) {
        nibble = nibble or 1
        vpdiff += stepQuarter
      }

      predictor =
        if ((nibble and 8) != 0) {
          predictor - vpdiff
        } else {
          predictor + vpdiff
        }
      predictor = max(-32768, min(32767, predictor))

      stepIndex = max(0, min(88, stepIndex + INDEX_TABLE[nibble]))

      if (!hasLow) {
        lowNibble = nibble and 0x0F
        hasLow = true
      } else {
        out[outIdx++] = (lowNibble or ((nibble and 0x0F) shl 4)).toByte()
        hasLow = false
      }
    }

    if (hasLow) {
      out[outIdx] = lowNibble.toByte()
    }

    return out
  }

  fun decode(encoded: ByteArray): ShortArray {
    if (encoded.size < 4) return ShortArray(0)
    var predictor =
      ((encoded[0].toInt() and 0xFF) or (encoded[1].toInt() shl 8)).toShort().toInt()
    var stepIndex = max(0, min(88, encoded[2].toInt() and 0xFF))
    val hasTrailingOdd = (encoded[3].toInt() and 0x01) == 1
    val payloadBytes = encoded.size - 4
    val decodedRemaining =
      if (payloadBytes == 0) 0
      else if (hasTrailingOdd) payloadBytes * 2 - 1
      else payloadBytes * 2

    val pcm = ShortArray(1 + decodedRemaining)
    pcm[0] = predictor.toShort()
    var outIdx = 1

    for (byteIdx in 4 until encoded.size) {
      val b = encoded[byteIdx].toInt() and 0xFF
      val lowNibble = b and 0x0F
      predictorAndStepDecode(lowNibble, predictor, stepIndex).also { (nextPred, nextIdx) ->
        predictor = nextPred
        stepIndex = nextIdx
      }
      if (outIdx < pcm.size) {
        pcm[outIdx++] = predictor.toShort()
      }

      if (outIdx < pcm.size) {
        val highNibble = (b ushr 4) and 0x0F
        predictorAndStepDecode(highNibble, predictor, stepIndex).also { (nextPred, nextIdx) ->
          predictor = nextPred
          stepIndex = nextIdx
        }
        pcm[outIdx++] = predictor.toShort()
      }
    }

    return pcm
  }

  private fun predictorAndStepDecode(nibble: Int, predictor: Int, stepIndex: Int): Pair<Int, Int> {
    val step = STEP_TABLE[stepIndex]
    var vpdiff = step shr 3
    if ((nibble and 4) != 0) vpdiff += step
    if ((nibble and 2) != 0) vpdiff += step shr 1
    if ((nibble and 1) != 0) vpdiff += step shr 2

    var nextPred =
      if ((nibble and 8) != 0) {
        predictor - vpdiff
      } else {
        predictor + vpdiff
      }
    nextPred = max(-32768, min(32767, nextPred))
    val nextIdx = max(0, min(88, stepIndex + INDEX_TABLE[nibble and 0x0F]))
    return nextPred to nextIdx
  }

  private fun estimateInitialStepIndex(pcm: ShortArray, count: Int): Int {
    if (count < 2) return 0
    val firstDiff = kotlin.math.abs(pcm[1].toInt() - pcm[0].toInt())
    for (i in STEP_TABLE.indices) {
      if (STEP_TABLE[i] >= firstDiff) return i
    }
    return 88
  }

  fun shortsToBytes(pcm: ShortArray, count: Int = pcm.size): ByteArray {
    val out = ByteArray(count * 2)
    for (i in 0 until count) {
      val s = pcm[i].toInt()
      out[i * 2] = (s and 0xFF).toByte()
      out[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
    }
    return out
  }

  fun bytesToShorts(bytes: ByteArray): ShortArray {
    val count = bytes.size / 2
    val out = ShortArray(count)
    for (i in 0 until count) {
      val low = bytes[i * 2].toInt() and 0xFF
      val high = bytes[i * 2 + 1].toInt() shl 8
      out[i] = (low or high).toShort()
    }
    return out
  }
}
