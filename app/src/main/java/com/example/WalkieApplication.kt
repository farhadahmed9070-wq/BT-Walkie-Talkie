package com.example

import android.app.Application
import com.example.bluetooth.BluetoothWalkieManager
import com.example.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WalkieApplication : Application() {
  private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  lateinit var settingsRepository: SettingsRepository
    private set

  lateinit var bluetoothWalkieManager: BluetoothWalkieManager
    private set

  override fun onCreate() {
    super.onCreate()
    settingsRepository = SettingsRepository(applicationContext)
    bluetoothWalkieManager =
      BluetoothWalkieManager(
        context = applicationContext,
        onDeviceConnectedPersist = { address, name ->
          appScope.launch {
            settingsRepository.saveLastConnectedDevice(address, name)
          }
        },
      )
  }

  override fun onTerminate() {
    bluetoothWalkieManager.destroy()
    super.onTerminate()
  }
}
