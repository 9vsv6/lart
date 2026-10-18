package app.lart.mobile.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Lart's dark palette: near-black canvas, raised surfaces, warm orange accent. */
object LartColors {
    val Canvas = Color(0xFF111316)
    val Surface = Color(0xFF1A1C20)
    val Elevated = Color(0xFF24272C)
    val Ink = Color(0xFFF2F2F3)
    val InkMuted = Color(0xFFA9ADB4)
    val InkSubtle = Color(0xFF6E737B)
    val Accent = Color(0xFFF0A35E)
}

private val scheme = darkColorScheme(
    primary = LartColors.Accent,
    onPrimary = Color(0xFF1A1206),
    secondary = LartColors.Accent,
    background = LartColors.Canvas,
    onBackground = LartColors.Ink,
    surface = LartColors.Surface,
    onSurface = LartColors.Ink,
    surfaceVariant = LartColors.Elevated,
    onSurfaceVariant = LartColors.InkMuted,
    surfaceContainer = LartColors.Surface,
    surfaceContainerHigh = LartColors.Elevated,
    outline = LartColors.InkSubtle,
)

@Composable
fun LartTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
