package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AudioQualityMode
import com.example.model.WalkieSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.walkieDataStore: DataStore<Preferences> by
  preferencesDataStore(name = "bt_walkie_settings")

data class LastDeviceRecord(
  val address: String,
  val name: String,
)

class SettingsRepository(private val context: Context) {

  private object Keys {
    val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
    val ROGER_BEEP_ENABLED = booleanPreferencesKey("roger_beep_enabled")
    val SPEAKERPHONE_ENABLED = booleanPreferencesKey("speakerphone_enabled")
    val AUDIO_QUALITY_CODE = intPreferencesKey("audio_quality_code")
    val MIC_GAIN = floatPreferencesKey("mic_gain")
    val NOISE_GATE = floatPreferencesKey("noise_gate")
    val AUTO_LISTEN = booleanPreferencesKey("auto_listen")
    val LAST_DEVICE_ADDRESS = stringPreferencesKey("last_device_address")
    val LAST_DEVICE_NAME = stringPreferencesKey("last_device_name")
  }

  val settingsFlow: Flow<WalkieSettings> =
    context.walkieDataStore.data.map { prefs ->
      WalkieSettings(
        vibrationEnabled = prefs[Keys.VIBRATION_ENABLED] ?: true,
        rogerBeepEnabled = prefs[Keys.ROGER_BEEP_ENABLED] ?: true,
        speakerphoneEnabled = prefs[Keys.SPEAKERPHONE_ENABLED] ?: true,
        audioQuality =
          AudioQualityMode.fromCode((prefs[Keys.AUDIO_QUALITY_CODE] ?: 1).toByte()),
        micGain = prefs[Keys.MIC_GAIN] ?: 1.2f,
        noiseGateThreshold = prefs[Keys.NOISE_GATE] ?: 0.02f,
        autoListenEnabled = prefs[Keys.AUTO_LISTEN] ?: true,
      )
    }

  val lastDeviceFlow: Flow<LastDeviceRecord?> =
    context.walkieDataStore.data.map { prefs ->
      val addr = prefs[Keys.LAST_DEVICE_ADDRESS]
      val name = prefs[Keys.LAST_DEVICE_NAME]
      if (!addr.isNullOrBlank()) {
        LastDeviceRecord(address = addr, name = name ?: "Paired Device")
      } else {
        null
      }
    }

  suspend fun updateSettings(transform: (WalkieSettings) -> WalkieSettings) {
    context.walkieDataStore.edit { prefs ->
      val current =
        WalkieSettings(
          vibrationEnabled = prefs[Keys.VIBRATION_ENABLED] ?: true,
          rogerBeepEnabled = prefs[Keys.ROGER_BEEP_ENABLED] ?: true,
          speakerphoneEnabled = prefs[Keys.SPEAKERPHONE_ENABLED] ?: true,
          audioQuality =
            AudioQualityMode.fromCode((prefs[Keys.AUDIO_QUALITY_CODE] ?: 1).toByte()),
          micGain = prefs[Keys.MIC_GAIN] ?: 1.2f,
          noiseGateThreshold = prefs[Keys.NOISE_GATE] ?: 0.02f,
          autoListenEnabled = prefs[Keys.AUTO_LISTEN] ?: true,
        )
      val updated = transform(current)
      prefs[Keys.VIBRATION_ENABLED] = updated.vibrationEnabled
      prefs[Keys.ROGER_BEEP_ENABLED] = updated.rogerBeepEnabled
      prefs[Keys.SPEAKERPHONE_ENABLED] = updated.speakerphoneEnabled
      prefs[Keys.AUDIO_QUALITY_CODE] = updated.audioQuality.code.toInt()
      prefs[Keys.MIC_GAIN] = updated.micGain
      prefs[Keys.NOISE_GATE] = updated.noiseGateThreshold
      prefs[Keys.AUTO_LISTEN] = updated.autoListenEnabled
    }
  }

  suspend fun saveLastConnectedDevice(address: String, name: String) {
    context.walkieDataStore.edit { prefs ->
      prefs[Keys.LAST_DEVICE_ADDRESS] = address
      prefs[Keys.LAST_DEVICE_NAME] = name
    }
  }
}
