package com.example.bluetooth

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/**
 * Framed RFCOMM binary protocol for half-duplex Bluetooth walkie-talkie control & voice packets.
 *
 * Frame layout:
 * - Byte 0: Magic byte (0x57 = 'W')
 * - Byte 1: Packet type
 * - Byte 2: Metadata (e.g., AudioQualityMode code)
 * - Bytes 3..4: Payload length (unsigned 16-bit big-endian, 0..MAX_PAYLOAD_BYTES)
 * - Bytes 5..(5 + len - 1): Payload bytes
 */
object WalkiePacketProtocol {
  const val MAGIC_BYTE: Byte = 0x57
  const val MAX_PAYLOAD_BYTES = 8192

  const val PKT_PTT_START: Byte = 0x01
  const val PKT_AUDIO_FRAME: Byte = 0x02
  const val PKT_PTT_END: Byte = 0x03
  const val PKT_HEARTBEAT: Byte = 0x04
  const val PKT_DISCONNECT: Byte = 0x05

  data class Packet(
    val type: Byte,
    val meta: Byte,
    val payload: ByteArray,
  ) {
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is Packet) return false
      return type == other.type && meta == other.meta && payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
      var result = type.toInt()
      result = 31 * result + meta.toInt()
      result = 31 * result + payload.contentHashCode()
      return result
    }
  }

  @Throws(IOException::class)
  fun writePacket(
    out: DataOutputStream,
    type: Byte,
    meta: Byte = 0,
    payload: ByteArray = ByteArray(0),
  ) {
    require(payload.size <= MAX_PAYLOAD_BYTES) {
      "Payload exceeds max frame size: ${payload.size}"
    }
    out.writeByte(MAGIC_BYTE.toInt())
    out.writeByte(type.toInt())
    out.writeByte(meta.toInt())
    out.writeShort(payload.size)
    if (payload.isNotEmpty()) {
      out.write(payload)
    }
    out.flush()
  }

  @Throws(IOException::class)
  fun readPacket(input: DataInputStream): Packet {
    // Synchronize on MAGIC_BYTE in case of any transient stream boundary noise
    var magic = input.readByte()
    var skipped = 0
    while (magic != MAGIC_BYTE) {
      skipped++
      if (skipped > MAX_PAYLOAD_BYTES) {
        throw IOException("Lost RFCOMM frame sync")
      }
      magic = input.readByte()
    }

    val type = input.readByte()
    val meta = input.readByte()
    val length = input.readUnsignedShort()
    if (length > MAX_PAYLOAD_BYTES) {
      throw IOException("Corrupt packet length: $length")
    }
    val payload = ByteArray(length)
    if (length > 0) {
      input.readFully(payload)
    }
    return Packet(type = type, meta = meta, payload = payload)
  }
}
