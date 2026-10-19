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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lart.mobile.data.AddonClient
import app.lart.mobile.data.Meta
import app.lart.mobile.data.Playback
import app.lart.mobile.data.RankedStream
import app.lart.mobile.data.Ranking
import app.lart.mobile.data.Ready
import app.lart.mobile.data.Repository
import app.lart.mobile.data.Stream
import coil.compose.AsyncImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/*
 * The sources page, as Harbor's play picker (src/views/play-picker): Back and Refresh,
 * the year and genres over the title in Harbor's display serif, a banner for addons that
 * couldn't be reached, Both / Direct-debrid / P2P, an addon picker, facet filters, and a
 * card per source with its full text, quality badges, copy link and a round Play button.
 */

private enum class SourceMode(val label: String) { Both("Both"), Direct("Direct/debrid"), P2P("P2P") }

/** Harbor's facet filters, read from each source's name and title. */
private enum class Facet(val label: String, val values: List<String>) {
    Resolution("Resolution", listOf("4K", "1080p", "720p", "SD")),
    Source("Source", listOf("Remux", "Blu-ray", "WEB")),
    Codec("Codec", listOf("HEVC", "AV1", "H.264")),
    Hdr("HDR", listOf("Dolby Vision", "HDR", "SDR")),
    Audio("Audio", listOf("Atmos", "TrueHD", "DTS", "DD+", "5.1")),
}

private fun facetValues(stream: Stream): Map<Facet, Set<String>> {
    val t = Ranking.traits(stream)
    val text = listOfNotNull(stream.name, stream.title, stream.description, stream.behaviorHints?.filename).joinToString(" ")
    fun has(pattern: String) = Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(text)
    return mapOf(
        Facet.Resolution to setOf(
            when {
                t.resolution >= 2160 -> "4K"
                t.resolution >= 1080 -> "1080p"
                t.resolution >= 720 -> "720p"
                t.resolution > 0 -> "SD"
                else -> ""
            },
        ),
        Facet.Source to setOfNotNull("Remux".takeIf { t.remux }, "Blu-ray".takeIf { t.bluray && !t.remux }, "WEB".takeIf { t.web }),
        Facet.Codec to setOf(if (t.hevc) "HEVC" else if (t.av1) "AV1" else "H.264"),
        Facet.Hdr to setOfNotNull("Dolby Vision".takeIf { t.dolbyVision }, "HDR".takeIf { t.hdr }, "SDR".takeIf { !t.hdr && !t.dolbyVision }),
        Facet.Audio to setOfNotNull(
            "Atmos".takeIf { has("""\batmos\b""") },
            "TrueHD".takeIf { has("""\btrue-?hd\b""") },
            "DTS".takeIf { has("""\bdts""") },
            "DD+".takeIf { has("""\b(dd\+|ddp|e-?ac-?3|eac3)""") },
            "5.1".takeIf { has("""\b5[ .]1\b""") },
        ),
    )
}

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
    var meta by remember { mutableStateOf<Meta?>(null) }
    var ranked by remember { mutableStateOf<List<RankedStream>?>(null) }
    var failures by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var mode by rememberSaveable { mutableStateOf(SourceMode.Both) }
    var addonFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var facets by remember { mutableStateOf<Map<Facet, Set<String>>>(emptyMap()) }
    // Auto-play once per visit: coming back from the player shows the list instead.
    var autoPlayed by rememberSaveable(videoId) { mutableStateOf(false) }

    fun play(item: RankedStream) {
        val stream = item.stream
        repository.rememberStream(metaId, item.addon.manifest.id, Playback.streamKey(stream))
        job?.cancel()
        error = null
        status = if (stream.direct) null else "Getting the link from ${debrid?.service?.label ?: "debrid"}…"
        job = scope.launch {
            runCatching { Playback.resolve(stream, debrid, item.addon.manifest.id) }
                .onSuccess { onPlay(it) }
                .onFailure { error = it.message }
            status = null
        }
    }

    LaunchedEffect(type, metaId) {
        meta = runCatching { AddonClient.meta(repository.metaAddons(), type, metaId) }.getOrNull()
    }

    LaunchedEffect(addons, type, videoId, debrid, refreshKey, settings.streamFilter, settings.streamSort, settings.safetyFilter, settings.streamLanguages, settings.torrentsDisabled, settings.requireStreamLanguage) {
        if (addons.isEmpty()) return@LaunchedEffect
        if (refreshKey > 0) ranked = null
        val list = Playback.ranked(addons, type, videoId, debrid, settings, prefer = remembered, onFailures = { failures = it })
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

    val all = ranked
    val shown = remember(all, mode, addonFilter, facets, debrid) {
        all.orEmpty().filter { item ->
            val torrent = item.stream.infoHash != null && !item.stream.direct
            val modeOk = when (mode) {
                SourceMode.Both -> true
                SourceMode.Direct -> item.stream.direct || (torrent && debrid != null)
                SourceMode.P2P -> torrent
            }
            val addonOk = addonFilter == null || item.addon.manifest.id == addonFilter
            val values = facetValues(item.stream)
            modeOk && addonOk && facets.all { (facet, picked) -> picked.isEmpty() || values[facet].orEmpty().any { it in picked } }
        }
    }

    val listState = rememberLazyListState()
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 2 } }

    BoxWithConstraints(Modifier.fillMaxSize().background(LartColors.Canvas)) {
        val wide = maxWidth >= 600.dp
        val side = if (wide) 40.dp else 18.dp
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = side, end = side, top = 12.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "head") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    HeaderPill(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Back", spin = false, onClick = onBack)
                    Spacer(Modifier.weight(1f))
                    HeaderPill(Icons.Rounded.Refresh, "Refresh", spin = all == null) {
                        refreshKey++
                        failures = emptyList()
                    }
                }
            }
            item(key = "title") {
                val current = meta
                Column(Modifier.padding(top = 10.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val eyebrow = listOfNotNull(current?.year) + current?.genres.orEmpty().take(2)
                    if (eyebrow.isNotEmpty()) {
                        Text(eyebrow.joinToString("  ·  ").uppercase(), color = LartColors.InkSubtle, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 3.5.sp)
                    }
                    val name = current?.name?.takeIf { it.isNotBlank() } ?: title.substringBefore(" · ")
                    Text(
                        name,
                        color = LartColors.Ink,
                        fontSize = if (wide) 60.sp else 42.sp,
                        lineHeight = if (wide) 60.sp else 44.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = (-0.5).sp,
                    )
                    title.substringAfter(" · ", "").takeIf { it.isNotBlank() }?.let {
                        Text(it, color = LartColors.InkMuted, fontSize = 15.sp)
                    }
                }
            }
            if (failures.isNotEmpty()) {
                item(key = "outage") {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Color(0x332A2000))
                            .border(1.dp, Color(0x80C9A227), RoundedCornerShape(26.dp)).padding(start = 22.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                if (failures.size == 1) "A source couldn't be reached" else "${failures.size} sources couldn't be reached",
                                color = Color(0xFFF3E7C5),
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            failures.take(3).forEach { (name, reason) ->
                                Text("$name — ${reason.take(60).lowercase().ifBlank { "couldn't be reached" }}", color = Color(0xFFE2D3A6), fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Box(
                            Modifier.clip(RoundedCornerShape(50)).background(LartColors.Elevated).clickable { refreshKey++; failures = emptyList() }
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                        ) { Text("Recheck", color = LartColors.Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
            status?.let { message ->
                item(key = "status") {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(LartColors.Elevated).padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = LartColors.Accent)
                        Text(message, fontSize = 14.sp, color = LartColors.Ink, modifier = Modifier.weight(1f))
                        Text("Cancel", color = LartColors.InkMuted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { job?.cancel(); status = null }.padding(8.dp))
                    }
                }
            }
            error?.let { message ->
                item(key = "error") { Text(message, fontSize = 14.sp, color = Color(0xFFE57373)) }
            }
            item(key = "mode") {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    Row(Modifier.clip(RoundedCornerShape(50)).background(LartColors.Surface).border(1.dp, LartColors.EdgeSoft, RoundedCornerShape(50)).padding(4.dp)) {
                        SourceMode.entries.forEach { entry ->
                            val on = entry == mode
                            Box(
                                Modifier.clip(RoundedCornerShape(50)).background(if (on) LartColors.Accent else Color.Transparent)
                                    .clickable { mode = entry }.padding(horizontal = 14.dp, vertical = 8.dp),
                            ) { Text(entry.label, color = if (on) Color(0xFF1A1206) else LartColors.InkMuted, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold) }
                        }
                    }
                }
            }
            item(key = "addon") { AddonPicker(all.orEmpty(), addonFilter) { addonFilter = it } }
            item(key = "facets") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(Facet.entries) { facet ->
                        FacetChip(facet, facets[facet].orEmpty()) { picked -> facets = facets + (facet to picked) }
                    }
                }
            }
            when {
                all == null -> item(key = "loading") { Loading() }
                shown.isEmpty() -> item(key = "empty") {
                    EmptyState(
                        "No sources found",
                        when {
                            all.isNotEmpty() -> "No source matches these filters."
                            addons.none { it.manifest.supports("stream", type, videoId) } -> "None of your addons offers streams for this. Add a stream addon in Settings → Addons."
                            else -> "Your stream addons found nothing for this title."
                        },
                    )
                }
                else -> items(shown, key = { it.addon.manifest.id + "|" + (it.stream.url ?: it.stream.infoHash) + "|" + it.stream.name + "|" + it.stream.title }) { item ->
                    val last = remembered != null && item.addon.manifest.id == remembered.first &&
                        (remembered.second == null || Playback.streamKey(item.stream) == remembered.second) && item === shown.first()
                    SourceCard(item, wide, last, settings.showFilename, item.stream.playable(debrid != null)) { play(item) }
                }
            }
        }
        if (scrolled) {
            Row(
                Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 18.dp, bottom = 96.dp)
                    .clip(RoundedCornerShape(50)).background(LartColors.Accent)
                    .clickable { scope.launch { listState.animateScrollToItem(0) } }.padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Rounded.ArrowUpward, null, tint = Color(0xFF1A1206), modifier = Modifier.size(18.dp))
                Text("Top", color = Color(0xFF1A1206), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Harbor's header pills: a 48 dp ringed circle and a label. */
@Composable
private fun HeaderPill(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, spin: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick).padding(end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(46.dp).clip(CircleShape).background(LartColors.Elevated.copy(alpha = 0.7f)).border(1.dp, LartColors.EdgeSoft, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (spin) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = LartColors.InkMuted)
            else Icon(icon, null, tint = LartColors.InkMuted, modifier = Modifier.size(24.dp))
        }
        Text(label, color = LartColors.InkMuted, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** "All" or one addon, with how many sources each brought. */
@Composable
private fun AddonPicker(all: List<RankedStream>, selected: String?, onPick: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val byAddon = all.groupBy { it.addon.manifest.id }
    val name = selected?.let { id -> byAddon[id]?.firstOrNull()?.addon?.manifest?.name } ?: "All"
    Box {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(LartColors.Surface).clickable { open = true }.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(LartColors.Elevated), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.GridView, null, tint = LartColors.InkMuted, modifier = Modifier.size(18.dp))
            }
            Text(name, color = LartColors.Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("${(selected?.let { byAddon[it] } ?: all).size}", color = LartColors.InkSubtle, fontSize = 13.sp)
            Icon(Icons.Rounded.KeyboardArrowDown, null, tint = LartColors.InkMuted, modifier = Modifier.padding(end = 6.dp))
        }
        DropdownMenu(open, onDismissRequest = { open = false }, containerColor = LartColors.Elevated) {
            DropdownMenuItem(text = { Text("All  ·  ${all.size}") }, onClick = { onPick(null); open = false })
            byAddon.forEach { (id, items) ->
                DropdownMenuItem(text = { Text("${items.first().addon.manifest.name}  ·  ${items.size}") }, onClick = { onPick(id); open = false })
            }
        }
    }
}

@Composable
private fun FacetChip(facet: Facet, picked: Set<String>, onChange: (Set<String>) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.clip(RoundedCornerShape(12.dp)).background(if (picked.isEmpty()) LartColors.Surface else LartColors.Raised)
                .clickable { open = true }.padding(start = 14.dp, end = 10.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                if (picked.isEmpty()) facet.label else "${facet.label}: ${picked.joinToString(", ")}",
                color = if (picked.isEmpty()) LartColors.InkMuted else LartColors.Ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Icon(Icons.Rounded.KeyboardArrowDown, null, tint = LartColors.InkSubtle, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(open, onDismissRequest = { open = false }, containerColor = LartColors.Elevated) {
            facet.values.forEach { value ->
                val on = value in picked
                DropdownMenuItem(
                    text = { Text(value) },
                    trailingIcon = { if (on) Icon(Icons.Rounded.Check, null, tint = LartColors.Accent) },
                    onClick = { onChange(if (on) picked - value else picked + value) },
                )
            }
            if (picked.isNotEmpty()) DropdownMenuItem(text = { Text("Clear", color = LartColors.InkMuted) }, onClick = { onChange(emptySet()); open = false })
        }
    }
}

/** stremio-row.tsx: logo, the addon's own text, badges, copy link and the round Play button. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourceCard(item: RankedStream, wide: Boolean, last: Boolean, showFilename: Boolean, playable: Boolean, onPlay: () -> Unit) {
    val stream = item.stream
    val clipboard = LocalClipboardManager.current
    var expanded by remember { mutableStateOf(false) }
    val body = listOfNotNull(stream.title, stream.description).joinToString("\n").trim()
    val actions: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            stream.url?.takeIf { stream.direct }?.let { url ->
                Box(
                    Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).clickable { clipboard.setText(AnnotatedString(url)) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.ContentCopy, "Copy link", tint = LartColors.InkSubtle, modifier = Modifier.size(17.dp)) }
            }
            Box(
                Modifier.size(if (wide) 72.dp else 60.dp).clip(CircleShape).background(if (playable) LartColors.Accent else LartColors.Raised)
                    .clickable(enabled = playable, onClick = onPlay),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.PlayArrow, "Play", tint = Color(0xFF121316), modifier = Modifier.size(if (wide) 36.dp else 30.dp)) }
        }
    }
    Column(
        Modifier.fillMaxWidth().alpha(if (playable) 1f else 0.5f).clip(RoundedCornerShape(26.dp))
            .background(LartColors.Surface.copy(alpha = 0.85f))
            .then(if (last) Modifier.border(1.dp, LartColors.Accent.copy(alpha = 0.6f), RoundedCornerShape(26.dp)) else Modifier)
            .clickable(enabled = playable, onClick = onPlay).padding(if (wide) 20.dp else 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(if (wide) 20.dp else 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(if (wide) 68.dp else 52.dp), contentAlignment = Alignment.Center) {
                item.addon.manifest.logo?.let { AsyncImage(it, null, contentScale = ContentScale.Fit, modifier = Modifier.size(if (wide) 64.dp else 48.dp).clip(RoundedCornerShape(12.dp))) }
                    ?: Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(LartColors.Elevated), contentAlignment = Alignment.Center) {
                        Text(item.addon.manifest.name.take(1), color = LartColors.InkMuted, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                if (last) {
                    Text("LAST PLAYED", color = LartColors.Accent, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                }
                Text((stream.name ?: item.addon.manifest.name).trim(), color = LartColors.Ink, fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold)
                if (body.isNotEmpty()) {
                    Text(
                        body,
                        color = LartColors.InkMuted,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        maxLines = if (expanded) Int.MAX_VALUE else 5,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (body.count { it == '\n' } >= 5 || body.length > 260) {
                        Row(Modifier.clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
                            Text(if (expanded) "Show less" else "Show more", color = LartColors.InkSubtle, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                if (showFilename) stream.behaviorHints?.filename?.let { Text(it, fontSize = 12.sp, color = LartColors.InkSubtle, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                Badges(stream)
                if (!playable) {
                    Text(
                        if (stream.infoHash != null) "Torrent: add a debrid account in Settings to play it" else "Not playable in Lart yet",
                        color = Color(0xFFE57373),
                        fontSize = 13.sp,
                    )
                }
            }
            if (wide) actions()
        }
        if (!wide) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { actions() }
    }
}

/** Harbor's badge strip: the resolution large, then source, codec and audio. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Badges(stream: Stream) {
    val values = facetValues(stream)
    val resolution = values[Facet.Resolution].orEmpty().firstOrNull { it.isNotEmpty() }
    val rest = listOfNotNull(
        values[Facet.Source].orEmpty().firstOrNull(),
        values[Facet.Codec].orEmpty().firstOrNull()?.let { if (it == "HEVC") "H.265" else it },
        values[Facet.Hdr].orEmpty().firstOrNull { it != "SDR" },
        values[Facet.Audio].orEmpty().firstOrNull { it == "Atmos" || it == "DD+" || it == "TrueHD" || it == "DTS" }?.let { if (it == "DD+") "Dolby Digital+" else if (it == "Atmos") "Dolby Atmos" else it },
        Ranking.badges(stream).firstOrNull { it.endsWith("GB") },
    )
    if (resolution == null && rest.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 2.dp)) {
        resolution?.let {
            Text(it, color = Color(0xE6FFFFFF), fontSize = 22.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, modifier = Modifier.align(Alignment.CenterVertically))
        }
        rest.forEach {
            Text(it, color = Color(0xCCFFFFFF), fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.align(Alignment.CenterVertically).widthIn(max = 160.dp))
        }
    }
}
