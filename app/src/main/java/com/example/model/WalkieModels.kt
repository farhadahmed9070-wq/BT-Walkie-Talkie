package com.example.model

enum class BluetoothAvailability {
  AVAILABLE,
  DISABLED,
  UNSUPPORTED,
}

enum class ConnectionState {
  DISCONNECTED,
  LISTENING,
  CONNECTING,
  CONNECTED,
  RECONNECTING,
}

enum class ChannelState {
  STANDBY,
  TRANSMITTING,
  RECEIVING,
}

enum class AudioQualityMode(
  val code: Byte,
  val sampleRate: Int,
  val label: String,
  val subtitle: String,
  val isAdpcm: Boolean,
) {
  ADPCM_16KHZ(
    code = 1,
    sampleRate = 16000,
    label = "HD ADPCM • 16 kHz",
    subtitle = "4:1 compressed • 64 kbps • Low latency",
    isAdpcm = true,
  ),
  ADPCM_8KHZ(
    code = 2,
    sampleRate = 8000,
    label = "Eco ADPCM • 8 kHz",
    subtitle = "4:1 compressed • 32 kbps • Max range",
    isAdpcm = true,
  ),
  PCM_16KHZ(
    code = 3,
    sampleRate = 16000,
    label = "Raw PCM • 16 kHz",
    subtitle = "Uncompressed 16-bit • 256 kbps",
    isAdpcm = false,
  );

  companion object {
    fun fromCode(code: Byte): AudioQualityMode {
      return entries.firstOrNull { it.code == code } ?: ADPCM_16KHZ
    }
  }
}

enum class DeviceCategory {
  PHONE,
  COMPUTER,
  AUDIO,
  WEARABLE,
  OTHER,
}

data class DiscoveredBtDevice(
  val address: String,
  val name: String,
  val isBonded: Boolean,
  val isBonding: Boolean = false,
  val rssiDbm: Int? = null,
  val category: DeviceCategory = DeviceCategory.OTHER,
)

data class ConnectedDeviceInfo(
  val name: String,
  val address: String,
  val rssiDbm: Int? = null,
  val isIncoming: Boolean = false,
)

data class PermissionStatus(
  val bluetoothConnectGranted: Boolean = false,
  val bluetoothScanGranted: Boolean = false,
  val bluetoothAdvertiseGranted: Boolean = false,
  val recordAudioGranted: Boolean = false,
  val locationGranted: Boolean = false,
  val requiresLocationForScan: Boolean = false,
) {
  val allBluetoothGranted: Boolean
    get() =
      bluetoothConnectGranted &&
        bluetoothScanGranted &&
        (!requiresLocationForScan || locationGranted)
}

data class WalkieSettings(
  val vibrationEnabled: Boolean = true,
  val rogerBeepEnabled: Boolean = true,
  val speakerphoneEnabled: Boolean = true,
  val audioQuality: AudioQualityMode = AudioQualityMode.ADPCM_16KHZ,
  val micGain: Float = 1.2f,
  val noiseGateThreshold: Float = 0.02f,
  val autoListenEnabled: Boolean = true,
)
