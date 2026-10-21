package app.lart.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lart.mobile.data.AppSettings
import app.lart.mobile.data.Stream
import app.lart.mobile.data.StreamHints

/*
 * Harbor's Picker layout choice and its preview (streaming-sources-panel/picker-tab.tsx and
 * picker-layout-preview.tsx), and the Stream badges page (stream-badges-panel/badges-tab.tsx).
 */

/** The sample source both previews draw. */
private val SAMPLE = Stream(
    url = "https://example.invalid/sample.mkv",
    name = "Source\n4K",
    title = "Sample.Title.2024.2160p.WEB-DL.DV.HDR10.HEVC.DDP5.1.Atmos.mkv\n💾 14.2 GB",
    behaviorHints = StreamHints(filename = "Sample.Title.2024.2160p.WEB-DL.DV.HDR10.HEVC.DDP5.1.Atmos.mkv"),
)

/** Harbor's radio list: Condensed and Stremio, beside a small preview of the one picked. */
@Composable
internal fun PickerLayoutSetting(value: String, onChange: (String) -> Unit) {
    val choice: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("condensed" to "Condensed", "stremio" to "Stremio").forEach { (id, label) ->
                val on = id == value
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(LartColors.Surface)
                        .border(1.dp, if (on) LartColors.Accent.copy(alpha = 0.7f) else LartColors.EdgeSoft, RoundedCornerShape(12.dp))
                        .clickable { onChange(id) }.padding(horizontal = 16.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(Modifier.size(20.dp).clip(CircleShape).border(2.dp, if (on) LartColors.Accent else LartColors.InkSubtle, CircleShape), contentAlignment = Alignment.Center) {
                        if (on) Box(Modifier.size(10.dp).clip(CircleShape).background(LartColors.Accent))
                    }
                    Text(label, color = LartColors.Ink, fontSize = 16.5.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        if (maxWidth >= 640.dp) Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            choice(Modifier.weight(1f))
            PickerLayoutPreview(value, Modifier.width(340.dp))
        } else Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            choice(Modifier.fillMaxWidth())
            PickerLayoutPreview(value, Modifier.fillMaxWidth())
        }
    }
}

/** picker-layout-preview.tsx: the layout in miniature, with two sample sources. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PickerLayoutPreview(layout: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row {
            Text("Preview", color = LartColors.InkSubtle, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text("Sample Title", color = LartColors.InkSubtle, fontSize = 12.sp)
        }
        Box(
            Modifier.fillMaxWidth().height(184.dp).clip(RoundedCornerShape(12.dp)).background(LartColors.Canvas.copy(alpha = 0.4f))
                .border(1.dp, LartColors.EdgeSoft, RoundedCornerShape(12.dp)).padding(10.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (layout == "condensed") Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(LartColors.Canvas.copy(alpha = 0.7f)).padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.width(110.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFF3A4A5C), Color(0xFF1C2230)))),
                    ) { Column(Modifier.align(Alignment.TopEnd).padding(4.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) { FormatBadge(BadgeKind.P1080, 10.dp); FormatBadge(BadgeKind.WebDl, 10.dp) } }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Sample.Title.1080p.WEB-DL.AVC.mp4", color = LartColors.Ink, fontSize = 8.5.sp, lineHeight = 12.sp, fontFamily = FontFamily.Monospace)
                        Text("1.4 GB  ·  H.264", color = LartColors.InkMuted, fontSize = 7.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                        Row(
                            Modifier.clip(RoundedCornerShape(50)).background(LartColors.Ink).padding(horizontal = 12.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(Icons.Rounded.PlayArrow, null, tint = LartColors.Canvas, modifier = Modifier.size(10.dp))
                            Text("Play", color = LartColors.Canvas, fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Triple(BadgeKind.P1080, "1080p", "1.4 GB"), Triple(BadgeKind.P720, "720p", "768 MB")).forEachIndexed { i, (kind, label, size) ->
                        Row(
                            Modifier.clip(RoundedCornerShape(6.dp)).background(if (i == 0) LartColors.Ink.copy(alpha = 0.05f) else Color.Transparent)
                                .border(1.dp, if (i == 0) LartColors.Ink.copy(alpha = 0.35f) else LartColors.EdgeSoft, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            FormatBadge(kind, 18.dp)
                            Column {
                                Text(label, color = LartColors.InkMuted, fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
                                Text(size, color = LartColors.InkSubtle, fontSize = 7.5.sp)
                            }
                        }
                    }
                }
            } else Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Triple("1080p", "Sample.Title.1080p.WEB-DL.AVC.mp4", BadgeKind.P1080), Triple("720p", "Sample.Title.720p.WEB-DL.AVC.mp4", BadgeKind.P720)).forEach { (q, file, kind) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(LartColors.Elevated.copy(alpha = 0.4f)).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(Modifier.size(30.dp).clip(RoundedCornerShape(7.dp)).background(LartColors.Raised), contentAlignment = Alignment.Center) {
                            Text("S", color = LartColors.InkMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Source $q", color = LartColors.Ink, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            Text(file, color = LartColors.InkMuted, fontSize = 8.5.sp, maxLines = 1)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { FormatBadge(kind, 12.dp); FormatBadge(BadgeKind.WebDl, 12.dp) }
                        }
                        Box(Modifier.size(24.dp).clip(CircleShape).background(LartColors.Accent), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.PlayArrow, null, tint = LartColors.Canvas, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
    }
}

/** stream-row-preview.tsx: one stream row as the picker draws it, with the badges as set below. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StreamRowPreview(settings: AppSettings, caption: String) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(LartColors.Elevated.copy(alpha = 0.4f))
                .border(1.dp, LartColors.EdgeSoft, RoundedCornerShape(16.dp)).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(LartColors.Raised), contentAlignment = Alignment.Center) {
                Text("S", color = LartColors.InkMuted, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Source 4K", color = LartColors.Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(SAMPLE.behaviorHints?.filename.orEmpty(), color = LartColors.InkMuted, fontSize = 14.sp, lineHeight = 19.sp)
                val kinds = shownBadges(SAMPLE, settings)
                if (kinds.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    kinds.forEach { FormatBadge(it, 24.dp) }
                }
            }
            Box(Modifier.size(52.dp).clip(CircleShape).background(LartColors.Accent), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PlayArrow, null, tint = Color(0xFF121316), modifier = Modifier.size(26.dp))
            }
        }
        Text(caption, color = LartColors.InkSubtle, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

/** badges-tab.tsx: the format chips switch, then every badge to hide or put back. */
@Composable
internal fun StreamBadgesPage(settings: AppSettings, set: ((AppSettings) -> AppSettings) -> Unit) {
    HarborHeading("Stream format chips", "The little 4K, HDR, codec, and audio chips that ride along each stream in the play picker.")
    StreamRowPreview(settings, "Every change on this page shows up here first. The badges you hide below stay off rows exactly like this one.")
    ToggleRow(
        "Show format chips on stream rows",
        "The picker tags each stream with resolution, HDR flavor, codec, and audio format. Off hides them all.",
        settings.showQualityBadge,
    ) { v -> set { it.copy(showQualityBadge = v) } }

    HarborHeading("Badge art", "Every format badge Lart can show on streams. Pick one to hide it, or pick it again to put it back. Changes apply everywhere badges appear.")
    val hidden = settings.hiddenBadges
    if (hidden.isNotEmpty()) Row(
        Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(Icons.Outlined.Palette, null, tint = LartColors.InkMuted, modifier = Modifier.size(20.dp))
        RowText("Badges you have changed", if (hidden.size == 1) "1 badge is hidden." else "${hidden.size} badges are hidden.", Modifier.weight(1f))
        HGhostButton(onClick = { set { it.copy(hiddenBadges = emptySet()) } }) { Text("Show all badges") }
    }
    BadgeGroup.entries.forEach { group ->
        SettingsLabel(group.label, Modifier.padding(top = 14.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = (maxWidth / 122.dp).toInt().coerceAtLeast(2)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BadgeKind.entries.filter { it.group == group }.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { kind ->
                            val off = kind.id in hidden
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(LartColors.Elevated)
                                    .border(1.dp, if (off) LartColors.Accent.copy(alpha = 0.6f) else LartColors.EdgeSoft, RoundedCornerShape(10.dp))
                                    .clickable { set { it.copy(hiddenBadges = if (off) it.hiddenBadges - kind.id else it.hiddenBadges + kind.id) } }
                                    .padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(6.dp)).background(LartColors.Canvas), contentAlignment = Alignment.Center) {
                                    if (off) Icon(Icons.Outlined.VisibilityOff, "Hidden", tint = LartColors.InkSubtle, modifier = Modifier.size(20.dp))
                                    else FormatBadge(kind, 26.dp)
                                    if (off) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(10.dp).clip(CircleShape).background(LartColors.Accent))
                                }
                                Text(kind.label, color = LartColors.Ink, fontSize = 15.5.sp, lineHeight = 20.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth())
                            }
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}
