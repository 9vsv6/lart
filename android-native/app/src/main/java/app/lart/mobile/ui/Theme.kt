package app.lart.mobile.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import app.lart.mobile.data.AppSettings
import app.lart.mobile.data.Meta

/**
 * The active profile's look-and-feel settings, read by every screen as Compose state
 * (so changing one in Settings redraws the app at once). MainActivity keeps it current.
 */
object LartDisplay {
    var settings by mutableStateOf(AppSettings())
    var rpdbKey by mutableStateOf<String?>(null)

    /** RPDB's poster with ratings drawn on, for IMDb titles when a key is set. */
    fun posterFor(meta: Meta): String? {
        val key = rpdbKey ?: return meta.poster
        val imdb = meta.id.substringBefore(':').takeIf { it.startsWith("tt") } ?: return meta.poster
        return "https://api.ratingposterdb.com/$key/imdb/poster-default/$imdb.jpg"
    }
}

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
    /** Harbor's amber by default; Settings → Appearance changes it. */
    val Accent: Color get() = Color(LartDisplay.settings.accent.toInt())
}

private fun scheme(accent: Color) = darkColorScheme(
    primary = accent,
    onPrimary = Color(0xFF1A1206),
    secondary = accent,
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
    val accent = LartColors.Accent
    val colors = remember(accent) { scheme(accent) }
    MaterialTheme(colorScheme = colors, content = content)
}
