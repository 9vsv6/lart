package app.lart.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lart.mobile.data.AddonClient
import app.lart.mobile.data.Meta
import app.lart.mobile.data.Repository
import app.lart.mobile.data.Video
import coil.compose.AsyncImage

@Composable
fun DetailScreen(
    repository: Repository,
    type: String,
    id: String,
    onBack: () -> Unit,
    onStreams: (videoId: String, title: String) -> Unit,
) {
    val addons by repository.addons.collectAsState()
    val builtins by repository.builtins.collectAsState()
    var meta by remember { mutableStateOf<Meta?>(null) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(addons, builtins, type, id) {
        if (addons.isEmpty() || meta != null) return@LaunchedEffect
        val loaded = AddonClient.meta(repository.metaAddons(), type, id)
        meta = loaded
        // Built-ins load in the background; only give up once they have had their turn.
        failed = loaded == null && builtins.isNotEmpty()
    }

    Box(Modifier.fillMaxSize().background(LartColors.Canvas)) {
        val current = meta
        when {
            current != null -> DetailBody(repository, current, onStreams)
            failed -> EmptyState("Couldn't load this title", "None of your addons has details for it.")
            else -> Loading(Modifier.padding(top = 160.dp))
        }
        IconButton(
            onClick = onBack,
            modifier = Modifier.padding(top = 36.dp, start = 8.dp).background(Color(0x66000000), RoundedCornerShape(50)),
        ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White) }
    }
}

@Composable
private fun DetailBody(repository: Repository, meta: Meta, onStreams: (String, String) -> Unit) {
    val videos = meta.videos.orEmpty()
    val isSeries = meta.type != "movie" && videos.isNotEmpty()
    // Season 0 holds specials; start on the first real season.
    val seasons = remember(videos) { videos.mapNotNull { it.season }.distinct().sortedWith(compareBy({ it == 0 }, { it })) }
    var season by rememberSaveable(meta.id) { mutableStateOf(seasons.firstOrNull()) }
    val episodes = remember(videos, season) {
        videos.filter { it.season == season }.sortedBy { it.episode ?: 0 }
    }

    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
        item(key = "header") { Header(meta) }
        item(key = "actions") {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = {
                        if (isSeries) {
                            val next = nextEpisode(repository, meta, videos)
                            next?.let { onStreams(it.id, "${meta.name} · ${episodeLabel(it)}") }
                        } else onStreams(meta.id, meta.name)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Text(if (isSeries) "Play next episode" else "Play", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
                MyListButton(repository, meta)
                }
                meta.description?.let {
                    Text(it, fontSize = 14.5.sp, lineHeight = 21.sp, color = LartColors.InkMuted)
                }
            }
        }
        if (isSeries && seasons.isNotEmpty()) {
            item(key = "seasons") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(seasons) { s ->
                        FilterChip(
                            selected = s == season,
                            onClick = { season = s },
                            label = { Text(if (s == 0) "Specials" else "Season $s") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color.White,
                                selectedLabelColor = Color.Black,
                            ),
                        )
                    }
                }
            }
            items(episodes, key = { it.id }) { video ->
                EpisodeRow(video) { onStreams(video.id, "${meta.name} · ${episodeLabel(video)}") }
            }
        }
    }
}

@Composable
private fun MyListButton(repository: Repository, meta: Meta) {
    val myList by repository.myList.collectAsState()
    val listed = myList.any { it.id == meta.id }
    IconButton(
        onClick = { repository.toggleMyList(meta) },
        modifier = Modifier.size(52.dp).background(LartColors.Elevated, RoundedCornerShape(50)),
    ) {
        Icon(
            if (listed) Icons.Rounded.Check else Icons.Rounded.Add,
            contentDescription = if (listed) "In My List" else "Add to My List",
            tint = if (listed) LartColors.Accent else LartColors.Ink,
        )
    }
}

@Composable
private fun Header(meta: Meta) {
    Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f)) {
        AsyncImage(
            model = meta.background ?: meta.poster,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, LartColors.Canvas))))
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (meta.logo != null) {
                AsyncImage(model = meta.logo, contentDescription = meta.name, modifier = Modifier.heightIn(max = 72.dp))
            } else {
                Text(meta.name, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
            val line = listOfNotNull(meta.releaseInfo, meta.runtime, meta.imdbRating?.let { "IMDb $it" })
            if (line.isNotEmpty()) Text(line.joinToString("  ·  "), fontSize = 13.sp, color = Color(0xCCFFFFFF))
            meta.genres?.takeIf { it.isNotEmpty() }?.let {
                Text(it.take(3).joinToString(" · "), fontSize = 13.sp, color = LartColors.InkMuted)
            }
        }
    }
}

@Composable
private fun EpisodeRow(video: Video, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.width(140.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(LartColors.Elevated),
        ) {
            if (video.thumbnail != null) {
                AsyncImage(video.thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(episodeLabel(video), fontSize = 12.sp, color = LartColors.Accent, fontWeight = FontWeight.SemiBold)
            Text(video.label, fontSize = 15.sp, color = LartColors.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            video.released?.take(10)?.let { Text(it, fontSize = 12.sp, color = LartColors.InkSubtle) }
        }
    }
}

private fun episodeLabel(video: Video): String =
    if (video.season != null && video.episode != null) "S${video.season} · E${video.episode}" else video.label

/** The episode after the last one watched (from the Stremio library), else the first. */
private fun nextEpisode(repository: Repository, meta: Meta, videos: List<Video>): Video? {
    val ordered = videos.filter { (it.season ?: 0) > 0 }.sortedWith(compareBy({ it.season }, { it.episode }))
        .ifEmpty { videos }
    val lastId = repository.library.value.firstOrNull { it.id == meta.id }?.state?.videoId
    val index = ordered.indexOfFirst { it.id == lastId }
    return when {
        index < 0 -> ordered.firstOrNull()
        repository.resumeFor(ordered[index].id, meta.id) > 0 -> ordered[index]
        else -> ordered.getOrNull(index + 1) ?: ordered[index]
    }
}
