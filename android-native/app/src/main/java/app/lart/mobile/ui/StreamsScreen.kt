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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lart.mobile.data.Playback
import app.lart.mobile.data.RankedStream
import app.lart.mobile.data.Ranking
import app.lart.mobile.data.Ready
import app.lart.mobile.data.Repository
import app.lart.mobile.data.Stream
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun StreamsScreen(
    repository: Repository,
    type: String,
    metaId: String,
    videoId: String,
    title: String,
    onBack: () -> Unit,
    onPlay: (Ready) -> Unit,
) {
    val addons by repository.addons.collectAsState()
    val debrid by repository.debrid.collectAsState()
    val autoPlay by repository.autoPlay.collectAsState()
    val settings by repository.settings.collectAsState()
    val remembered = remember(metaId) { repository.preferredSource(metaId) }
    val scope = rememberCoroutineScope()
    var ranked by remember { mutableStateOf<List<RankedStream>?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    // Auto-play once per visit: coming back from the player shows the list instead.
    var autoPlayed by rememberSaveable(videoId) { mutableStateOf(false) }

    fun play(item: RankedStream) {
        val stream = item.stream
        repository.rememberStream(metaId, item.addon.manifest.id, Playback.streamKey(stream))
        job?.cancel()
        error = null
        status = if (stream.direct) null else "Getting the link from ${debrid?.service?.label ?: "debrid"}…"
        job = scope.launch {
            runCatching { Playback.resolve(stream, debrid) }
                .onSuccess { onPlay(it) }
                .onFailure { error = it.message }
            status = null
        }
    }

    LaunchedEffect(addons, type, videoId, debrid, settings.streamFilter, settings.streamSort, settings.safetyFilter, settings.streamLanguages, settings.torrentsDisabled, settings.requireStreamLanguage) {
        if (addons.isEmpty()) return@LaunchedEffect
        val list = Playback.ranked(addons, type, videoId, debrid, settings, prefer = remembered)
        ranked = list
        if (autoPlay && !autoPlayed) {
            autoPlayed = true
            status = "Playing the best source…"
            job = scope.launch {
                val best = Playback.best(list, debrid)
                status = null
                if (best != null) onPlay(best) else error = "None of the top sources would open. Pick one below."
            }
        }
    }

    Column(Modifier.fillMaxSize().background(LartColors.Canvas).statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        status?.let { message ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).background(LartColors.Elevated, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = LartColors.Accent)
                Text(message, fontSize = 14.sp, color = LartColors.Ink, modifier = Modifier.weight(1f))
                TextButton(onClick = { job?.cancel(); status = null }) { Text("Cancel") }
            }
        }
        error?.let {
            Text(
                it,
                fontSize = 13.sp,
                color = androidx.compose.ui.graphics.Color(0xFFE57373),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        val current = ranked
        when {
            current == null -> Loading()
            current.isEmpty() -> EmptyState(
                "No streams found",
                if (addons.none { it.manifest.supports("stream", type, videoId) }) {
                    "None of your addons offers streams for this. Add a stream addon in Stremio."
                } else "Your stream addons found nothing for this title.",
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(current) { index, item ->
                    val last = remembered != null && index == 0 && item.addon.manifest.id == remembered.first &&
                        (remembered.second == null || Playback.streamKey(item.stream) == remembered.second)
                    StreamRow(item, best = index == 0, last = last, showFilename = settings.showFilename, playable = item.stream.playable(debrid != null)) { play(item) }
                }
            }
        }
    }
}

@Composable
private fun StreamRow(item: RankedStream, best: Boolean, last: Boolean, showFilename: Boolean, playable: Boolean, onClick: () -> Unit) {
    val stream = item.stream
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (playable) 1f else 0.45f)
            .background(if (best && playable) LartColors.Elevated else LartColors.Surface, RoundedCornerShape(14.dp))
            .clickable(enabled = playable, onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                (stream.name ?: item.addon.manifest.name).replace('\n', ' '),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = LartColors.Accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (best && playable) Text(if (last) "Last played" else "Best", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LartColors.Ink)
        }
        val badges = Ranking.badges(stream)
        if (badges.isNotEmpty()) Text(badges.joinToString("  ·  "), fontSize = 12.sp, color = LartColors.InkMuted)
        Text(stream.text, fontSize = 13.sp, lineHeight = 18.sp, color = LartColors.Ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
        if (showFilename) stream.behaviorHints?.filename?.let { Text(it, fontSize = 11.sp, color = LartColors.InkSubtle, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        Text(
            when {
                playable -> item.addon.manifest.name
                stream.infoHash != null -> "Torrent: add a debrid account in Settings to play it"
                else -> "Not playable in Lart yet"
            },
            fontSize = 12.sp,
            color = LartColors.InkSubtle,
        )
    }
}
