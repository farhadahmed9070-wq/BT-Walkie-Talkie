package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.model.ChannelState
import com.example.model.ConnectionState
import com.example.ui.theme.BorderGlass
import com.example.ui.theme.DeepSpace
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.GlassPanel
import com.example.ui.theme.GlassPanelElevated
import com.example.ui.theme.MidnightDeck
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TransmitCrimson
import com.example.ui.theme.WarningAmber
import kotlin.math.sin

@Composable
fun FuturisticBackdrop(
  modifier: Modifier = Modifier,
  accentColor: Color = NeonCyan,
  content: @Composable () -> Unit,
) {
  val animatedAccent by animateColorAsState(
    targetValue = accentColor,
    animationSpec = tween(450),
    label = "backdropAccent",
  )
  Box(
    modifier =
      modifier
        .fillMaxSize()
        .background(DeepSpace)
        .drawBehind {
          // Top atmospheric radial glow
          drawCircle(
            brush =
              Brush.radialGradient(
                colors = listOf(animatedAccent.copy(alpha = 0.14f), Color.Transparent),
                center = Offset(size.width * 0.5f, size.height * 0.12f),
                radius = size.maxDimension * 0.55f,
              ),
            radius = size.maxDimension * 0.55f,
            center = Offset(size.width * 0.5f, size.height * 0.12f),
          )
          // Bottom-right subtle electric blue depth glow
          drawCircle(
            brush =
              Brush.radialGradient(
                colors = listOf(ElectricBlue.copy(alpha = 0.12f), Color.Transparent),
                center = Offset(size.width * 0.85f, size.height * 0.85f),
                radius = size.maxDimension * 0.45f,
              ),
            radius = size.maxDimension * 0.45f,
            center = Offset(size.width * 0.85f, size.height * 0.85f),
          )
        },
  ) {
    content()
  }
}

@Composable
fun TacticalGlassCard(
  modifier: Modifier = Modifier,
  borderColor: Color = BorderGlass,
  elevated: Boolean = false,
  content: @Composable ColumnScope.() -> Unit,
) {
  val bgTop = if (elevated) GlassPanelElevated.copy(alpha = 0.82f) else GlassPanel.copy(alpha = 0.72f)
  val bgBottom = MidnightDeck.copy(alpha = 0.88f)
  Surface(
    modifier =
      modifier
        .fillMaxWidth()
        .border(
          width = 1.dp,
          brush =
            Brush.linearGradient(
              colors = listOf(borderColor.copy(alpha = 0.75f), BorderGlass.copy(alpha = 0.25f)),
            ),
          shape = RoundedCornerShape(22.dp),
        ),
    shape = RoundedCornerShape(22.dp),
    color = Color.Transparent,
  ) {
    Column(
      modifier =
        Modifier
          .background(Brush.verticalGradient(colors = listOf(bgTop, bgBottom)))
          .padding(16.dp),
      content = content,
    )
  }
}

@Composable
fun StatusPill(
  text: String,
  dotColor: Color,
  modifier: Modifier = Modifier,
  pulsing: Boolean = false,
) {
  val infiniteTransition = rememberInfiniteTransition(label = "pillPulse")
  val pulseAlpha by
    infiniteTransition.animateFloat(
      initialValue = 0.4f,
      targetValue = 1f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(700, easing = FastOutSlowInEasing),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "dotPulse",
    )

  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(50),
    color = dotColor.copy(alpha = 0.12f),
    border = BorderStroke(1.dp, dotColor.copy(alpha = 0.45f)),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Box(
        modifier =
          Modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(dotColor.copy(alpha = if (pulsing) pulseAlpha else 1f)),
      )
      Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = dotColor,
        fontWeight = FontWeight.Bold,
      )
    }
  }
}

@Composable
fun SignalBarsIndicator(
  rssiDbm: Int?,
  modifier: Modifier = Modifier,
) {
  if (rssiDbm == null) {
    Text(
      text = "RSSI: N/A",
      style = MaterialTheme.typography.labelSmall,
      color = TextMuted,
      modifier = modifier,
    )
    return
  }

  val strengthLevel =
    when {
      rssiDbm >= -60 -> 4
      rssiDbm >= -72 -> 3
      rssiDbm >= -84 -> 2
      else -> 1
    }
  val activeColor =
    when (strengthLevel) {
      4, 3 -> SignalEmerald
      2 -> WarningAmber
      else -> TransmitCrimson
    }

  Row(
    modifier = modifier,
    verticalAlignment = Alignment.Bottom,
    horizontalArrangement = Arrangement.spacedBy(3.dp),
  ) {
    for (bar in 1..4) {
      val barHeight = (5 + bar * 3).dp
      Box(
        modifier =
          Modifier
            .width(4.dp)
            .height(barHeight)
            .clip(RoundedCornerShape(2.dp))
            .background(if (bar <= strengthLevel) activeColor else TextMuted.copy(alpha = 0.35f)),
      )
    }
    Spacer(modifier = Modifier.width(4.dp))
    Text(
      text = "$rssiDbm dBm",
      style = MaterialTheme.typography.labelSmall,
      color = activeColor,
    )
  }
}

@Composable
fun LiveAudioWaveform(
  channelState: ChannelState,
  txLevel: Float,
  rxLevel: Float,
  modifier: Modifier = Modifier,
) {
  val activeLevel =
    when (channelState) {
      ChannelState.TRANSMITTING -> txLevel
      ChannelState.RECEIVING -> rxLevel
      ChannelState.STANDBY -> 0f
    }
  val activeColor by
    animateColorAsState(
      targetValue =
        when (channelState) {
          ChannelState.TRANSMITTING -> TransmitCrimson
          ChannelState.RECEIVING -> SignalEmerald
          ChannelState.STANDBY -> NeonCyan.copy(alpha = 0.35f)
        },
      animationSpec = tween(250),
      label = "waveColor",
    )

  val infiniteTransition = rememberInfiniteTransition(label = "wavePhase")
  val phase by
    infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = (2f * Math.PI).toFloat(),
      animationSpec = infiniteRepeatable(animation = tween(900), repeatMode = RepeatMode.Restart),
      label = "phase",
    )

  val smoothedLevel by
    animateFloatAsState(
      targetValue = activeLevel,
      animationSpec = tween(80),
      label = "smoothAmp",
    )

  Canvas(modifier = modifier.fillMaxWidth().height(36.dp)) {
    val barCount = 24
    val spacing = size.width / (barCount * 2f - 1f)
    val barWidth = spacing
    val centerY = size.height / 2f

    for (i in 0 until barCount) {
      val normPos = (i.toFloat() / (barCount - 1)) * 2f - 1f // -1..1
      val centerEnvelope = 1f - (normPos * normPos) * 0.65f
      val waveFactor = 0.5f + 0.5f * sin(phase + i * 0.55f)
      val barRatio =
        if (channelState == ChannelState.STANDBY) {
          0.12f
        } else {
          (0.16f + smoothedLevel * 0.84f * waveFactor * centerEnvelope).coerceIn(0.12f, 1f)
        }
      val barH = size.height * barRatio
      val left = i * spacing * 2f
      drawRoundRect(
        color = activeColor,
        topLeft = Offset(left, centerY - barH / 2f),
        size = Size(barWidth, barH),
        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
      )
    }
  }
}

@Composable
fun PushToTalkButton(
  connectionState: ConnectionState,
  channelState: ChannelState,
  hasMicPermission: Boolean,
  txLevel: Float,
  rxLevel: Float,
  buttonSize: Dp = 216.dp,
  onPressStart: () -> Unit,
  onPressEnd: () -> Unit,
  onRequestMicPermission: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var isFingerDown by remember { mutableStateOf(false) }

  val currentConnectionState by rememberUpdatedState(connectionState)
  val currentChannelState by rememberUpdatedState(channelState)
  val currentHasMic by rememberUpdatedState(hasMicPermission)
  val latestOnPressStart by rememberUpdatedState(onPressStart)
  val latestOnPressEnd by rememberUpdatedState(onPressEnd)
  val latestOnRequestMic by rememberUpdatedState(onRequestMicPermission)

  val isConnected = connectionState == ConnectionState.CONNECTED
  val isTransmitting = channelState == ChannelState.TRANSMITTING
  val isReceiving = channelState == ChannelState.RECEIVING

  val primaryGlowColor by
    animateColorAsState(
      targetValue =
        when {
          !isConnected -> TextMuted
          !hasMicPermission -> WarningAmber
          isTransmitting -> TransmitCrimson
          isReceiving -> SignalEmerald
          else -> NeonCyan
        },
      animationSpec = tween(250),
      label = "pttGlow",
    )

  val scale by
    animateFloatAsState(
      targetValue = if (isFingerDown && isConnected && !isReceiving) 0.94f else 1f,
      animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 500f),
      label = "pttScale",
    )

  val innerShadowElevation by
    animateDpAsState(
      targetValue = if (isFingerDown && isConnected && !isReceiving) 2.dp else 12.dp,
      animationSpec = tween(120),
      label = "pttDepth",
    )

  val infiniteTransition = rememberInfiniteTransition(label = "pttRings")
  val ringExpand by
    infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(animation = tween(1300), repeatMode = RepeatMode.Restart),
      label = "ringExpand",
    )

  val liveAmplitude =
    when {
      isTransmitting -> txLevel
      isReceiving -> rxLevel
      else -> 0f
    }

  val statusTitle =
    when {
      !isConnected -> "CONNECT TO TALK"
      !hasMicPermission -> "GRANT MIC ACCESS"
      isTransmitting -> "TRANSMITTING"
      isReceiving -> "CHANNEL BUSY"
      else -> "HOLD TO TALK"
    }

  val statusSubtitle =
    when {
      !isConnected -> "Pair & link a device"
      !hasMicPermission -> "Tap to allow microphone"
      isTransmitting -> "Release to stop"
      isReceiving -> "Receiving peer voice..."
      else -> "Half-duplex RFCOMM"
    }

  Box(
    modifier =
      modifier
        .size(buttonSize + 54.dp)
        .testTag("push_to_talk_button")
        .semantics { contentDescription = "Push to Talk: $statusTitle" },
    contentAlignment = Alignment.Center,
  ) {
    // Animated outer neon radar/amplitude rings
    Canvas(modifier = Modifier.fillMaxSize()) {
      val center = Offset(size.width / 2f, size.height / 2f)
      val baseRadius = buttonSize.toPx() / 2f

      if (isTransmitting || isReceiving) {
        val pulseRadius1 = baseRadius + 8.dp.toPx() + ringExpand * 22.dp.toPx()
        val pulseRadius2 = baseRadius + 4.dp.toPx() + liveAmplitude * 18.dp.toPx()
        drawCircle(
          color = primaryGlowColor.copy(alpha = (1f - ringExpand) * 0.45f),
          radius = pulseRadius1,
          center = center,
          style = Stroke(width = 2.5.dp.toPx()),
        )
        drawCircle(
          color = primaryGlowColor.copy(alpha = 0.25f + liveAmplitude * 0.4f),
          radius = pulseRadius2,
          center = center,
          style = Stroke(width = 3.dp.toPx()),
        )
      } else if (isConnected) {
        drawCircle(
          color = primaryGlowColor.copy(alpha = 0.22f),
          radius = baseRadius + 10.dp.toPx(),
          center = center,
          style = Stroke(width = 1.5.dp.toPx()),
        )
      }
    }

    // 3D Hardware Bezel & Dome
    Box(
      modifier =
        Modifier
          .size(buttonSize)
          .scale(scale)
          .clip(CircleShape)
          .background(
            brush =
              Brush.linearGradient(
                colors =
                  listOf(
                    primaryGlowColor.copy(alpha = if (isConnected) 0.55f else 0.22f),
                    MidnightDeck,
                    ElectricBlue.copy(alpha = if (isConnected) 0.35f else 0.12f),
                  ),
              ),
          )
          .padding(if (innerShadowElevation > 6.dp) 10.dp else 12.dp)
          .clip(CircleShape)
          .background(
            brush =
              Brush.radialGradient(
                colors =
                  listOf(
                    if (isTransmitting) TransmitCrimson.copy(alpha = 0.38f)
                    else if (isReceiving) SignalEmerald.copy(alpha = 0.28f)
                    else GlassPanelElevated,
                    DeepSpace,
                  ),
                radius = 420f,
              ),
          )
          .border(
            width = 2.dp,
            brush =
              Brush.verticalGradient(
                colors =
                  listOf(
                    primaryGlowColor.copy(alpha = 0.9f),
                    primaryGlowColor.copy(alpha = 0.2f),
                  ),
              ),
            shape = CircleShape,
          )
          .pointerInput(Unit) {
            awaitEachGesture {
              awaitFirstDown(requireUnconsumed = false)
              if (currentConnectionState != ConnectionState.CONNECTED) {
                waitForUpOrCancellation()
                return@awaitEachGesture
              }
              if (!currentHasMic) {
                waitForUpOrCancellation()
                latestOnRequestMic()
                return@awaitEachGesture
              }
              if (currentChannelState == ChannelState.RECEIVING) {
                latestOnPressStart()
                waitForUpOrCancellation()
                return@awaitEachGesture
              }
              isFingerDown = true
              latestOnPressStart()
              try {
                waitForUpOrCancellation()
              } finally {
                isFingerDown = false
                latestOnPressEnd()
              }
            }
          },
      contentAlignment = Alignment.Center,
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(16.dp),
      ) {
        Icon(
          imageVector =
            when {
              !isConnected -> Icons.Rounded.BluetoothDisabled
              !hasMicPermission -> Icons.Rounded.MicOff
              isTransmitting -> Icons.Rounded.GraphicEq
              isReceiving -> Icons.AutoMirrored.Rounded.VolumeUp
              else -> Icons.Rounded.Mic
            },
          contentDescription = null,
          tint = if (isConnected) primaryGlowColor else TextMuted,
          modifier = Modifier.size(54.dp),
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
          text = statusTitle,
          style = MaterialTheme.typography.labelLarge,
          color = if (isConnected) TextPrimary else TextSecondary,
          textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          if (isConnected && !isReceiving && hasMicPermission) {
            Icon(
              imageVector = Icons.Rounded.Lock,
              contentDescription = null,
              tint = TextSecondary,
              modifier = Modifier.size(11.dp),
            )
          }
          Text(
            text = statusSubtitle,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            textAlign = TextAlign.Center,
          )
        }
      }
    }
  }
}
