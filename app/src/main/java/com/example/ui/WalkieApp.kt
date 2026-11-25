package com.example.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BluetoothSearching
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.bluetooth.BluetoothPermissionHelper
import com.example.model.ChannelState
import com.example.ui.components.FuturisticBackdrop
import com.example.ui.screens.CommsScreen
import com.example.ui.screens.DevicesScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.DeepSpace
import com.example.ui.theme.GlassPanel
import com.example.ui.theme.GlassPanelElevated
import com.example.ui.theme.MidnightDeck
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TransmitCrimson
import com.example.viewmodel.WalkieViewModel

const val ROUTE_COMMS = "comms"
const val ROUTE_DEVICES = "devices"
const val ROUTE_SETTINGS = "settings"

private data class NavDestinationItem(
  val route: String,
  val labelRes: Int,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
  val testTag: String,
)

private val navDestinations =
  listOf(
    NavDestinationItem(
      route = ROUTE_COMMS,
      labelRes = R.string.nav_comms,
      selectedIcon = Icons.Rounded.GraphicEq,
      unselectedIcon = Icons.Outlined.GraphicEq,
      testTag = "nav_comms_tab",
    ),
    NavDestinationItem(
      route = ROUTE_DEVICES,
      labelRes = R.string.nav_devices,
      selectedIcon = Icons.Rounded.BluetoothSearching,
      unselectedIcon = Icons.Outlined.BluetoothSearching,
      testTag = "nav_devices_tab",
    ),
    NavDestinationItem(
      route = ROUTE_SETTINGS,
      labelRes = R.string.nav_settings,
      selectedIcon = Icons.Rounded.Settings,
      unselectedIcon = Icons.Outlined.Settings,
      testTag = "nav_settings_tab",
    ),
  )

@Composable
fun WalkieApp(
  viewModel: WalkieViewModel = viewModel(),
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  val navController = rememberNavController()
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = navBackStackEntry?.destination?.route ?: ROUTE_COMMS

  val snackbarHostState = remember { SnackbarHostState() }
  var showMicRationaleDialog by remember { mutableStateOf(false) }

  // Refresh permission and Bluetooth state on resume (e.g., returning from System Settings)
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        viewModel.refreshPermissionsAndBluetooth()
      } else if (event == Lifecycle.Event.ON_PAUSE) {
        // Ensure active mic transmission always stops if app is backgrounded
        viewModel.onPushToTalkReleased()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  LaunchedEffect(Unit) {
    viewModel.statusEvents.collect { message ->
      snackbarHostState.showSnackbar(message)
    }
  }

  val btPermissionLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
      viewModel.refreshPermissionsAndBluetooth()
    }

  val micPermissionLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
      viewModel.refreshPermissionsAndBluetooth()
      if (!granted) {
        showMicRationaleDialog = true
      }
    }

  val enableBtLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.StartActivityForResult(),
    ) {
      viewModel.refreshPermissionsAndBluetooth()
    }

  val discoverableLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.StartActivityForResult(),
    ) {
      viewModel.refreshPermissionsAndBluetooth()
    }

  val openAppSettings = {
    val intent =
      Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
      }
    context.startActivity(intent)
  }

  val requestBluetoothEnable = {
    if (!uiState.permissions.bluetoothConnectGranted) {
      btPermissionLauncher.launch(BluetoothPermissionHelper.requiredBluetoothPermissions())
    } else {
      val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
      enableBtLauncher.launch(enableIntent)
    }
  }

  val requestDiscoverable = {
    if (!uiState.permissions.bluetoothAdvertiseGranted || !uiState.permissions.bluetoothConnectGranted) {
      btPermissionLauncher.launch(BluetoothPermissionHelper.requiredBluetoothPermissions())
    } else {
      val discoverableIntent =
        Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
          putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120)
        }
      discoverableLauncher.launch(discoverableIntent)
    }
  }

  val backdropAccent =
    when (uiState.channelState) {
      ChannelState.TRANSMITTING -> TransmitCrimson
      ChannelState.RECEIVING -> SignalEmerald
      ChannelState.STANDBY -> NeonCyan
    }

  val navigateToTab: (String) -> Unit = { route ->
    navController.navigate(route) {
      popUpTo(navController.graph.findStartDestination().id) {
        saveState = true
      }
      launchSingleTop = true
      restoreState = true
    }
  }

  FuturisticBackdrop(accentColor = backdropAccent) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
      val useNavRail = maxWidth >= 680.dp

      Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
          if (!useNavRail) {
            NavigationBar(
              containerColor = MidnightDeck.copy(alpha = 0.94f),
              contentColor = TextPrimary,
              tonalElevation = 0.dp,
            ) {
              navDestinations.forEach { dest ->
                val selected = currentRoute == dest.route
                NavigationBarItem(
                  selected = selected,
                  onClick = { navigateToTab(dest.route) },
                  icon = {
                    Icon(
                      imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                      contentDescription = stringResource(dest.labelRes),
                    )
                  },
                  label = {
                    Text(
                      text = stringResource(dest.labelRes),
                      style = MaterialTheme.typography.labelSmall,
                    )
                  },
                  colors =
                    NavigationBarItemDefaults.colors(
                      selectedIconColor = NeonCyan,
                      selectedTextColor = NeonCyan,
                      indicatorColor = GlassPanelElevated,
                      unselectedIconColor = TextSecondary,
                      unselectedTextColor = TextSecondary,
                    ),
                  modifier = Modifier.testTag(dest.testTag),
                )
              }
            }
          }
        },
      ) { innerPadding ->
        Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
          if (useNavRail) {
            NavigationRail(
              containerColor = MidnightDeck.copy(alpha = 0.94f),
              contentColor = TextPrimary,
            ) {
              navDestinations.forEach { dest ->
                val selected = currentRoute == dest.route
                NavigationRailItem(
                  selected = selected,
                  onClick = { navigateToTab(dest.route) },
                  icon = {
                    Icon(
                      imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                      contentDescription = stringResource(dest.labelRes),
                    )
                  },
                  label = {
                    Text(
                      text = stringResource(dest.labelRes),
                      style = MaterialTheme.typography.labelSmall,
                    )
                  },
                  colors =
                    NavigationRailItemDefaults.colors(
                      selectedIconColor = NeonCyan,
                      selectedTextColor = NeonCyan,
                      indicatorColor = GlassPanelElevated,
                      unselectedIconColor = TextSecondary,
                      unselectedTextColor = TextSecondary,
                    ),
                  modifier = Modifier.testTag(dest.testTag),
                )
              }
            }
          }

          Box(modifier = Modifier.weight(1f).fillMaxSize()) {
            NavHost(
              navController = navController,
              startDestination = ROUTE_COMMS,
            ) {
              composable(ROUTE_COMMS) {
                CommsScreen(
                  uiState = uiState,
                  onScanDevicesClick = { navigateToTab(ROUTE_DEVICES) },
                  onEnableBluetoothClick = requestBluetoothEnable,
                  onRequestPermissionsClick = {
                    btPermissionLauncher.launch(
                      BluetoothPermissionHelper.requiredBluetoothPermissions(),
                    )
                  },
                  onRequestMicPermissionClick = {
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                  },
                  onReconnectClick = { viewModel.reconnectLastDevice() },
                  onDisconnectClick = { viewModel.disconnect() },
                  onPttPressed = { viewModel.onPushToTalkPressed() },
                  onPttReleased = { viewModel.onPushToTalkReleased() },
                )
              }

              composable(ROUTE_DEVICES) {
                DevicesScreen(
                  uiState = uiState,
                  onStartScan = { viewModel.startScan() },
                  onStopScan = { viewModel.stopScan() },
                  onMakeDiscoverable = requestDiscoverable,
                  onEnableBluetooth = requestBluetoothEnable,
                  onRequestBluetoothPermissions = {
                    btPermissionLauncher.launch(
                      BluetoothPermissionHelper.requiredBluetoothPermissions(),
                    )
                  },
                  onPairDevice = { address -> viewModel.pairDevice(address) },
                  onConnectDevice = { device ->
                    viewModel.connectToDevice(device)
                    navigateToTab(ROUTE_COMMS)
                  },
                  onDisconnect = { viewModel.disconnect() },
                  onReconnect = { viewModel.reconnectLastDevice() },
                  onDismissError = { viewModel.clearError() },
                )
              }

              composable(ROUTE_SETTINGS) {
                SettingsScreen(
                  uiState = uiState,
                  onVibrationChanged = { viewModel.setVibrationEnabled(it) },
                  onRogerBeepChanged = { viewModel.setRogerBeepEnabled(it) },
                  onSpeakerphoneChanged = { viewModel.setSpeakerphoneEnabled(it) },
                  onAudioQualityChanged = { viewModel.setAudioQuality(it) },
                  onMicGainChanged = { viewModel.setMicGain(it) },
                  onNoiseGateChanged = { viewModel.setNoiseGate(it) },
                  onAutoListenChanged = { viewModel.setAutoListenEnabled(it) },
                  onDisconnect = { viewModel.disconnect() },
                  onReconnect = { viewModel.reconnectLastDevice() },
                  onRequestAllPermissions = {
                    btPermissionLauncher.launch(BluetoothPermissionHelper.allRequiredPermissions())
                  },
                  onOpenAppSettings = openAppSettings,
                )
              }
            }
          }
        }
      }
    }
  }

  if (showMicRationaleDialog) {
    AlertDialog(
      onDismissRequest = { showMicRationaleDialog = false },
      containerColor = GlassPanel,
      titleContentColor = TextPrimary,
      textContentColor = TextSecondary,
      title = { Text("Microphone Permission Required") },
      text = {
        Text(
          "BT Walkie Talkie only captures microphone audio while you physically press and hold the Push-to-Talk button. No audio is ever stored or transmitted without your interaction.",
        )
      },
      confirmButton = {
        Button(
          onClick = {
            showMicRationaleDialog = false
            openAppSettings()
          },
          colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DeepSpace),
        ) {
          Text("Open Settings")
        }
      },
      dismissButton = {
        TextButton(onClick = { showMicRationaleDialog = false }) {
          Text("Cancel", color = TextSecondary)
        }
      },
    )
  }
}
