package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R
import com.example.model.AudioQualityMode
import com.example.model.ConnectionState
import com.example.ui.components.StatusPill
import com.example.ui.components.TacticalGlassCard
import com.example.ui.theme.BorderGlass
import com.example.ui.theme.DeepSpace
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.GlassPanelElevated
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TransmitCrimson
import com.example.ui.theme.WarningAmber
import com.example.viewmodel.WalkieUiState
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
  uiState: WalkieUiState,
  onVibrationChanged: (Boolean) -> Unit,
  onRogerBeepChanged: (Boolean) -> Unit,
  onSpeakerphoneChanged: (Boolean) -> Unit,
  onAudioQualityChanged: (AudioQualityMode) -> Unit,
  onMicGainChanged: (Float) -> Unit,
  onNoiseGateChanged: (Float) -> Unit,
  onAutoListenChanged: (Boolean) -> Unit,
  onDisconnect: () -> Unit,
  onReconnect: () -> Unit,
  onRequestAllPermissions: () -> Unit,
  onOpenAppSettings: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter,
  ) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item {
        Text(
          text = "Transceiver Settings",
          style = MaterialTheme.typography.headlineSmall,
          color = TextPrimary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "Configure audio codec, haptics, RFCOMM link behavior, and permissions.",
          style = MaterialTheme.typography.bodyMedium,
          color = TextSecondary,
        )
      }

      // Haptics & Alerts
      item {
        TacticalGlassCard(borderColor = NeonCyan) {
          Text(
            text = "HAPTICS & AUDIO CUES",
            style = MaterialTheme.typography.labelMedium,
            color = NeonCyan,
          )
          Spacer(modifier = Modifier.height(10.dp))
          SettingsToggleRow(
            title = "Vibration Feedback",
            subtitle = "Haptic pulse on Push-to-Talk press, release, and incoming voice.",
            checked = uiState.settings.vibrationEnabled,
            onCheckedChange = onVibrationChanged,
            testTag = "settings_vibration_switch",
          )
          Spacer(modifier = Modifier.height(10.dp))
          SettingsToggleRow(
            title = "Roger Beep",
            subtitle = "Play subtle two-tone squelch tail when peer finishes talking.",
            checked = uiState.settings.rogerBeepEnabled,
            onCheckedChange = onRogerBeepChanged,
            testTag = "settings_roger_beep_switch",
          )
          Spacer(modifier = Modifier.height(10.dp))
          SettingsToggleRow(
            title = "Speakerphone Output",
            subtitle = "Route received voice to loudspeaker instead of phone earpiece.",
            checked = uiState.settings.speakerphoneEnabled,
            onCheckedChange = onSpeakerphoneChanged,
            testTag = "settings_speakerphone_switch",
          )
          Spacer(modifier = Modifier.height(10.dp))
          SettingsToggleRow(
            title = "Auto-Listen for Incoming Link",
            subtitle = "Keep secure RFCOMM server ready for paired peers.",
            checked = uiState.settings.autoListenEnabled,
            onCheckedChange = onAutoListenChanged,
            testTag = "settings_autolisten_switch",
          )
        }
      }

      // Audio Quality & DSP
      item {
        TacticalGlassCard(borderColor = ElectricBlue) {
          Text(
            text = "AUDIO ENCODING & DSP",
            style = MaterialTheme.typography.labelMedium,
            color = ElectricBlue,
          )
          Spacer(modifier = Modifier.height(10.dp))

          AudioQualityMode.entries.forEach { mode ->
            val selected = uiState.settings.audioQuality == mode
            Surface(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .padding(vertical = 4.dp)
                  .clickable { onAudioQualityChanged(mode) },
              shape = RoundedCornerShape(14.dp),
              color = if (selected) GlassPanelElevated else DeepSpace.copy(alpha = 0.45f),
              border =
                BorderStroke(
                  1.dp,
                  if (selected) NeonCyan else BorderGlass.copy(alpha = 0.4f),
                ),
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Icon(
                  imageVector =
                    if (selected) Icons.Rounded.RadioButtonChecked
                    else Icons.Rounded.RadioButtonUnchecked,
                  contentDescription = null,
                  tint = if (selected) NeonCyan else TextMuted,
                )
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = mode.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                  )
                  Text(
                    text = mode.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                  )
                }
              }
            }
          }

          Spacer(modifier = Modifier.height(14.dp))

          val gainPercent = (uiState.settings.micGain * 100).roundToInt()
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = "Microphone Gain",
              style = MaterialTheme.typography.titleMedium,
              color = TextPrimary,
            )
            Text(
              text = "$gainPercent%",
              style = MaterialTheme.typography.labelMedium,
              color = NeonCyan,
            )
          }
          Slider(
            value = uiState.settings.micGain,
            onValueChange = onMicGainChanged,
            valueRange = 0.8f..2.0f,
            colors =
              SliderDefaults.colors(
                thumbColor = NeonCyan,
                activeTrackColor = NeonCyan,
                inactiveTrackColor = GlassPanelElevated,
              ),
          )

          Spacer(modifier = Modifier.height(6.dp))

          val gateLabel =
            if (uiState.settings.noiseGateThreshold <= 0.005f) "OFF"
            else "${(uiState.settings.noiseGateThreshold * 1000).roundToInt()} mV"
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = "Noise Gate (Squelch)",
              style = MaterialTheme.typography.titleMedium,
              color = TextPrimary,
            )
            Text(
              text = gateLabel,
              style = MaterialTheme.typography.labelMedium,
              color = NeonCyan,
            )
          }
          Slider(
            value = uiState.settings.noiseGateThreshold,
            onValueChange = onNoiseGateChanged,
            valueRange = 0.0f..0.08f,
            colors =
              SliderDefaults.colors(
                thumbColor = ElectricBlue,
                activeTrackColor = ElectricBlue,
                inactiveTrackColor = GlassPanelElevated,
              ),
          )
        }
      }

      // Connection Actions
      item {
        TacticalGlassCard(
          borderColor =
            if (uiState.connectionState == ConnectionState.CONNECTED) SignalEmerald
            else BorderGlass,
        ) {
          Text(
            text = "LINK MANAGEMENT",
            style = MaterialTheme.typography.labelMedium,
            color = NeonCyan,
          )
          Spacer(modifier = Modifier.height(8.dp))
          val linkSummary =
            uiState.connectedDevice?.let { "Connected to ${it.name} (${it.address})" }
              ?: uiState.lastDevice?.let { "Last peer: ${it.name} (${it.address})" }
              ?: "No device paired or connected yet."
          Text(
            text = linkSummary,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
          )
          Spacer(modifier = Modifier.height(12.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            OutlinedButton(
              onClick = onDisconnect,
              enabled = uiState.connectionState == ConnectionState.CONNECTED,
              modifier = Modifier.weight(1f).testTag("settings_disconnect_button"),
              shape = RoundedCornerShape(14.dp),
              colors = ButtonDefaults.outlinedButtonColors(contentColor = TransmitCrimson),
            ) {
              Icon(
                imageVector = Icons.Rounded.LinkOff,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(text = "Disconnect", style = MaterialTheme.typography.labelMedium)
            }

            Button(
              onClick = onReconnect,
              enabled = uiState.lastDevice != null,
              modifier = Modifier.weight(1f).testTag("settings_reconnect_button"),
              shape = RoundedCornerShape(14.dp),
              colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue, contentColor = TextPrimary),
            ) {
              Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(text = "Reconnect", style = MaterialTheme.typography.labelMedium)
            }
          }
        }
      }

      // Permissions Status
      item {
        val perms = uiState.permissions
        TacticalGlassCard(borderColor = WarningAmber) {
          Text(
            text = "PERMISSIONS STATUS",
            style = MaterialTheme.typography.labelMedium,
            color = WarningAmber,
          )
          Spacer(modifier = Modifier.height(10.dp))

          PermissionRow("Bluetooth Connect", perms.bluetoothConnectGranted)
          PermissionRow("Bluetooth Scan", perms.bluetoothScanGranted)
          PermissionRow("Bluetooth Advertise", perms.bluetoothAdvertiseGranted)
          PermissionRow("Microphone (Push-to-Talk)", perms.recordAudioGranted)
          if (perms.requiresLocationForScan) {
            PermissionRow("Location (Android ≤11 BT Scan)", perms.locationGranted)
          }

          Spacer(modifier = Modifier.height(12.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Button(
              onClick = onRequestAllPermissions,
              modifier = Modifier.weight(1f).testTag("settings_request_permissions_button"),
              shape = RoundedCornerShape(14.dp),
              colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DeepSpace),
            ) {
              Text(text = "Request Missing", style = MaterialTheme.typography.labelMedium)
            }
            FilledTonalButton(
              onClick = onOpenAppSettings,
              modifier = Modifier.weight(1f),
              shape = RoundedCornerShape(14.dp),
              colors =
                ButtonDefaults.filledTonalButtonColors(
                  containerColor = GlassPanelElevated,
                  contentColor = TextPrimary,
                ),
            ) {
              Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(text = "App Settings", style = MaterialTheme.typography.labelMedium)
            }
          }
        }
      }

      // About App & Security
      item {
        TacticalGlassCard {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.Info,
              contentDescription = null,
              tint = NeonCyan,
              modifier = Modifier.size(20.dp),
            )
            Text(
              text = "${stringResource(R.string.app_name)} v${BuildConfig.VERSION_NAME}",
              style = MaterialTheme.typography.titleMedium,
              color = TextPrimary,
            )
          }
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = stringResource(R.string.about_description),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
          )
          Spacer(modifier = Modifier.height(10.dp))
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.Security,
              contentDescription = null,
              tint = SignalEmerald,
              modifier = Modifier.size(16.dp),
            )
            Text(
              text = "Secure Paired RFCOMM • Zero Cloud • Zero Audio Storage",
              style = MaterialTheme.typography.labelSmall,
              color = SignalEmerald,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun SettingsToggleRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  testTag: String,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = TextPrimary,
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = TextSecondary,
      )
    }
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      modifier = Modifier.testTag(testTag),
      colors =
        SwitchDefaults.colors(
          checkedThumbColor = DeepSpace,
          checkedTrackColor = NeonCyan,
          uncheckedThumbColor = TextSecondary,
          uncheckedTrackColor = GlassPanelElevated,
        ),
    )
  }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(
        imageVector = if (granted) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
        contentDescription = null,
        tint = if (granted) SignalEmerald else WarningAmber,
        modifier = Modifier.size(18.dp),
      )
      Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = TextPrimary,
      )
    }
    StatusPill(
      text = if (granted) "GRANTED" else "DENIED",
      dotColor = if (granted) SignalEmerald else WarningAmber,
    )
  }
}
