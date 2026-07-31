package com.flacplayer.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 深色低饱和配色：深炭灰背景 + 暖琥珀强调
val BgDeep = Color(0xFF14120F)
val SurfaceDark = Color(0xFF1E1B17)
val SurfaceVariant = Color(0xFF2A2620)
val AccentWarm = Color(0xFFD9A05B)
val AccentMuted = Color(0xFF8C6F4E)
val TextPrimary = Color(0xFFE8E2D9)
val TextSecondary = Color(0xFF9A917F)
val ErrorWarm = Color(0xFFCF7B6B)

private val DarkColors = darkColorScheme(
    primary = AccentWarm,
    onPrimary = Color(0xFF241A0E),
    secondary = AccentMuted,
    onSecondary = TextPrimary,
    background = BgDeep,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = TextSecondary,
    error = ErrorWarm
)

@Composable
fun FlacPlayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content
    )
}
