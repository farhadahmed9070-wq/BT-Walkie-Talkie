package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.DevicesOther
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.BluetoothAvailability
import com.example.model.ConnectionState
import com.example.model.DeviceCategory
import com.example.model.DiscoveredBtDevice
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
fun DevicesScreen(
  uiState: WalkieUiState,
  onStartScan: () -> Unit,
  onStopScan: () -> Unit,
  onMakeDiscoverable: () -> Unit,
  onEnableBluetooth: () -> Unit,
  onRequestBluetoothPermissions: () -> Unit,
  onPairDevice: (String) -> Unit,
  onConnectDevice: (DiscoveredBtDevice) -> Unit,
  onDisconnect: () -> Unit,
  onReconnect: () -> Unit,
  onDismissError: () -> Unit,
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
          text = "Bluetooth Transceivers",
          style = MaterialTheme.typography.headlineSmall,
          color = TextPrimary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "Pair and link two Android phones over offline Classic RFCOMM.",
          style = MaterialTheme.typography.bodyMedium,
          color = TextSecondary,
        )
      }

      if (uiState.availability == BluetoothAvailability.UNSUPPORTED) {
        item {
          TacticalGlassCard(borderColor = TransmitCrimson) {
            Text(
              text = "Bluetooth Hardware Unavailable",
              style = MaterialTheme.typography.titleMedium,
              color = TransmitCrimson,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "This device does not expose a Bluetooth adapter.",
              style = MaterialTheme.typography.bodyMedium,
              color = TextSecondary,
            )
          }
        }
      } else if (!uiState.permissions.allBluetoothGranted) {
        item {
          TacticalGlassCard(borderColor = WarningAmber) {
            Text(
              text = "Bluetooth Permissions Needed",
              style = MaterialTheme.typography.titleMedium,
              color = WarningAmber,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text =
                "Grant Bluetooth Connect and Scan permissions so the app can discover nearby phones and establish RFCOMM voice links.",
              style = MaterialTheme.typography.bodyMedium,
              color = TextSecondary,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
              onClick = onRequestBluetoothPermissions,
              shape = RoundedCornerShape(14.dp),
              colors = ButtonDefaults.buttonColors(containerColor = WarningAmber, contentColor = DeepSpace),
              modifier = Modifier.testTag("devices_grant_permissions_button"),
            ) {
              Text(text = "Grant Permissions", style = MaterialTheme.typography.labelLarge)
            }
          }
        }
      } else if (uiState.availability == BluetoothAvailability.DISABLED) {
        item {
          TacticalGlassCard(borderColor = WarningAmber) {
            Text(
              text = "Bluetooth Radio Is Off",
              style = MaterialTheme.typography.titleMedium,
              color = WarningAmber,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "Enable Bluetooth to view paired phones and scan nearby devices.",
              style = MaterialTheme.typography.bodyMedium,
              color = TextSecondary,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
              onClick = onEnableBluetooth,
              shape = RoundedCornerShape(14.dp),
              colors = ButtonDefaults.buttonColors(containerColor = WarningAmber, contentColor = DeepSpace),
              modifier = Modifier.testTag("devices_enable_bt_button"),
            ) {
              Icon(imageVector = Icons.Rounded.Bluetooth, contentDescription = null)
              Spacer(modifier = Modifier.width(8.dp))
              Text(text = "Enable Bluetooth", style = MaterialTheme.typography.labelLarge)
            }
          }
        }
      }

      // Scan & Discoverable Toolbar
      if (uiState.availability == BluetoothAvailability.AVAILABLE &&
        uiState.permissions.allBluetoothGranted
      ) {
        item {
          TacticalGlassCard(borderColor = NeonCyan) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Button(
                onClick = if (uiState.isScanning) onStopScan else onStartScan,
                modifier = Modifier.weight(1f).testTag("scan_devices_button"),
                shape = RoundedCornerShape(14.dp),
                colors =
                  ButtonDefaults.buttonColors(
                    containerColor = if (uiState.isScanning) TransmitCrimson else NeonCyan,
                    contentColor = if (uiState.isScanning) TextPrimary else DeepSpace,
                  ),
              ) {
                Icon(
                  imageVector = Icons.Rounded.BluetoothSearching,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = if (uiState.isScanning) "Stop Scan" else "Scan Devices",
                  style = MaterialTheme.typography.labelLarge,
                )
              }

              FilledTonalButton(
                onClick = onMakeDiscoverable,
                modifier = Modifier.weight(1f).testTag("make_discoverable_button"),
                shape = RoundedCornerShape(14.dp),
                colors =
                  ButtonDefaults.filledTonalButtonColors(
                    containerColor = GlassPanelElevated,
                    contentColor = NeonCyan,
                  ),
              ) {
                Icon(
                  imageVector = Icons.Rounded.Visibility,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Discoverable", style = MaterialTheme.typography.labelMedium)
              }
            }

            AnimatedVisibility(visible = uiState.isScanning) {
              Column {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                  modifier = Modifier.fillMaxWidth(),
                  color = NeonCyan,
                  trackColor = GlassPanelElevated,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                  text = "Scanning nearby Bluetooth Classic devices...",
                  style = MaterialTheme.typography.labelSmall,
                  color = NeonCyan,
                )
              }
            }
          }
        }
      }

      // Active Connection Banner
      val connected = uiState.connectedDevice
      if (connected != null && uiState.connectionState == ConnectionState.CONNECTED) {
        item {
          TacticalGlassCard(borderColor = SignalEmerald, elevated = true) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
              ) {
                Icon(
                  imageVector = Icons.Rounded.BluetoothConnected,
                  contentDescription = null,
                  tint = SignalEmerald,
                  modifier = Modifier.size(28.dp),
                )
                Column {
                  Text(
                    text = connected.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  Text(
                    text = "${connected.address} • Active RFCOMM Link",
                    style = MaterialTheme.typography.labelSmall,
                    color = SignalEmerald,
                  )
                }
              }
              OutlinedButton(
                onClick = onDisconnect,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TransmitCrimson),
                modifier = Modifier.testTag("devices_disconnect_button"),
              ) {
                Icon(
                  imageVector = Icons.Rounded.LinkOff,
                  contentDescription = "Disconnect",
                  modifier = Modifier.size(18.dp),
                )
              }
            }
          }
        }
      }

      // Error / Reconnect Banner
      if (uiState.lastError != null) {
        item {
          TacticalGlassCard(borderColor = TransmitCrimson) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = TransmitCrimson,
              )
              Text(
                text = uiState.lastError,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                modifier = Modifier.weight(1f),
              )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              if (uiState.lastDevice != null) {
                FilledTonalButton(
                  onClick = onReconnect,
                  shape = RoundedCornerShape(12.dp),
                  colors =
                    ButtonDefaults.filledTonalButtonColors(
                      containerColor = GlassPanelElevated,
                      contentColor = NeonCyan,
                    ),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(text = "Reconnect", style = MaterialTheme.typography.labelMedium)
                }
              }
              TextButton(onClick = onDismissError) {
                Text(text = "Dismiss", color = TextSecondary)
              }
            }
          }
        }
      }

      // Paired Devices Section
      item {
        Text(
          text = "PAIRED DEVICES (${uiState.pairedDevices.size})",
          style = MaterialTheme.typography.labelMedium,
          color = NeonCyan,
          modifier = Modifier.padding(top = 4.dp),
        )
      }

      if (uiState.pairedDevices.isEmpty()) {
        item {
          TacticalGlassCard {
            Text(
              text = "No paired Bluetooth devices found yet. Tap Discoverable on one phone and Scan Devices on the other.",
              style = MaterialTheme.typography.bodyMedium,
              color = TextSecondary,
            )
          }
        }
      } else {
        items(uiState.pairedDevices, key = { "paired_${it.address}" }) { device ->
          val isThisConnected =
            uiState.connectedDevice?.address == device.address &&
              uiState.connectionState == ConnectionState.CONNECTED
          val isThisConnecting =
            (uiState.connectionState == ConnectionState.CONNECTING ||
              uiState.connectionState == ConnectionState.RECONNECTING) &&
              uiState.lastDevice?.address == device.address

          DeviceListCard(
            device = device,
            isConnected = isThisConnected,
            isConnecting = isThisConnecting,
            onPair = { onPairDevice(device.address) },
            onConnect = { onConnectDevice(device) },
            onDisconnect = onDisconnect,
          )
        }
      }

      // Nearby Discovered Devices Section
      item {
        Text(
          text = "NEARBY DISCOVERED (${uiState.nearbyDevices.size})",
          style = MaterialTheme.typography.labelMedium,
          color = ElectricBlue,
          modifier = Modifier.padding(top = 6.dp),
        )
      }

      if (uiState.nearbyDevices.isEmpty()) {
        item {
          TacticalGlassCard {
            Text(
              text =
                if (uiState.isScanning) "Listening for nearby Bluetooth broadcasts..."
                else "Tap Scan Devices to discover nearby phones in discoverable mode.",
              style = MaterialTheme.typography.bodyMedium,
              color = TextMuted,
            )
          }
        }
      } else {
        items(uiState.nearbyDevices, key = { "nearby_${it.address}" }) { device ->
          DeviceListCard(
            device = device,
            isConnected = false,
            isConnecting = false,
            onPair = { onPairDevice(device.address) },
            onConnect = { onConnectDevice(device) },
            onDisconnect = onDisconnect,
          )
        }
      }
    }
  }
}

@Composable
private fun DeviceListCard(
  device: DiscoveredBtDevice,
  isConnected: Boolean,
  isConnecting: Boolean,
  onPair: () -> Unit,
  onConnect: () -> Unit,
  onDisconnect: () -> Unit,
) {
  val icon =
    when (device.category) {
      DeviceCategory.PHONE -> Icons.Rounded.Smartphone
      DeviceCategory.COMPUTER -> Icons.Rounded.Computer
      DeviceCategory.AUDIO -> Icons.Rounded.Headphones
      DeviceCategory.WEARABLE -> Icons.Rounded.Watch
      DeviceCategory.OTHER -> Icons.Rounded.DevicesOther
    }

  TacticalGlassCard(
    borderColor =
      when {
        isConnected -> SignalEmerald
        device.isBonded -> NeonCyan
        else -> ElectricBlue
      },
    elevated = isConnected,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = if (isConnected) SignalEmerald else NeonCyan,
        modifier = Modifier.size(30.dp),
      )

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = device.name,
          style = MaterialTheme.typography.titleMedium,
          color = TextPrimary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = device.address,
          style = MaterialTheme.typography.labelSmall,
          color = TextSecondary,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          StatusPill(
            text =
              when {
                isConnected -> "LINKED"
                device.isBonding -> "PAIRING"
                device.isBonded -> "PAIRED"
                else -> "UNPAIRED"
              },
            dotColor =
              when {
                isConnected -> SignalEmerald
                device.isBonded -> NeonCyan
                else -> WarningAmber
              },
          )
          if (device.rssiDbm != null) {
            SignalBarsIndicator(rssiDbm = device.rssiDbm)
          }
        }
      }

      when {
        isConnected -> {
          OutlinedButton(
            onClick = onDisconnect,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TransmitCrimson),
          ) {
            Text(text = "Disconnect", style = MaterialTheme.typography.labelMedium)
          }
        }
        isConnecting -> {
          CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            color = NeonCyan,
            strokeWidth = 2.5.dp,
          )
        }
        device.isBonded -> {
          Button(
            onClick = onConnect,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue, contentColor = TextPrimary),
            modifier = Modifier.testTag("connect_device_${device.address}"),
          ) {
            Text(text = "Connect", style = MaterialTheme.typography.labelMedium)
          }
        }
        else -> {
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilledTonalButton(
              onClick = onPair,
              enabled = !device.isBonding,
              shape = RoundedCornerShape(12.dp),
              colors =
                ButtonDefaults.filledTonalButtonColors(
                  containerColor = GlassPanelElevated,
                  contentColor = WarningAmber,
                ),
            ) {
              Text(
                text = if (device.isBonding) "Pairing..." else "Pair",
                style = MaterialTheme.typography.labelMedium,
              )
            }
            Button(
              onClick = onConnect,
              shape = RoundedCornerShape(12.dp),
              colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue, contentColor = TextPrimary),
            ) {
              Text(text = "Link", style = MaterialTheme.typography.labelMedium)
            }
          }
        }
      }
    }
  }
}
