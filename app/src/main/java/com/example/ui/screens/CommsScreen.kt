package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.BluetoothAvailability
import com.example.model.ChannelState
import com.example.model.ConnectionState
import com.example.ui.components.LiveAudioWaveform
import com.example.ui.components.PushToTalkButton
import com.example.ui.components.SignalBarsIndicator
import com.example.ui.components.StatusPill
import com.example.ui.components.TacticalGlassCard
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

@Composable
fun CommsScreen(
  uiState: WalkieUiState,
  onScanDevicesClick: () -> Unit,
  onEnableBluetoothClick: () -> Unit,
  onRequestPermissionsClick: () -> Unit,
  onRequestMicPermissionClick: () -> Unit,
  onReconnectClick: () -> Unit,
  onDisconnectClick: () -> Unit,
  onPttPressed: () -> Unit,
  onPttReleased: () -> Unit,
  modifier: Modifier = Modifier,
) {
  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val isWideLayout = maxWidth >= 680.dp

    if (isWideLayout) {
      Row(
        modifier =
          Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(
          modifier =
            Modifier
              .weight(1f)
              .fillMaxHeight()
              .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          CommsHeader(
            uiState = uiState,
            onEnableBluetoothClick = onEnableBluetoothClick,
            onScanDevicesClick = onScanDevicesClick,
          )
          ConnectionTelemetryCard(
            uiState = uiState,
            onScanDevicesClick = onScanDevicesClick,
            onRequestPermissionsClick = onRequestPermissionsClick,
            onEnableBluetoothClick = onEnableBluetoothClick,
            onReconnectClick = onReconnectClick,
            onDisconnectClick = onDisconnectClick,
          )
        }
        Box(
          modifier = Modifier.weight(1f).fillMaxHeight(),
          contentAlignment = Alignment.Center,
        ) {
          PttDeckSection(
            uiState = uiState,
            onPttPressed = onPttPressed,
            onPttReleased = onPttReleased,
            onRequestMicPermissionClick = onRequestMicPermissionClick,
          )
        }
      }
    } else {
      Column(
        modifier =
          Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Column(
          modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          CommsHeader(
            uiState = uiState,
            onEnableBluetoothClick = onEnableBluetoothClick,
            onScanDevicesClick = onScanDevicesClick,
          )
          ConnectionTelemetryCard(
            uiState = uiState,
            onScanDevicesClick = onScanDevicesClick,
            onRequestPermissionsClick = onRequestPermissionsClick,
            onEnableBluetoothClick = onEnableBluetoothClick,
            onReconnectClick = onReconnectClick,
            onDisconnectClick = onDisconnectClick,
          )
        }
        Spacer(modifier = Modifier.weight(1f, fill = false))
        PttDeckSection(
          uiState = uiState,
          onPttPressed = onPttPressed,
          onPttReleased = onPttReleased,
          onRequestMicPermissionClick = onRequestMicPermissionClick,
        )
        PrivacyFooterBanner()
      }
    }
  }
}

@Composable
private fun CommsHeader(
  uiState: WalkieUiState,
  onEnableBluetoothClick: () -> Unit,
  onScanDevicesClick: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = stringResource(R.string.app_name),
        style = MaterialTheme.typography.headlineSmall,
        color = TextPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Spacer(modifier = Modifier.height(4.dp))
      val btLabel =
        when (uiState.availability) {
          BluetoothAvailability.AVAILABLE -> stringResource(R.string.status_bt_enabled)
          BluetoothAvailability.DISABLED -> stringResource(R.string.status_bt_disabled)
          BluetoothAvailability.UNSUPPORTED -> stringResource(R.string.status_bt_unavailable)
        }
      val btColor =
        when (uiState.availability) {
          BluetoothAvailability.AVAILABLE -> NeonCyan
          BluetoothAvailability.DISABLED -> WarningAmber
          BluetoothAvailability.UNSUPPORTED -> TransmitCrimson
        }
      StatusPill(text = btLabel, dotColor = btColor)
    }

    if (uiState.availability == BluetoothAvailability.DISABLED) {
      Button(
        onClick = onEnableBluetoothClick,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = WarningAmber, contentColor = DeepSpace),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        modifier = Modifier.testTag("enable_bluetooth_button"),
      ) {
        Icon(
          imageVector = Icons.Rounded.Bluetooth,
          contentDescription = null,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.size(6.dp))
        Text(text = "Turn On", style = MaterialTheme.typography.labelLarge)
      }
    } else {
      FilledTonalButton(
        onClick = onScanDevicesClick,
        shape = RoundedCornerShape(14.dp),
        colors =
          ButtonDefaults.filledTonalButtonColors(
            containerColor = GlassPanelElevated,
            contentColor = NeonCyan,
          ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        modifier = Modifier.testTag("home_scan_devices_button"),
      ) {
        Icon(
          imageVector = Icons.Rounded.BluetoothSearching,
          contentDescription = null,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.size(6.dp))
        Text(text = "Scan Devices", style = MaterialTheme.typography.labelMedium)
      }
    }
  }
}

@Composable
private fun ConnectionTelemetryCard(
  uiState: WalkieUiState,
  onScanDevicesClick: () -> Unit,
  onRequestPermissionsClick: () -> Unit,
  onEnableBluetoothClick: () -> Unit,
  onReconnectClick: () -> Unit,
  onDisconnectClick: () -> Unit,
) {
  val connColor =
    when (uiState.connectionState) {
      ConnectionState.CONNECTED -> SignalEmerald
      ConnectionState.CONNECTING,
      ConnectionState.RECONNECTING -> WarningAmber
      ConnectionState.LISTENING -> NeonCyan
      ConnectionState.DISCONNECTED -> TextMuted
    }
  val connLabel =
    when (uiState.connectionState) {
      ConnectionState.CONNECTED -> "CONNECTED"
      ConnectionState.CONNECTING -> "CONNECTING"
      ConnectionState.RECONNECTING -> "RECONNECTING"
      ConnectionState.LISTENING -> "STANDBY • LISTENING"
      ConnectionState.DISCONNECTED -> "DISCONNECTED"
    }

  TacticalGlassCard(
    borderColor = connColor,
    elevated = uiState.connectionState == ConnectionState.CONNECTED,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      StatusPill(
        text = connLabel,
        dotColor = connColor,
        pulsing =
          uiState.connectionState == ConnectionState.CONNECTING ||
            uiState.connectionState == ConnectionState.RECONNECTING ||
            uiState.connectionState == ConnectionState.LISTENING,
      )
      SignalBarsIndicator(rssiDbm = uiState.connectedDevice?.rssiDbm)
    }

    Spacer(modifier = Modifier.height(14.dp))

    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Icon(
        imageVector =
          when {
            uiState.connectionState == ConnectionState.CONNECTED -> Icons.Rounded.BluetoothConnected
            uiState.availability != BluetoothAvailability.AVAILABLE -> Icons.Rounded.BluetoothDisabled
            else -> Icons.Rounded.Bluetooth
          },
        contentDescription = null,
        tint = connColor,
        modifier = Modifier.size(32.dp),
      )
      Column(modifier = Modifier.weight(1f)) {
        val deviceTitle =
          uiState.connectedDevice?.name
            ?: uiState.lastDevice?.let { "Last: ${it.name}" }
            ?: "No Paired Link Active"
        Text(
          text = deviceTitle,
          style = MaterialTheme.typography.titleLarge,
          color = TextPrimary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        val subtitle =
          when {
            uiState.connectedDevice != null ->
              "${uiState.connectedDevice.address} • ${
                if (uiState.connectedDevice.isIncoming) "Incoming RFCOMM" else "Outgoing RFCOMM"
              }"
            !uiState.permissions.allBluetoothGranted ->
              "Bluetooth permissions required to discover & connect"
            uiState.availability == BluetoothAvailability.DISABLED ->
              "Bluetooth radio is turned off"
            uiState.connectionState == ConnectionState.LISTENING ->
              "Listening for secure RFCOMM connection..."
            else -> "Select a paired phone from Devices to start"
          }
        Text(
          text = subtitle,
          style = MaterialTheme.typography.labelSmall,
          color = TextSecondary,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Audio codec & live waveform strip
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      val channelText =
        when (uiState.channelState) {
          ChannelState.TRANSMITTING -> "TX ACTIVE"
          ChannelState.RECEIVING -> "RX ACTIVE"
          ChannelState.STANDBY -> "CHANNEL IDLE"
        }
      val channelColor =
        when (uiState.channelState) {
          ChannelState.TRANSMITTING -> TransmitCrimson
          ChannelState.RECEIVING -> SignalEmerald
          ChannelState.STANDBY -> TextSecondary
        }
      Text(
        text = channelText,
        style = MaterialTheme.typography.labelSmall,
        color = channelColor,
        fontWeight = FontWeight.Bold,
      )
      val codecInfo =
        if (uiState.channelState == ChannelState.RECEIVING && uiState.activeRemoteCodec != null) {
          "RX: ${uiState.activeRemoteCodec.label}"
        } else {
          "TX: ${uiState.settings.audioQuality.label}"
        }
      Text(
        text = codecInfo,
        style = MaterialTheme.typography.labelSmall,
        color = TextMuted,
      )
    }

    Spacer(modifier = Modifier.height(8.dp))
    LiveAudioWaveform(
      channelState = uiState.channelState,
      txLevel = uiState.txAudioLevel,
      rxLevel = uiState.rxAudioLevel,
    )

    Spacer(modifier = Modifier.height(12.dp))

    // Action row: context-sensitive Disconnect / Reconnect / Permissions / Scan
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      when {
        !uiState.permissions.allBluetoothGranted -> {
          Button(
            onClick = onRequestPermissionsClick,
            modifier = Modifier.weight(1f).testTag("grant_bt_permissions_button"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DeepSpace),
          ) {
            Text(text = "Grant Bluetooth Access", style = MaterialTheme.typography.labelLarge)
          }
        }
        uiState.availability == BluetoothAvailability.DISABLED -> {
          Button(
            onClick = onEnableBluetoothClick,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = WarningAmber, contentColor = DeepSpace),
          ) {
            Text(text = "Enable Bluetooth", style = MaterialTheme.typography.labelLarge)
          }
        }
        uiState.connectionState == ConnectionState.CONNECTED -> {
          OutlinedButton(
            onClick = onDisconnectClick,
            modifier = Modifier.weight(1f).testTag("home_disconnect_button"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TransmitCrimson),
          ) {
            Icon(
              imageVector = Icons.Rounded.LinkOff,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.size(6.dp))
            Text(text = "Disconnect", style = MaterialTheme.typography.labelLarge)
          }
        }
        else -> {
          Button(
            onClick = onScanDevicesClick,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue, contentColor = TextPrimary),
          ) {
            Text(text = "Find Peer", style = MaterialTheme.typography.labelLarge)
          }
          if (uiState.lastDevice != null) {
            OutlinedButton(
              onClick = onReconnectClick,
              modifier = Modifier.weight(1f).testTag("home_reconnect_button"),
              shape = RoundedCornerShape(14.dp),
              colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
            ) {
              Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.size(6.dp))
              Text(text = "Reconnect", style = MaterialTheme.typography.labelLarge)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun PttDeckSection(
  uiState: WalkieUiState,
  onPttPressed: () -> Unit,
  onPttReleased: () -> Unit,
  onRequestMicPermissionClick: () -> Unit,
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    PushToTalkButton(
      connectionState = uiState.connectionState,
      channelState = uiState.channelState,
      hasMicPermission = uiState.permissions.recordAudioGranted,
      txLevel = uiState.txAudioLevel,
      rxLevel = uiState.rxAudioLevel,
      onPressStart = onPttPressed,
      onPressEnd = onPttReleased,
      onRequestMicPermission = onRequestMicPermissionClick,
    )
  }
}

@Composable
private fun PrivacyFooterBanner() {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    modifier = Modifier.padding(bottom = 4.dp),
  ) {
    Icon(
      imageVector = Icons.Rounded.Security,
      contentDescription = null,
      tint = TextMuted,
      modifier = Modifier.size(14.dp),
    )
    Text(
      text = stringResource(R.string.privacy_banner),
      style = MaterialTheme.typography.labelSmall,
      color = TextMuted,
    )
  }
}
