package app.lart.mobile.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Harbor's dark palette (`src/index.css` tokens, converted from OKLCH to sRGB). */
object LartColors {
    val Canvas = Color(0xFF121316)
    val Surface = Color(0xFF1A1B1E)
    val Elevated = Color(0xFF25262A)
    val Raised = Color(0xFF323337)
    val Ink = Color(0xFFF4F5F7)
    val InkMuted = Color(0xFFA3A4A7)
    val InkSubtle = Color(0xFF8E8F92)
    val EdgeSoft = Color(0x40505257)
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
    surfaceContainerHighest = LartColors.Raised,
    outline = LartColors.InkSubtle,
)

@Composable
fun LartTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
