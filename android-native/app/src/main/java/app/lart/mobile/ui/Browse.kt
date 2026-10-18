package app.lart.mobile.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lart.mobile.data.CwItem
import app.lart.mobile.data.Meta
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

/** The browse views Harbor's phone app switches between from the top-right pill. */
enum class BrowseView(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Rounded.Home),
    Movies("Movies", Icons.Rounded.Movie),
    Shows("Shows", Icons.Rounded.Tv),
    Anime("Anime", Icons.Rounded.AutoAwesome),
}

@Composable
fun ViewSwitcher(current: BrowseView, onSelect: (BrowseView) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.height(44.dp).clip(RoundedCornerShape(10.dp)).background(LartColors.Elevated.copy(alpha = 0.9f))
                .border(1.dp, LartColors.EdgeSoft, RoundedCornerShape(10.dp))
                .clickable { open = true }.padding(start = 10.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(current.icon, contentDescription = null, tint = LartColors.Ink, modifier = Modifier.size(20.dp))
            Text(current.label, color = LartColors.Ink, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(18.dp),
            containerColor = LartColors.Elevated,
        ) {
            BrowseView.entries.forEach { view ->
                val on = view == current
                DropdownMenuItem(
                    text = {
                        Text(view.label, color = if (on) LartColors.Ink else LartColors.InkMuted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    },
                    leadingIcon = { Icon(view.icon, null, tint = if (on) LartColors.Accent else LartColors.InkMuted) },
                    onClick = {
                        open = false
                        onSelect(view)
                    },
                    modifier = Modifier.width(186.dp),
                )
            }
        }
    }
}

/** Harbor's mobile hero: a rounded 16:13 card that dissolves between titles every 8 seconds. */
@Composable
fun HeroCarousel(
    slides: List<Meta>,
    badge: (index: Int, meta: Meta) -> String,
    inMyList: (Meta) -> Boolean,
    onPlay: (Meta) -> Unit,
    onToggleList: (Meta) -> Unit,
    onOpen: (Meta) -> Unit,
) {
    if (slides.isEmpty()) return
    var index by remember(slides) { mutableIntStateOf(0) }
    var pausedUntil by remember { mutableLongStateOf(0L) }
    var listVersion by remember { mutableIntStateOf(0) }
    LaunchedEffect(slides) {
        while (slides.size > 1) {
            delay(8_000)
            if (System.currentTimeMillis() >= pausedUntil) index = (index + 1) % slides.size
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().aspectRatio(16f / 13f).heightIn(max = 560.dp)
                .clip(RoundedCornerShape(24.dp)).background(LartColors.Surface)
                .border(1.dp, LartColors.EdgeSoft, RoundedCornerShape(24.dp)),
        ) {
            Crossfade(targetState = index, animationSpec = tween(900), label = "hero") { i ->
                val meta = slides[i.coerceIn(0, slides.lastIndex)]
                Box(Modifier.fillMaxSize().clickable { onOpen(meta) }) {
                    AsyncImage(meta.background ?: meta.poster, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(0f to Color(0x0D000000), 0.5f to Color(0x73000000), 1f to Color(0xF2000000)),
                        ),
                    )
                    Column(
                        Modifier.align(Alignment.BottomStart).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            Modifier.background(Color(0x73000000), RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(Icons.Rounded.TrendingUp, null, tint = LartColors.Accent, modifier = Modifier.size(13.dp))
                            Text(badge(i, meta), color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                        TitleArt(meta, maxHeight = 62)
                        MetaLine(meta)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Row(
                                Modifier.height(52.dp).clip(RoundedCornerShape(50)).background(Color.White)
                                    .clickable { onPlay(meta) }.padding(horizontal = 30.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(Icons.Rounded.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(24.dp))
                                Text("Play", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            }
                            val listed = remember(meta.id, listVersion) { inMyList(meta) }
                            GlassCircle(if (listed) Icons.Rounded.Check else Icons.Rounded.Add, if (listed) "In My List" else "Add to My List", if (listed) LartColors.Accent else Color.White) {
                                onToggleList(meta)
                                listVersion++
                            }
                            GlassCircle(Icons.Rounded.Info, "More info", Color.White) { onOpen(meta) }
                        }
                    }
                }
            }
        }
        if (slides.size > 1) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                slides.indices.forEach { i ->
                    Box(
                        Modifier.padding(horizontal = 3.dp).height(6.dp).animateContentSize()
                            .width(if (i == index) 20.dp else 6.dp).clip(RoundedCornerShape(50))
                            .background(if (i == index) LartColors.Accent else LartColors.Ink.copy(alpha = 0.2f))
                            .clickable {
                                pausedUntil = System.currentTimeMillis() + 12_000
                                index = i
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassCircle(icon: ImageVector, description: String, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(52.dp).clip(CircleShape).background(Color(0x59000000)).border(1.dp, Color(0x40FFFFFF), CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(22.dp)) }
}

/** The title's logo when it has one, else its name in large type. */
@Composable
fun TitleArt(meta: Meta, maxHeight: Int) {
    var failed by remember(meta.id) { mutableStateOf(false) }
    val logo = meta.logoUrl
    if (logo != null && !failed) {
        AsyncImage(
            logo,
            contentDescription = meta.name,
            contentScale = ContentScale.Fit,
            alignment = Alignment.CenterStart,
            onError = { failed = true },
            modifier = Modifier.heightIn(max = maxHeight.dp).fillMaxWidth(0.74f),
        )
    } else {
        Text(meta.name, color = Color.White, fontSize = 30.sp, lineHeight = 31.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun MetaLine(meta: Meta) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        meta.year?.let { Text(it, color = Color(0xCCFFFFFF), fontSize = 13.sp, fontWeight = FontWeight.Medium) }
        meta.imdbRating?.takeIf { it.isNotBlank() }?.let { ImdbBadge(it) }
        meta.genres?.firstOrNull()?.let { Text(it, color = Color(0xB3FFFFFF), fontSize = 13.sp) }
    }
}

@Composable
fun ImdbBadge(rating: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.background(Color(0xFFF5C518), RoundedCornerShape(3.dp)).padding(horizontal = 4.dp, vertical = 1.dp)) {
            Text("IMDb", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Black)
        }
        Text(rating, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RailHeader(title: String, onSeeAll: (() -> Unit)?) {
    Row(
        Modifier.padding(horizontal = 16.dp).let { if (onSeeAll != null) it.clickable(onClick = onSeeAll) else it },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = LartColors.Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        if (onSeeAll != null) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "See all", tint = LartColors.InkSubtle)
    }
}

@Composable
fun Rail(title: String, metas: List<Meta>, onOpen: (Meta) -> Unit, onSeeAll: (() -> Unit)? = null) {
    if (metas.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RailHeader(title, onSeeAll)
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(metas, key = { it.type + it.id }) { meta -> PosterTile(meta, Modifier.width(124.dp)) { onOpen(meta) } }
        }
    }
}

@Composable
fun PosterTile(meta: Meta, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)).background(LartColors.Elevated)) {
            if (meta.poster != null) {
                AsyncImage(meta.poster, meta.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text(meta.name, color = LartColors.InkMuted, fontSize = 12.sp, modifier = Modifier.align(Alignment.Center).padding(8.dp))
            }
            meta.imdbRating?.takeIf { it.isNotBlank() }?.let { rating ->
                Row(
                    Modifier.align(Alignment.BottomEnd).padding(6.dp).background(Color(0xB3000000), RoundedCornerShape(6.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Icon(Icons.Rounded.Star, null, tint = Color(0xFFF5C518), modifier = Modifier.size(10.dp))
                    Text(rating, color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Text(
            meta.name,
            color = LartColors.InkMuted,
            fontSize = 12.5.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** "Top 10 Today": big outlined numbers behind the posters. */
@Composable
fun RankRail(title: String, metas: List<Meta>, onOpen: (Meta) -> Unit) {
    if (metas.size < 6) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RailHeader(title, null)
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(metas.take(10), key = { _, m -> m.type + m.id }) { i, meta ->
                Box(Modifier.width(150.dp).height(176.dp).clickable { onOpen(meta) }) {
                    Text(
                        "${i + 1}",
                        style = TextStyle(
                            fontSize = if (i + 1 >= 10) 118.sp else 158.sp,
                            fontWeight = FontWeight.Medium,
                            color = LartColors.InkMuted,
                            drawStyle = Stroke(width = 5f),
                            letterSpacing = (-6).sp,
                        ),
                        modifier = Modifier.offset(x = (-6).dp, y = (-18).dp),
                    )
                    Box(
                        Modifier.align(Alignment.TopEnd).width(93.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp))
                            .background(LartColors.Elevated),
                    ) {
                        meta.poster?.let { AsyncImage(it, meta.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                    }
                    Text(
                        meta.name,
                        color = LartColors.InkMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.BottomStart).padding(start = 57.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun ContinueWatchingRow(items: List<CwItem>, onOpen: (CwItem) -> Unit, onDismiss: (CwItem) -> Unit) {
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RailHeader("Continue watching", null)
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items, key = { it.meta.id }) { item -> CwCard(item, { onOpen(item) }, { onDismiss(item) }) }
        }
    }
}

@Composable
private fun CwCard(item: CwItem, onOpen: () -> Unit, onDismiss: () -> Unit) {
    val progress = if (item.durationMs > 0) (item.positionMs.toFloat() / item.durationMs).coerceIn(0f, 1f) else 0f
    val episode = if (item.season != null && item.episode != null) {
        if (item.meta.id.startsWith("kitsu:")) "Ep ${item.episode}" else "S${item.season} · E${item.episode}"
    } else null
    val remaining = if (item.durationMs > 0) remainingLabel(item.durationMs - item.positionMs) else null
    Column(Modifier.width(260.dp)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)).background(LartColors.Surface)
                .border(1.dp, LartColors.EdgeSoft, RoundedCornerShape(16.dp)).clickable(onClick = onOpen),
        ) {
            AsyncImage(item.meta.background ?: item.meta.poster, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x26000000), 0.5f to Color(0x0D000000), 1f to Color(0xBF000000))))
            item.meta.logoUrl?.let { logo ->
                var failed by remember(logo) { mutableStateOf(false) }
                if (!failed) {
                    AsyncImage(
                        logo,
                        null,
                        contentScale = ContentScale.Fit,
                        onError = { failed = true },
                        modifier = Modifier.align(Alignment.Center).padding(start = 20.dp, end = 20.dp, bottom = 28.dp).fillMaxWidth(0.76f).fillMaxHeight(0.5f),
                    )
                }
            }
            Row(
                Modifier.align(Alignment.BottomStart).padding(10.dp).background(Color(0x8C000000), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(12.dp))
                Text(
                    listOfNotNull(episode, remaining).joinToString("  ·  ").ifEmpty { "Resume" },
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp).background(Color(0x40FFFFFF))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(LartColors.Accent))
            }
            Box(
                Modifier.align(Alignment.TopEnd).padding(6.dp).size(36.dp).clip(CircleShape).background(Color(0x8C000000)).clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Close, "Remove from Continue watching", tint = Color(0xE6FFFFFF), modifier = Modifier.size(17.dp)) }
        }
        Text(
            item.meta.name,
            color = LartColors.InkMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp).widthIn(max = 260.dp),
        )
    }
}

private fun remainingLabel(ms: Long): String {
    val minutes = (ms / 60_000).coerceAtLeast(0)
    if (minutes < 60) return "${minutes}m left"
    val hours = minutes / 60
    val rest = minutes % 60
    return if (rest == 0L) "${hours}h left" else "${hours}h ${rest}m left"
}
