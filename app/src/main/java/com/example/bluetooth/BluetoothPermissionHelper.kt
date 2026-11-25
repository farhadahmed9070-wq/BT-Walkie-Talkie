package com.example.bluetooth

import android.Manifest
import android.bluetooth.BluetoothClass
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.model.DeviceCategory
import com.example.model.PermissionStatus

object BluetoothPermissionHelper {

  fun requiredBluetoothPermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      arrayOf(
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_ADVERTISE,
      )
    } else {
      arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
  }

  fun allRequiredPermissions(): Array<String> {
    return requiredBluetoothPermissions() + Manifest.permission.RECORD_AUDIO
  }

  fun hasPermission(context: Context, permission: String): Boolean {
    return ContextCompat.checkSelfPermission(context, permission) ==
      PackageManager.PERMISSION_GRANTED
  }

  fun hasBluetoothConnect(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      hasPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
      true
    }
  }

  fun hasBluetoothScan(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      hasPermission(context, Manifest.permission.BLUETOOTH_SCAN)
    } else {
      hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
    }
  }

  fun hasBluetoothAdvertise(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      hasPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE)
    } else {
      true
    }
  }

  fun hasRecordAudio(context: Context): Boolean {
    return hasPermission(context, Manifest.permission.RECORD_AUDIO)
  }

  fun getPermissionStatus(context: Context): PermissionStatus {
    val requiresLocation = Build.VERSION.SDK_INT < Build.VERSION_CODES.S
    return PermissionStatus(
      bluetoothConnectGranted = hasBluetoothConnect(context),
      bluetoothScanGranted = hasBluetoothScan(context),
      bluetoothAdvertiseGranted = hasBluetoothAdvertise(context),
      recordAudioGranted = hasRecordAudio(context),
      locationGranted =
        if (requiresLocation) hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        else true,
      requiresLocationForScan = requiresLocation,
    )
  }

  fun categorizeDevice(majorDeviceClass: Int?): DeviceCategory {
    return when (majorDeviceClass) {
      BluetoothClass.Device.Major.PHONE -> DeviceCategory.PHONE
      BluetoothClass.Device.Major.COMPUTER -> DeviceCategory.COMPUTER
      BluetoothClass.Device.Major.AUDIO_VIDEO -> DeviceCategory.AUDIO
      BluetoothClass.Device.Major.WEARABLE -> DeviceCategory.WEARABLE
      else -> DeviceCategory.OTHER
    }
  }
}
