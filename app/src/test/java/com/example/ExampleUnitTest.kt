package com.example

import com.example.audio.ImaAdpcmCodec
import com.example.bluetooth.WalkiePacketProtocol
import com.example.model.AudioQualityMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

  @Test
  fun adpcmRoundTripPreservesSineWaveShapeAndSampleCount() {
    val sampleCount = 320
    val input =
      ShortArray(sampleCount) { i ->
        (sin(2.0 * PI * 440.0 * i / 16000.0) * 14000.0).toInt().toShort()
      }

    val encoded = ImaAdpcmCodec.encode(input, sampleCount)
    val decoded = ImaAdpcmCodec.decode(encoded)

    assertEquals(sampleCount, decoded.size)
    // Verify 4:1 compression ratio (+4 byte header)
    assertTrue(encoded.size <= 4 + sampleCount / 2)

    var totalAbsError = 0L
    for (i in 0 until sampleCount) {
      totalAbsError += abs(input[i].toInt() - decoded[i].toInt())
    }
    val meanAbsError = totalAbsError.toDouble() / sampleCount
    assertTrue("Expected low ADPCM quantization error, got $meanAbsError", meanAbsError < 700.0)
  }

  @Test
  fun adpcmHandlesOddSampleCount() {
    val input = shortArrayOf(0, 1200, -1500, 3000, -2500)
    val encoded = ImaAdpcmCodec.encode(input, input.size)
    val decoded = ImaAdpcmCodec.decode(encoded)
    assertEquals(input.size, decoded.size)
  }

  @Test
  fun packetProtocolRoundTripPreservesTypeMetaAndPayload() {
    val baos = ByteArrayOutputStream()
    val dos = DataOutputStream(baos)
    val audioBytes = byteArrayOf(10, 20, 30, 40, -5, -10)

    WalkiePacketProtocol.writePacket(
      out = dos,
      type = WalkiePacketProtocol.PKT_PTT_START,
      meta = AudioQualityMode.ADPCM_16KHZ.code,
    )
    WalkiePacketProtocol.writePacket(
      out = dos,
      type = WalkiePacketProtocol.PKT_AUDIO_FRAME,
      meta = AudioQualityMode.ADPCM_16KHZ.code,
      payload = audioBytes,
    )
    WalkiePacketProtocol.writePacket(
      out = dos,
      type = WalkiePacketProtocol.PKT_PTT_END,
      meta = AudioQualityMode.ADPCM_16KHZ.code,
    )

    val dis = DataInputStream(ByteArrayInputStream(baos.toByteArray()))
    val p1 = WalkiePacketProtocol.readPacket(dis)
    val p2 = WalkiePacketProtocol.readPacket(dis)
    val p3 = WalkiePacketProtocol.readPacket(dis)

    assertEquals(WalkiePacketProtocol.PKT_PTT_START, p1.type)
    assertEquals(AudioQualityMode.ADPCM_16KHZ.code, p1.meta)
    assertEquals(0, p1.payload.size)

    assertEquals(WalkiePacketProtocol.PKT_AUDIO_FRAME, p2.type)
    assertArrayEquals(audioBytes, p2.payload)

    assertEquals(WalkiePacketProtocol.PKT_PTT_END, p3.type)
  }
}
