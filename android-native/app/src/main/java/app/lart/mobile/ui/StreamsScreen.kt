package app.lart.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lart.mobile.data.AddonClient
import app.lart.mobile.data.Repository
import app.lart.mobile.data.Stream

@Composable
fun StreamsScreen(
    repository: Repository,
    type: String,
    videoId: String,
    title: String,
    onBack: () -> Unit,
    onPlay: (Stream) -> Unit,
) {
    val addons by repository.addons.collectAsState()
    var groups by remember { mutableStateOf<List<AddonClient.AddonStreams>?>(null) }

    LaunchedEffect(addons, type, videoId) {
        if (addons.isEmpty()) return@LaunchedEffect
        groups = AddonClient.streams(addons, type, videoId)
    }

    Column(Modifier.fillMaxSize().background(LartColors.Canvas).statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val current = groups
        when {
            current == null -> Loading()
            current.all { it.streams.isEmpty() } -> EmptyState(
                "No streams found",
                if (current.isEmpty()) "None of your addons offers streams for this. Add a stream addon in Settings."
                else "Your stream addons found nothing for this title.",
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                current.filter { it.streams.isNotEmpty() }.forEach { group ->
                    item(key = "h:" + group.addon.transportUrl) {
                        Text(
                            group.addon.manifest.name,
                            modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = LartColors.InkSubtle,
                        )
                    }
                    items(group.streams.sortedByDescending { it.playable }) { stream -> StreamRow(stream) { onPlay(stream) } }
                }
            }
        }
    }
}

@Composable
private fun StreamRow(stream: Stream, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (stream.playable) 1f else 0.45f)
            .background(LartColors.Surface, RoundedCornerShape(14.dp))
            .clickable(enabled = stream.playable, onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        stream.name?.let { Text(it.replace('\n', ' '), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = LartColors.Accent) }
        Text(stream.text, fontSize = 13.sp, lineHeight = 18.sp, color = LartColors.Ink, maxLines = 4, overflow = TextOverflow.Ellipsis)
        if (!stream.playable) {
            Text(
                if (stream.infoHash != null) "Torrent: needs a debrid service (coming in the next phase)" else "Not playable in Lart yet",
                fontSize = 12.sp,
                color = LartColors.InkSubtle,
            )
        }
    }
}
