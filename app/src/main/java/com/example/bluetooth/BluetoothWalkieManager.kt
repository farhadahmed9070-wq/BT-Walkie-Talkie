package com.example.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.example.audio.WalkieAudioEngine
import com.example.model.AudioQualityMode
import com.example.model.BluetoothAvailability
import com.example.model.ChannelState
import com.example.model.ConnectedDeviceInfo
import com.example.model.ConnectionState
import com.example.model.DiscoveredBtDevice
import com.example.model.WalkieSettings
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Manages real Bluetooth Classic RFCOMM discovery, secure pairing/connection,
 * socket lifecycle, heartbeat monitoring, and half-duplex voice packet streaming.
 */
class BluetoothWalkieManager(
  private val context: Context,
  private val onDeviceConnectedPersist: (address: String, name: String) -> Unit,
) {
  companion object {
    const val SERVICE_NAME = "BTWalkieTalkie"
    val SERVICE_UUID: UUID = UUID.fromString("8a9e4f10-3c2b-4d7e-9f1a-6b5c4d3e2f10")
    private const val HEARTBEAT_INTERVAL_MS = 3000L
    private const val RECONNECT_DELAY_MS = 1200L
  }

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val bluetoothManager =
    context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
  private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

  val audioEngine =
    WalkieAudioEngine(
      context = context,
      onAudioInterruption = {
        if (_channelState.value == ChannelState.TRANSMITTING) {
          stopTransmitting()
          emitStatusEvent("Transmission paused due to system audio interruption.")
        }
      },
    )

  private val _availability = MutableStateFlow(resolveAvailability())
  val availability: StateFlow<BluetoothAvailability> = _availability.asStateFlow()

  private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
  val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

  private val _channelState = MutableStateFlow(ChannelState.STANDBY)
  val channelState: StateFlow<ChannelState> = _channelState.asStateFlow()

  private val _isScanning = MutableStateFlow(false)
  val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

  private val _pairedDevices = MutableStateFlow<List<DiscoveredBtDevice>>(emptyList())
  val pairedDevices: StateFlow<List<DiscoveredBtDevice>> = _pairedDevices.asStateFlow()

  private val _nearbyDevices = MutableStateFlow<List<DiscoveredBtDevice>>(emptyList())
  val nearbyDevices: StateFlow<List<DiscoveredBtDevice>> = _nearbyDevices.asStateFlow()

  private val _connectedDevice = MutableStateFlow<ConnectedDeviceInfo?>(null)
  val connectedDevice: StateFlow<ConnectedDeviceInfo?> = _connectedDevice.asStateFlow()

  private val _lastError = MutableStateFlow<String?>(null)
  val lastError: StateFlow<String?> = _lastError.asStateFlow()

  private val _activeRemoteCodec = MutableStateFlow<AudioQualityMode?>(null)
  val activeRemoteCodec: StateFlow<AudioQualityMode?> = _activeRemoteCodec.asStateFlow()

  private val _statusEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
  val statusEvents: SharedFlow<String> = _statusEvents.asSharedFlow()

  private val _incomingTransmissionStartEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
  val incomingTransmissionStartEvents: SharedFlow<Unit> =
    _incomingTransmissionStartEvents.asSharedFlow()

  private var currentSettings = WalkieSettings()
  private var lastTargetAddress: String? = null
  private var lastTargetName: String? = null
  private var lastKnownRssiMap = mutableMapOf<String, Int>()

  private var acceptJob: Job? = null
  private var connectJob: Job? = null
  private var sessionReadJob: Job? = null
  private var heartbeatJob: Job? = null

  private var serverSocket: BluetoothServerSocket? = null
  private var activeSocket: BluetoothSocket? = null
  private var dataOut: DataOutputStream? = null
  private val writeLock = Any()

  private var receiverRegistered = false

  private val btBroadcastReceiver =
    object : BroadcastReceiver() {
      @SuppressLint("MissingPermission")
      override fun onReceive(ctx: Context?, intent: Intent?) {
        when (intent?.action) {
          BluetoothAdapter.ACTION_STATE_CHANGED -> {
            val state =
              intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
            _availability.value = resolveAvailability()
            when (state) {
              BluetoothAdapter.STATE_ON -> {
                refreshPairedDevices()
                if (currentSettings.autoListenEnabled) {
                  startListeningServer()
                }
              }
              BluetoothAdapter.STATE_OFF,
              BluetoothAdapter.STATE_TURNING_OFF -> {
                _isScanning.value = false
                tearDownConnection(targetState = ConnectionState.DISCONNECTED, notifyPeer = false)
                stopListeningServer()
              }
            }
          }
          BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
            _isScanning.value = true
          }
          BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
            _isScanning.value = false
          }
          BluetoothDevice.ACTION_FOUND -> {
            if (!BluetoothPermissionHelper.hasBluetoothConnect(context)) return
            val device: BluetoothDevice? =
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
              } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
              }
            device ?: return
            val rssiRaw =
              intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
            val rssi = if (rssiRaw != Short.MIN_VALUE.toInt()) rssiRaw else null
            if (rssi != null) {
              lastKnownRssiMap[device.address] = rssi
              if (_connectedDevice.value?.address == device.address) {
                _connectedDevice.value = _connectedDevice.value?.copy(rssiDbm = rssi)
              }
            }
            val model = device.toModel(rssi)
            if (model.isBonded) {
              refreshPairedDevices()
            } else {
              upsertNearbyDevice(model)
            }
          }
          BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
            if (!BluetoothPermissionHelper.hasBluetoothConnect(context)) return
            val device: BluetoothDevice? =
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
              } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
              }
            val bondState =
              intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
            refreshPairedDevices()
            if (device != null) {
              if (bondState == BluetoothDevice.BOND_BONDED) {
                _nearbyDevices.value =
                  _nearbyDevices.value.filterNot { it.address == device.address }
                emitStatusEvent("Paired with ${safeDeviceName(device)}")
              } else if (bondState == BluetoothDevice.BOND_BONDING) {
                _nearbyDevices.value =
                  _nearbyDevices.value.map {
                    if (it.address == device.address) it.copy(isBonding = true) else it
                  }
              } else {
                _nearbyDevices.value =
                  _nearbyDevices.value.map {
                    if (it.address == device.address) it.copy(isBonding = false, isBonded = false)
                    else it
                  }
              }
            }
          }
          BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
            if (!BluetoothPermissionHelper.hasBluetoothConnect(context)) return
            val device: BluetoothDevice? =
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
              } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
              }
            if (device != null && device.address == _connectedDevice.value?.address) {
              handleConnectionLost("Bluetooth link dropped by ${safeDeviceName(device)}.")
            }
          }
        }
      }
    }

  init {
    registerReceivers()
    refreshState()
  }

  fun updateSettings(settings: WalkieSettings) {
    val prevSpeaker = currentSettings.speakerphoneEnabled
    val prevAutoListen = currentSettings.autoListenEnabled
    currentSettings = settings
    if (prevSpeaker != settings.speakerphoneEnabled) {
      audioEngine.applyAudioRouting(settings.speakerphoneEnabled)
    }
    if (prevAutoListen != settings.autoListenEnabled) {
      if (settings.autoListenEnabled && _connectionState.value == ConnectionState.DISCONNECTED) {
        startListeningServer()
      } else if (!settings.autoListenEnabled && _connectionState.value == ConnectionState.LISTENING) {
        stopListeningServer()
        _connectionState.value = ConnectionState.DISCONNECTED
      }
    }
  }

  fun refreshState() {
    _availability.value = resolveAvailability()
    refreshPairedDevices()
    if (
      _availability.value == BluetoothAvailability.AVAILABLE &&
        BluetoothPermissionHelper.hasBluetoothConnect(context) &&
        currentSettings.autoListenEnabled &&
        _connectionState.value == ConnectionState.DISCONNECTED
    ) {
      startListeningServer()
    }
  }

  @SuppressLint("MissingPermission")
  fun refreshPairedDevices() {
    if (!BluetoothPermissionHelper.hasBluetoothConnect(context)) {
      _pairedDevices.value = emptyList()
      return
    }
    val adapter = bluetoothAdapter ?: return
    val bonded =
      try {
        adapter.bondedDevices?.map { device ->
          device.toModel(rssi = lastKnownRssiMap[device.address])
        } ?: emptyList()
      } catch (_: SecurityException) {
        emptyList()
      }
    _pairedDevices.value = bonded.sortedBy { it.name.lowercase() }
  }

  @SuppressLint("MissingPermission")
  fun startScan() {
    _lastError.value = null
    if (resolveAvailability() != BluetoothAvailability.AVAILABLE) {
      _lastError.value = "Enable Bluetooth to scan for nearby devices."
      return
    }
    if (!BluetoothPermissionHelper.hasBluetoothScan(context) ||
      !BluetoothPermissionHelper.hasBluetoothConnect(context)
    ) {
      _lastError.value = "Bluetooth scan and connect permissions are required."
      return
    }
    val adapter = bluetoothAdapter ?: return
    try {
      if (adapter.isDiscovering) {
        adapter.cancelDiscovery()
      }
      _nearbyDevices.value = emptyList()
      refreshPairedDevices()
      val started = adapter.startDiscovery()
      _isScanning.value = started
      if (!started) {
        _lastError.value = "Could not start Bluetooth discovery. Ensure Bluetooth is on."
      }
    } catch (e: SecurityException) {
      _isScanning.value = false
      _lastError.value = "Bluetooth scan permission denied."
    }
  }

  @SuppressLint("MissingPermission")
  fun stopScan() {
    if (!BluetoothPermissionHelper.hasBluetoothScan(context)) {
      _isScanning.value = false
      return
    }
    try {
      if (bluetoothAdapter?.isDiscovering == true) {
        bluetoothAdapter.cancelDiscovery()
      }
    } catch (_: SecurityException) {
    } finally {
      _isScanning.value = false
    }
  }

  @SuppressLint("MissingPermission")
  fun pairDevice(address: String) {
    if (!BluetoothPermissionHelper.hasBluetoothConnect(context)) {
      _lastError.value = "Bluetooth connect permission is required to pair."
      return
    }
    val device = getRemoteDevice(address) ?: return
    try {
      stopScan()
      val initiated = device.createBond()
      if (!initiated) {
        _lastError.value = "Unable to start pairing with ${safeDeviceName(device)}."
      } else {
        emitStatusEvent("Pairing requested with ${safeDeviceName(device)}...")
      }
    } catch (e: SecurityException) {
      _lastError.value = "Bluetooth permission denied while pairing."
    }
  }

  @SuppressLint("MissingPermission")
  fun startListeningServer() {
    if (resolveAvailability() != BluetoothAvailability.AVAILABLE) return
    if (!BluetoothPermissionHelper.hasBluetoothConnect(context)) return
    if (_connectionState.value == ConnectionState.CONNECTED ||
      _connectionState.value == ConnectionState.CONNECTING ||
      _connectionState.value == ConnectionState.RECONNECTING
    ) {
      return
    }
    if (acceptJob?.isActive == true) return

    val adapter = bluetoothAdapter ?: return
    acceptJob =
      scope.launch {
        var localServer: BluetoothServerSocket? = null
        try {
          localServer = adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID)
          serverSocket = localServer
          if (_connectionState.value == ConnectionState.DISCONNECTED) {
            _connectionState.value = ConnectionState.LISTENING
          }

          while (isActive) {
            val socket =
              try {
                localServer.accept()
              } catch (_: IOException) {
                break
              }
            if (socket != null) {
              stopScan()
              closeServerSocketQuietly()
              onSocketEstablished(socket, isIncoming = true)
              break
            }
          }
        } catch (e: SecurityException) {
          _lastError.value = "Bluetooth connect permission missing for listener."
          if (_connectionState.value == ConnectionState.LISTENING) {
            _connectionState.value = ConnectionState.DISCONNECTED
          }
        } catch (e: IOException) {
          if (_connectionState.value == ConnectionState.LISTENING) {
            _connectionState.value = ConnectionState.DISCONNECTED
          }
        } finally {
          try {
            localServer?.close()
          } catch (_: IOException) {}
          if (serverSocket === localServer) {
            serverSocket = null
          }
        }
      }
  }

  fun stopListeningServer() {
    acceptJob?.cancel()
    acceptJob = null
    closeServerSocketQuietly()
  }

  @SuppressLint("MissingPermission")
  fun connectToDevice(address: String, fallbackName: String? = null) {
    _lastError.value = null
    if (resolveAvailability() != BluetoothAvailability.AVAILABLE) {
      _lastError.value = "Bluetooth is turned off."
      return
    }
    if (!BluetoothPermissionHelper.hasBluetoothConnect(context)) {
      _lastError.value = "Bluetooth connect permission is required."
      return
    }
    val device = getRemoteDevice(address)
    if (device == null) {
      _lastError.value = "Invalid Bluetooth device address."
      return
    }

    lastTargetAddress = address
    lastTargetName = fallbackName ?: safeDeviceName(device)

    connectJob?.cancel()
    stopListeningServer()
    tearDownConnection(targetState = ConnectionState.CONNECTING, notifyPeer = false)

    connectJob =
      scope.launch {
        stopScan()
        var socket: BluetoothSocket? = null
        try {
          // Secure RFCOMM socket enforces encrypted, authenticated Bluetooth pairing
          socket = device.createRfcommSocketToServiceRecord(SERVICE_UUID)
          socket.connect()
          onSocketEstablished(socket, isIncoming = false)
        } catch (e: SecurityException) {
          closeSocketQuietly(socket)
          _connectionState.value = ConnectionState.DISCONNECTED
          _lastError.value = "Permission denied while connecting to ${lastTargetName}."
          if (currentSettings.autoListenEnabled) startListeningServer()
        } catch (e: IOException) {
          closeSocketQuietly(socket)
          _connectionState.value = ConnectionState.DISCONNECTED
          _lastError.value =
            "Could not connect to ${lastTargetName}. Ensure BT Walkie Talkie is open and paired on the other phone."
          if (currentSettings.autoListenEnabled) startListeningServer()
        }
      }
  }

  fun reconnect(fallbackAddress: String?, fallbackName: String?) {
    val targetAddr = lastTargetAddress ?: _connectedDevice.value?.address ?: fallbackAddress
    val targetName = lastTargetName ?: _connectedDevice.value?.name ?: fallbackName
    if (targetAddr.isNullOrBlank()) {
      _lastError.value = "No previous device available to reconnect."
      return
    }
    _lastError.value = null
    _connectionState.value = ConnectionState.RECONNECTING
    connectJob?.cancel()
    scope.launch {
      delay(300)
      connectToDevice(targetAddr, targetName)
    }
  }

  fun disconnect() {
    connectJob?.cancel()
    connectJob = null
    _lastError.value = null
    tearDownConnection(targetState = ConnectionState.DISCONNECTED, notifyPeer = true)
    emitStatusEvent("Disconnected.")
    if (currentSettings.autoListenEnabled &&
      resolveAvailability() == BluetoothAvailability.AVAILABLE
    ) {
      startListeningServer()
    }
  }

  fun startTransmitting(): Boolean {
    if (_connectionState.value != ConnectionState.CONNECTED) {
      _lastError.value = "Connect to a device before transmitting."
      return false
    }
    if (_channelState.value == ChannelState.RECEIVING) {
      emitStatusEvent("Channel busy — receiving peer voice.")
      return false
    }
    if (_channelState.value == ChannelState.TRANSMITTING) {
      return true
    }
    if (!BluetoothPermissionHelper.hasRecordAudio(context)) {
      _lastError.value = "Microphone permission is required for Push-to-Talk."
      return false
    }

    val quality = currentSettings.audioQuality
    val sentStart = sendPacket(WalkiePacketProtocol.PKT_PTT_START, quality.code)
    if (!sentStart) {
      handleConnectionLost("Connection lost while starting transmission.")
      return false
    }

    _channelState.value = ChannelState.TRANSMITTING
    val started =
      audioEngine.startCapture(
        settings = currentSettings,
        onEncodedFrame = { mode, payload ->
          if (_channelState.value == ChannelState.TRANSMITTING) {
            val ok = sendPacket(WalkiePacketProtocol.PKT_AUDIO_FRAME, mode.code, payload)
            if (!ok) {
              handleConnectionLost("Connection lost during transmission.")
            }
          }
        },
        onCaptureError = { msg ->
          _lastError.value = msg
          stopTransmitting()
        },
      )

    if (!started) {
      _channelState.value = ChannelState.STANDBY
      sendPacket(WalkiePacketProtocol.PKT_PTT_END, quality.code)
      return false
    }
    return true
  }

  fun stopTransmitting() {
    if (_channelState.value != ChannelState.TRANSMITTING) {
      audioEngine.stopCapture()
      return
    }
    audioEngine.stopCapture()
    _channelState.value = ChannelState.STANDBY
    if (_connectionState.value == ConnectionState.CONNECTED) {
      sendPacket(WalkiePacketProtocol.PKT_PTT_END, currentSettings.audioQuality.code)
    }
  }

  fun clearError() {
    _lastError.value = null
  }

  fun destroy() {
    stopScan()
    connectJob?.cancel()
    tearDownConnection(targetState = ConnectionState.DISCONNECTED, notifyPeer = true)
    stopListeningServer()
    audioEngine.releaseAll()
    if (receiverRegistered) {
      try {
        context.unregisterReceiver(btBroadcastReceiver)
      } catch (_: Throwable) {}
      receiverRegistered = false
    }
  }

  @SuppressLint("MissingPermission")
  private fun onSocketEstablished(socket: BluetoothSocket, isIncoming: Boolean) {
    val remote = socket.remoteDevice
    val name = safeDeviceName(remote)
    val address = remote.address
    val rssi = lastKnownRssiMap[address]

    lastTargetAddress = address
    lastTargetName = name
    onDeviceConnectedPersist(address, name)

    activeSocket = socket
    try {
      dataOut = DataOutputStream(BufferedOutputStream(socket.outputStream, 2048))
      val dataIn = DataInputStream(BufferedInputStream(socket.inputStream, 4096))

      _connectedDevice.value =
        ConnectedDeviceInfo(
          name = name,
          address = address,
          rssiDbm = rssi,
          isIncoming = isIncoming,
        )
      _connectionState.value = ConnectionState.CONNECTED
      _channelState.value = ChannelState.STANDBY
      _lastError.value = null
      emitStatusEvent("Connected to $name")

      startHeartbeatLoop()
      startSessionReadLoop(dataIn, name)
    } catch (e: IOException) {
      handleConnectionLost("Failed to open RFCOMM audio streams with $name.")
    }
  }

  private fun startSessionReadLoop(dataIn: DataInputStream, peerName: String) {
    sessionReadJob?.cancel()
    sessionReadJob =
      scope.launch {
        try {
          while (isActive && _connectionState.value == ConnectionState.CONNECTED) {
            val packet = WalkiePacketProtocol.readPacket(dataIn)
            when (packet.type) {
              WalkiePacketProtocol.PKT_PTT_START -> {
                val mode = AudioQualityMode.fromCode(packet.meta)
                _activeRemoteCodec.value = mode
                if (_channelState.value == ChannelState.TRANSMITTING) {
                  audioEngine.stopCapture()
                }
                _channelState.value = ChannelState.RECEIVING
                _incomingTransmissionStartEvents.tryEmit(Unit)
                audioEngine.preparePlayback(mode, currentSettings.speakerphoneEnabled)
              }
              WalkiePacketProtocol.PKT_AUDIO_FRAME -> {
                val mode = AudioQualityMode.fromCode(packet.meta)
                _activeRemoteCodec.value = mode
                if (_channelState.value != ChannelState.TRANSMITTING) {
                  if (_channelState.value != ChannelState.RECEIVING) {
                    _channelState.value = ChannelState.RECEIVING
                  }
                  audioEngine.playEncodedFrame(
                    mode = mode,
                    payload = packet.payload,
                    speakerphoneEnabled = currentSettings.speakerphoneEnabled,
                  )
                }
              }
              WalkiePacketProtocol.PKT_PTT_END -> {
                audioEngine.stopPlayback()
                _activeRemoteCodec.value = null
                if (_channelState.value == ChannelState.RECEIVING) {
                  _channelState.value = ChannelState.STANDBY
                  if (currentSettings.rogerBeepEnabled) {
                    audioEngine.playRogerBeep(currentSettings.speakerphoneEnabled)
                  }
                }
              }
              WalkiePacketProtocol.PKT_HEARTBEAT -> {
                // Keepalive acknowledged
              }
              WalkiePacketProtocol.PKT_DISCONNECT -> {
                handleConnectionLost("$peerName disconnected.")
                break
              }
            }
          }
        } catch (_: IOException) {
          if (_connectionState.value == ConnectionState.CONNECTED) {
            handleConnectionLost("Connection lost with $peerName.")
          }
        }
      }
  }

  private fun startHeartbeatLoop() {
    heartbeatJob?.cancel()
    heartbeatJob =
      scope.launch {
        while (isActive && _connectionState.value == ConnectionState.CONNECTED) {
          delay(HEARTBEAT_INTERVAL_MS)
          if (_connectionState.value == ConnectionState.CONNECTED &&
            _channelState.value == ChannelState.STANDBY
          ) {
            val ok = sendPacket(WalkiePacketProtocol.PKT_HEARTBEAT)
            if (!ok) {
              handleConnectionLost("Peer stopped responding.")
              break
            }
          }
        }
      }
  }

  private fun sendPacket(
    type: Byte,
    meta: Byte = 0,
    payload: ByteArray = ByteArray(0),
  ): Boolean {
    val out = dataOut ?: return false
    return synchronized(writeLock) {
      try {
        WalkiePacketProtocol.writePacket(out, type, meta, payload)
        true
      } catch (_: IOException) {
        false
      }
    }
  }

  private fun handleConnectionLost(reason: String) {
    tearDownConnection(targetState = ConnectionState.DISCONNECTED, notifyPeer = false)
    _lastError.value = reason
    if (currentSettings.autoListenEnabled &&
      resolveAvailability() == BluetoothAvailability.AVAILABLE
    ) {
      scope.launch {
        delay(RECONNECT_DELAY_MS)
        if (_connectionState.value == ConnectionState.DISCONNECTED) {
          startListeningServer()
        }
      }
    }
  }

  private fun tearDownConnection(targetState: ConnectionState, notifyPeer: Boolean) {
    if (notifyPeer && _connectionState.value == ConnectionState.CONNECTED) {
      sendPacket(WalkiePacketProtocol.PKT_DISCONNECT)
    }

    audioEngine.stopCapture()
    audioEngine.stopPlayback()
    _channelState.value = ChannelState.STANDBY
    _activeRemoteCodec.value = null

    heartbeatJob?.cancel()
    heartbeatJob = null

    sessionReadJob?.cancel()
    sessionReadJob = null

    synchronized(writeLock) {
      try {
        dataOut?.close()
      } catch (_: IOException) {}
      dataOut = null
    }

    closeSocketQuietly(activeSocket)
    activeSocket = null

    if (targetState != ConnectionState.CONNECTING && targetState != ConnectionState.RECONNECTING) {
      _connectedDevice.value = null
    }
    _connectionState.value = targetState
  }

  private fun resolveAvailability(): BluetoothAvailability {
    val adapter = bluetoothAdapter ?: return BluetoothAvailability.UNSUPPORTED
    return if (adapter.isEnabled) BluetoothAvailability.AVAILABLE
    else BluetoothAvailability.DISABLED
  }

  @SuppressLint("MissingPermission")
  private fun getRemoteDevice(address: String): BluetoothDevice? {
    return try {
      bluetoothAdapter?.getRemoteDevice(address)
    } catch (_: IllegalArgumentException) {
      null
    }
  }

  @SuppressLint("MissingPermission")
  private fun safeDeviceName(device: BluetoothDevice): String {
    return try {
      device.name?.takeIf { it.isNotBlank() } ?: "BT Device (${device.address.takeLast(5)})"
    } catch (_: SecurityException) {
      "Bluetooth Device"
    }
  }

  @SuppressLint("MissingPermission")
  private fun BluetoothDevice.toModel(rssi: Int?): DiscoveredBtDevice {
    val bonded =
      try {
        bondState == BluetoothDevice.BOND_BONDED
      } catch (_: SecurityException) {
        false
      }
    val bonding =
      try {
        bondState == BluetoothDevice.BOND_BONDING
      } catch (_: SecurityException) {
        false
      }
    val majorClass =
      try {
        bluetoothClass?.majorDeviceClass
      } catch (_: SecurityException) {
        null
      }
    return DiscoveredBtDevice(
      address = address,
      name = safeDeviceName(this),
      isBonded = bonded,
      isBonding = bonding,
      rssiDbm = rssi,
      category = BluetoothPermissionHelper.categorizeDevice(majorClass),
    )
  }

  private fun upsertNearbyDevice(device: DiscoveredBtDevice) {
    val current = _nearbyDevices.value.toMutableList()
    val idx = current.indexOfFirst { it.address == device.address }
    if (idx >= 0) {
      val mergedRssi = device.rssiDbm ?: current[idx].rssiDbm
      current[idx] = device.copy(rssiDbm = mergedRssi)
    } else {
      current.add(device)
    }
    _nearbyDevices.value = current
  }

  private fun registerReceivers() {
    if (receiverRegistered) return
    val filter =
      IntentFilter().apply {
        addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
        addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        addAction(BluetoothDevice.ACTION_FOUND)
        addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
      }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      context.registerReceiver(btBroadcastReceiver, filter, Context.RECEIVER_EXPORTED)
    } else {
      context.registerReceiver(btBroadcastReceiver, filter)
    }
    receiverRegistered = true
  }

  private fun closeServerSocketQuietly() {
    try {
      serverSocket?.close()
    } catch (_: IOException) {}
    serverSocket = null
  }

  private fun closeSocketQuietly(socket: BluetoothSocket?) {
    try {
      socket?.close()
    } catch (_: IOException) {}
  }

  private fun emitStatusEvent(message: String) {
    _statusEvents.tryEmit(message)
  }
}
