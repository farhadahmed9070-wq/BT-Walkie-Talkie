package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val WalkieDarkColorScheme =
  darkColorScheme(
    primary = NeonCyan,
    onPrimary = DeepSpace,
    primaryContainer = GlassPanelElevated,
    onPrimaryContainer = NeonCyan,
    secondary = ElectricBlue,
    onSecondary = TextPrimary,
    secondaryContainer = GlassPanel,
    onSecondaryContainer = ElectricBlueBright,
    tertiary = SignalEmerald,
    onTertiary = DeepSpace,
    background = DeepSpace,
    onBackground = TextPrimary,
    surface = MidnightDeck,
    onSurface = TextPrimary,
    surfaceVariant = GlassPanel,
    onSurfaceVariant = TextSecondary,
    outline = BorderGlass,
    outlineVariant = TextMuted,
    error = TransmitCrimson,
    onError = TextPrimary,
  )

val WalkieShapes =
  Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
  )

@Composable
fun BTWalkieTalkieTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = WalkieDarkColorScheme,
    typography = WalkieTypography,
    shapes = WalkieShapes,
    content = content,
  )
}
