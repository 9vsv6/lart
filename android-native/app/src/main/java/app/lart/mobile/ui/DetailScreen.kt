package app.lart.mobile.ui

import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lart.mobile.data.AddonClient
import app.lart.mobile.data.AnimeInfo
import app.lart.mobile.data.AnimeExtra
import app.lart.mobile.data.AwardEntry
import app.lart.mobile.data.AwardType
import app.lart.mobile.data.Awards
import app.lart.mobile.data.CastMember
import app.lart.mobile.data.Meta
import app.lart.mobile.data.Omdb
import app.lart.mobile.data.Fanart
import app.lart.mobile.data.Keyless
import app.lart.mobile.data.Mdblist
import app.lart.mobile.data.ProviderKey
import app.lart.mobile.data.Tvdb
import app.lart.mobile.data.Repository
import app.lart.mobile.data.Score
import app.lart.mobile.data.Tmdb
import app.lart.mobile.data.TmdbDetails
import app.lart.mobile.data.Video
import coil.compose.AsyncImage
import java.util.Locale

/*
 * The title page, as Harbor's (src/views/detail): a full-bleed backdrop with the logo, a
 * facts line with the IMDb badge, Play and Watchlist; then the synopsis, where to watch,
 * the crew, episodes, the cast as portrait cards, More Like This and You Might Also Like,
 * the media gallery, and Information. TMDB fills most of it when a key is set; without
 * one, Cinemeta's cast, director and writers are shown.
 */

@Composable
fun DetailScreen(
    repository: Repository,
    type: String,
    id: String,
    onBack: () -> Unit,
    onStreams: (videoId: String, title: String) -> Unit,
    onOpen: (Meta) -> Unit = {},
) {
    val addons by repository.addons.collectAsState()
    val builtins by repository.builtins.collectAsState()
    var meta by remember { mutableStateOf<Meta?>(null) }
    var failed by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf<TmdbDetails?>(null) }
    var awards by remember { mutableStateOf<List<AwardEntry>>(emptyList()) }
    var scores by remember { mutableStateOf<List<Score>>(emptyList()) }
    val omdbKey by repository.omdbKey.collectAsState()
    val providerKeys by repository.providerKeys.collectAsState()
    var mdblist by remember { mutableStateOf<Mdblist.Scores?>(null) }
    var network by remember { mutableStateOf<String?>(null) }
    var art by remember { mutableStateOf<Fanart.Art?>(null) }
    val tmdbKey by repository.tmdbKey.collectAsState()
    var anime by remember { mutableStateOf<AnimeExtra?>(null) }
    var relatedKitsu by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }

    // Harbor's anime extras from AniList: characters, related anime, other titles, stats, next episode.
    LaunchedEffect(meta?.id) {
        val current = meta ?: return@LaunchedEffect
        if (current.id.substringBefore(':') !in setOf("kitsu", "mal", "anilist")) return@LaunchedEffect
        val ids = AnimeInfo.ids(current.id) ?: return@LaunchedEffect
        // The IMDb id behind the anime gives it Wikidata's awards too.
        ids.imdb?.takeIf { awards.isEmpty() }?.let { imdb -> awards = runCatching { Awards.forTitle(imdb) }.getOrDefault(emptyList()) }
        val extra = ids.anilist?.let { AnimeInfo.extra(it) } ?: return@LaunchedEffect
        anime = extra
        relatedKitsu = AnimeInfo.kitsuFor(extra.related.map { it.anilistId })
    }

    // Awards from Wikidata, as Harbor's hero badge and Awards list; no key needed.
    LaunchedEffect(meta?.id) {
        val imdb = meta?.id?.substringBefore(':')?.takeIf { it.startsWith("tt") } ?: return@LaunchedEffect
        awards = runCatching { Awards.forTitle(imdb) }.getOrDefault(emptyList())
    }

    // With an OMDb key: the real IMDb, Rotten Tomatoes and Metacritic scores, as Harbor's hero.
    LaunchedEffect(meta?.id, omdbKey) {
        val key = omdbKey ?: return@LaunchedEffect
        val imdb = meta?.id?.substringBefore(':')?.takeIf { it.startsWith("tt") } ?: return@LaunchedEffect
        scores = runCatching { Omdb.scores(key, imdb) }.getOrDefault(emptyList())
    }
    // Harbor's other providers, each with the user's own key.
    LaunchedEffect(meta?.id, details?.tvdbId, providerKeys) {
        val current = meta ?: return@LaunchedEffect
        val imdb = current.id.substringBefore(':').takeIf { it.startsWith("tt") } ?: return@LaunchedEffect
        val series = current.type != "movie"
        providerKeys[ProviderKey.MDBLIST]?.let { key -> mdblist = runCatching { Mdblist.scores(key, imdb, current.type) }.getOrNull() }
        val tvdbKey = providerKeys[ProviderKey.TVDB]
        val tvdbId = if (series && tvdbKey != null) runCatching { Tvdb.seriesId(tvdbKey, imdb) }.getOrNull() else null
        // TheTVDB: the network, and episode titles, descriptions and stills in your language.
        if (tvdbKey != null && tvdbId != null) {
            network = runCatching { Tvdb.network(tvdbKey, tvdbId) }.getOrNull()
            val lang = LartDisplay.settings.metadataLanguage.ifEmpty { java.util.Locale.getDefault().language }
            val episodes = runCatching { Tvdb.episodes(tvdbKey, tvdbId, Tvdb.language(lang)) }.getOrDefault(emptyList())
                .associateBy { it.season to it.number }
            if (episodes.isNotEmpty()) {
                meta = current.copy(videos = current.videos?.map { v ->
                    val e = episodes[(v.season ?: -1) to (v.episode ?: -1)] ?: return@map v
                    v.copy(
                        title = e.name?.takeIf { it.isNotBlank() } ?: v.title,
                        overview = e.overview?.takeIf { it.isNotBlank() } ?: v.overview,
                        thumbnail = v.thumbnail ?: e.image,
                    )
                })
            }
        }
        // Fanart.tv: a logo and backdrop where TMDB has none.
        providerKeys[ProviderKey.FANART]?.let { key ->
            art = runCatching {
                if (series) (details?.tvdbId ?: tvdbId)?.let { Fanart.tv(key, it) } else Fanart.movie(key, imdb)
            }.getOrNull()
        }
    }
    val listState = rememberLazyListState()
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }

    LaunchedEffect(addons, builtins, type, id) {
        if (addons.isEmpty() || meta != null) return@LaunchedEffect
        val loaded = AddonClient.meta(repository.metaAddons(), type, id)
        meta = loaded
        // Built-ins load in the background; only give up once they have had their turn.
        failed = loaded == null && builtins.isNotEmpty()
    }

    // TMDB with a key; without one (or when TMDB misses), Harbor's keyless sources.
    LaunchedEffect(meta?.id, tmdbKey) {
        val current = meta ?: return@LaunchedEffect
        val imdb = current.id.substringBefore(':').takeIf { it.startsWith("tt") } ?: return@LaunchedEffect
        val key = tmdbKey
        details = (if (key != null) runCatching { Tmdb.details(key, imdb, current.type) }.getOrNull() else null)
            ?: runCatching { Keyless.details(current) }.getOrNull()
    }

    Box(Modifier.fillMaxSize().background(LartColors.Canvas)) {
        val current = meta
        when {
            current != null -> DetailBody(
                repository,
                current.copy(logo = current.logo ?: art?.logo, background = current.background ?: art?.backdrop),
                details?.let { d -> d.copy(logo = d.logo ?: art?.logo, backdrop = d.backdrop ?: art?.backdrop, networks = d.networks.ifEmpty { listOfNotNull(network) }) },
                awards,
                scores + mdblistScores(mdblist),
                listState,
                onStreams,
                onOpen,
                network,
                anime,
                relatedKitsu,
            )
            failed -> EmptyState("Couldn't load this title", "None of your addons has details for it.")
            else -> Loading(Modifier.padding(top = 160.dp))
        }
        // Past the hero, a solid bar keeps the page out from under the status bar and Back.
        if (scrolled) Box(Modifier.fillMaxWidth().background(LartColors.Canvas.copy(alpha = 0.97f)).statusBarsPadding().height(60.dp))
        // Harbor's "← Back".
        Row(
            Modifier.statusBarsPadding().padding(start = 14.dp, top = 8.dp).clip(RoundedCornerShape(50)).background(Color(0x66000000))
                .clickable(onClick = onBack).padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(18.dp))
            Text("Back", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailBody(
    repository: Repository,
    meta: Meta,
    details: TmdbDetails?,
    awards: List<AwardEntry>,
    scores: List<Score>,
    listState: LazyListState,
    onStreams: (String, String) -> Unit,
    onOpen: (Meta) -> Unit,
    network: String? = null,
    anime: AnimeExtra? = null,
    relatedKitsu: Map<Int, Int> = emptyMap(),
) {
    val videos = meta.videos.orEmpty()
    val isSeries = meta.type != "movie" && videos.isNotEmpty()
    // Season 0 holds specials; start on the first real season.
    val seasons = remember(videos) { videos.mapNotNull { it.season }.distinct().sortedWith(compareBy({ it == 0 }, { it })) }
    var season by rememberSaveable(meta.id) { mutableStateOf(seasons.firstOrNull()) }
    val episodes = remember(videos, season, LartDisplay.settings.episodesNewestFirst) {
        videos.filter { it.season == season }.sortedBy { it.episode ?: 0 }
            .let { if (LartDisplay.settings.episodesNewestFirst) it.reversed() else it }
    }
    val next = remember(meta.id, videos) { if (isSeries) nextEpisode(repository, meta, videos) else null }
    // Harbor's episode ratings: OMDb's real IMDb scores with a key, else TMDB's; one call per season.
    val omdbKey by repository.omdbKey.collectAsState()
    val tmdbKey by repository.tmdbKey.collectAsState()
    val ratings = remember(meta.id) { androidx.compose.runtime.mutableStateMapOf<Int, Map<Int, String>>() }
    LaunchedEffect(meta.id, season, omdbKey, tmdbKey, details?.tmdbId) {
        val s = season ?: return@LaunchedEffect
        if (!isSeries || !LartDisplay.settings.showEpisodeRating || ratings.containsKey(s)) return@LaunchedEffect
        val imdb = meta.id.substringBefore(':').takeIf { it.startsWith("tt") }
        val fromOmdb = if (omdbKey != null && imdb != null) runCatching { Omdb.seasonRatings(omdbKey!!, imdb, s) }.getOrDefault(emptyMap()) else emptyMap()
        val found = fromOmdb.ifEmpty {
            val key = tmdbKey
            val tv = details?.tmdbId
            if (key != null && tv != null) runCatching { Tmdb.seasonRatings(key, tv, s) }.getOrDefault(emptyMap()) else emptyMap()
        }
        if (found.isNotEmpty()) ratings[s] = found
    }
    val play: () -> Unit = {
        if (isSeries) next?.let { onStreams(it.id, "${meta.name} · ${episodeLabel(it)}") } else onStreams(meta.id, meta.name)
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 720.dp
        val side = if (wide) 48.dp else 20.dp
        val heroHeight = if (wide) (maxHeight * 0.78f).coerceAtMost(680.dp) else (maxHeight * 0.68f).coerceIn(420.dp, 620.dp)
        LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(30.dp)) {
            item(key = "hero") { Hero(repository, meta, details, awards, scores, heroHeight, side, wide, isSeries, next, play) }
            (details?.overview ?: meta.description)?.let { synopsis ->
                item(key = "synopsis") { Synopsis(synopsis, details?.tagline, side) }
            }
            anime?.nextAiring?.let { (at, ep) -> item(key = "airing") { AnimeAiringBanner(at, ep, side) } }
            details?.watchOn?.takeIf { it.isNotEmpty() }?.let { providers ->
                item(key = "watch") {
                    Column(Modifier.padding(horizontal = side), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Eyebrow("Watch on")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            providers.take(8).forEach { provider ->
                                Row(
                                    Modifier.clip(RoundedCornerShape(50)).background(LartColors.Surface).padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Box(Modifier.size(34.dp).clip(CircleShape).background(LartColors.Elevated)) {
                                        provider.logo?.let { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                                    }
                                    Text(provider.name, color = LartColors.Ink, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
            val crew = crewOf(meta, details)
            if (crew.isNotEmpty()) item(key = "crew") { CrewGrid(crew, if (wide) 3 else 2, side) }
            if (isSeries && seasons.isNotEmpty()) {
                item(key = "seasons") {
                    // Harbor's episode list: the title and a season picker, the season's size, then the rows.
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.padding(horizontal = side), verticalAlignment = Alignment.CenterVertically) {
                            Text("Episodes", color = LartColors.Ink, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp, modifier = Modifier.weight(1f))
                            SeasonPicker(seasons, season, videos) { season = it }
                        }
                        val year = episodes.mapNotNull { it.released?.take(4) }.minOrNull()
                        Text(
                            listOfNotNull("${episodes.size} episodes", year).joinToString("  ·  "),
                            color = LartColors.InkSubtle,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = side),
                        )
                        val upTo = next?.let { (it.season ?: 0) * 10_000 + (it.episode ?: 0) } ?: 0
                        Column(Modifier.padding(top = 8.dp)) {
                            episodes.forEach { video ->
                                val spoiler = LartDisplay.settings.hideSpoilers && (video.season ?: 0) > 0 && (video.season ?: 0) * 10_000 + (video.episode ?: 0) > upTo
                                EpisodeRow(video, spoiler, side, wide, ratings[video.season ?: -1]?.get(video.episode ?: -1) ?: video.rating?.takeIf { it.isNotBlank() && it != "0" }) { onStreams(video.id, "${meta.name} · ${episodeLabel(video)}") }
                            }
                        }
                    }
                }
            }
            val cast = details?.cast?.takeIf { it.isNotEmpty() } ?: meta.castNames.map { CastMember(it, null, null) }
            if (cast.isNotEmpty()) item(key = "cast") { CastRail(cast, details?.castCount?.takeIf { it > 0 } ?: cast.size, side, wide) }
            anime?.characters?.takeIf { it.isNotEmpty() }?.let { item(key = "characters") { AnimeCharactersRail(it, side, wide) } }
            details?.recommendations?.takeIf { it.isNotEmpty() }?.let { item(key = "more") { Rail("More Like This", it, onOpen) } }
            details?.similar?.takeIf { it.isNotEmpty() }?.let { item(key = "also") { Rail("You Might Also Like", it, onOpen) } }
            anime?.related?.takeIf { it.isNotEmpty() }?.let { nodes ->
                item(key = "related") {
                    AnimeRelatedRail(nodes, relatedKitsu, side) { node, kitsu ->
                        onOpen(Meta(id = "kitsu:$kitsu", type = if (node.format == "Movie") "movie" else "series", name = node.title, poster = node.poster))
                    }
                }
            }
            if (details != null && (details.backdrops.isNotEmpty() || details.posters.isNotEmpty())) {
                item(key = "media") { MediaGallery(details, side, wide) }
            }
            if (awards.isNotEmpty()) item(key = "awards") { AwardsSection(awards, side) }
            anime?.let { animeTitles(it, meta.name) }?.takeIf { it.isNotEmpty() }?.let { titles ->
                item(key = "titles") { Titled("Titles", titles, if (wide) 3 else 2, side) }
            }
            val info = infoOf(meta, details) + animeInfo(anime, details)
            if (info.isNotEmpty()) item(key = "info") { Information(info, if (wide) 3 else 2, side) }
            anime?.statusDistribution?.takeIf { it.isNotEmpty() }?.let { item(key = "stats") { AnimeStats(it, side) } }
        }
    }
}

// ---------------- Hero ----------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Hero(
    repository: Repository,
    meta: Meta,
    details: TmdbDetails?,
    awards: List<AwardEntry>,
    scores: List<Score>,
    height: Dp,
    side: Dp,
    wide: Boolean,
    isSeries: Boolean,
    next: Video?,
    play: () -> Unit,
) {
    val uri = LocalUriHandler.current
    Box(Modifier.fillMaxWidth().height(height)) {
        AsyncImage(
            model = details?.backdrop ?: meta.background ?: meta.poster,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // Harbor's scrims: down into the page, and from the left behind the text.
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0f to Color(0x33000000), 0.45f to Color.Transparent, 1f to LartColors.Canvas)))
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(0f to Color(0xCC121316), if (wide) 0.55f to Color.Transparent else 0.9f to Color(0x33121316))))
        Column(
            Modifier.align(Alignment.BottomStart).padding(start = side, end = side, bottom = 8.dp).widthIn(max = 760.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            val logo = details?.logo ?: meta.logoUrl
            if (logo != null) {
                var failed by remember(logo) { mutableStateOf(false) }
                if (!failed) {
                    AsyncImage(
                        logo,
                        meta.name,
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.CenterStart,
                        onError = { failed = true },
                        modifier = Modifier.heightIn(max = if (wide) 120.dp else 92.dp).widthIn(max = if (wide) 520.dp else 300.dp),
                    )
                } else HeroTitle(meta.name, wide)
            } else HeroTitle(details?.title ?: meta.name, wide)

            FlowRow(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                meta.year?.let { Fact(it) }
                (scores.firstOrNull { it.source == "IMDb" }?.value ?: meta.imdbRating)?.takeIf { it.isNotBlank() }?.let { rating ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.align(Alignment.CenterVertically)) {
                        Box(Modifier.clip(RoundedCornerShape(3.dp)).background(Color(0xFFF5C518)).padding(horizontal = 4.dp, vertical = 1.dp)) {
                            Text("IMDb", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Black)
                        }
                        Text(rating, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
                scores.filter { it.source != "IMDb" }.forEach { score -> Fact(
                    when (score.source) {
                        "Rotten Tomatoes" -> "🍅 ${score.value}"
                        "Audience" -> "🍿 ${score.value}"
                        else -> "${score.source} ${score.value}"
                    },
                ) }
                if (isSeries) meta.videos.orEmpty().mapNotNull { it.season }.filter { it > 0 }.distinct().size.takeIf { it > 0 }?.let { Fact(if (it == 1) "1 season" else "$it seasons") }
                else (details?.runtimeMinutes?.let { "$it min" } ?: meta.runtime)?.let { Fact(it) }
                (details?.genres?.takeIf { it.isNotEmpty() } ?: meta.genres.orEmpty()).take(3).forEach { Fact(it) }
            }

            if (!wide) HeroAwards(awards, Modifier)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(Color.White).clickable(onClick = play).padding(start = 22.dp, end = 26.dp, top = 14.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Rounded.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(24.dp))
                    Text(
                        when {
                            isSeries && next != null && repository.resumeFor(next.id, meta.id) > 0 -> "Resume S${next.season}:E${next.episode}"
                            isSeries && next != null -> "Play S${next.season}:E${next.episode}"
                            repository.resumeFor(meta.id, meta.id) > 0 -> "Resume"
                            else -> "Play"
                        },
                        color = Color.Black,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                WatchlistButton(repository, meta, compact = !wide)
                (meta.trailerYouTube ?: details?.trailerYouTube)?.let { yt ->
                    RoundAction(Icons.Outlined.Movie, "Trailer") { uri.openUri("https://www.youtube.com/watch?v=$yt") }
                }
                meta.id.substringBefore(':').takeIf { it.startsWith("tt") }?.let { imdb ->
                    RoundAction(Icons.Rounded.Language, "IMDb") { uri.openUri("https://www.imdb.com/title/$imdb/") }
                }
            }
        }
        if (wide) HeroAwards(awards, Modifier.align(Alignment.BottomEnd).padding(end = side, bottom = 120.dp))
    }
}

@Composable
private fun HeroTitle(name: String, wide: Boolean) {
    Text(
        name.uppercase(),
        color = Color.White,
        fontSize = if (wide) 64.sp else 40.sp,
        lineHeight = if (wide) 66.sp else 44.sp,
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        letterSpacing = if (wide) 10.sp else 5.sp,
    )
}

@Composable
private fun Fact(text: String) {
    Text(
        text,
        color = Color(0xD9FFFFFF),
        fontSize = 14.5.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0x1FFFFFFF)).padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

@Composable
private fun WatchlistButton(repository: Repository, meta: Meta, compact: Boolean) {
    val myList by repository.myList.collectAsState()
    val listed = myList.any { it.id == meta.id }
    if (compact) {
        RoundAction(if (listed) Icons.Rounded.Check else Icons.Rounded.Add, if (listed) "In Watchlist" else "Add to Watchlist", tint = if (listed) LartColors.Accent else Color.White) {
            repository.toggleMyList(meta)
        }
    } else {
        Row(
            Modifier.clip(RoundedCornerShape(50)).clickable { repository.toggleMyList(meta) }.padding(horizontal = 10.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(if (listed) Icons.Rounded.Check else Icons.Rounded.Add, null, tint = if (listed) LartColors.Accent else Color.White, modifier = Modifier.size(22.dp))
            Text(if (listed) "In Watchlist" else "Add to Watchlist", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun RoundAction(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, tint: Color = Color.White, onClick: () -> Unit) {
    Box(
        Modifier.size(52.dp).clip(CircleShape).background(Color(0x33FFFFFF)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = tint, modifier = Modifier.size(23.dp)) }
}

// ---------------- Sections ----------------

@Composable
private fun Eyebrow(text: String) {
    Text(text.uppercase(), color = LartColors.InkSubtle, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 3.sp)
}

@Composable
private fun Synopsis(text: String, tagline: String?, side: Dp) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = side).widthIn(max = 820.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tagline?.let { Text(it, color = LartColors.Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Serif) }
        Text(
            text,
            color = LartColors.InkMuted,
            fontSize = 16.sp,
            lineHeight = 25.sp,
            maxLines = if (expanded) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis,
        )
        if (text.length > 240) {
            Text(if (expanded) "Less" else "More", color = LartColors.Accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { expanded = !expanded })
        }
    }
}

private fun crewOf(meta: Meta, details: TmdbDetails?): List<Pair<String, String>> {
    fun row(label: String, names: List<String>) = names.takeIf { it.isNotEmpty() }?.let { label to it.take(4).joinToString(", ") }
    return if (details != null) listOfNotNull(
        row(if (details.directors.size > 1) "Directors" else if (meta.type == "movie") "Director" else "Created by", details.directors),
        row("Writers", details.writers),
        row("Producers", details.producers),
        row("Cinematography", details.cinematography),
        row("Music", details.music),
        row("Editor", details.editors),
    ) else listOfNotNull(
        row(if (meta.directors.size > 1) "Directors" else "Director", meta.directors),
        row("Writers", meta.writers),
    )
}

/** A label-over-value grid, as Harbor's crew block and Information. */
@Composable
private fun LabelGrid(cells: List<Pair<String, String>>, columns: Int, side: Dp) {
    Column(Modifier.padding(horizontal = side), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        cells.chunked(columns).forEach { rowCells ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                rowCells.forEach { (label, value) ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Eyebrow(label)
                        Text(value, color = LartColors.Ink, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
                    }
                }
                repeat(columns - rowCells.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun CrewGrid(cells: List<Pair<String, String>>, columns: Int, side: Dp) = LabelGrid(cells, columns, side)

@Composable
private fun CastRail(cast: List<CastMember>, total: Int, side: Dp, wide: Boolean) {
    val width = if (wide) 150.dp else 118.dp
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.padding(horizontal = side), verticalAlignment = Alignment.CenterVertically) {
            Text("Cast", color = LartColors.Ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text("  ·  ${cast.size}", color = LartColors.InkMuted, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        }
        LazyRow(contentPadding = PaddingValues(horizontal = side), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(cast) { person ->
                Column(Modifier.width(width), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(18.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFF2B2140), Color(0xFF3A2A12)))),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (person.photo != null) AsyncImage(person.photo, person.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        else Text(person.name.take(1), color = LartColors.InkMuted, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text(person.name, color = LartColors.Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                    person.character?.let { Text(it, color = LartColors.InkSubtle, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
}

private enum class MediaTab(val label: String) { Backdrops("Backdrops"), Posters("Posters"), Logos("Logos") }

@Composable
private fun MediaGallery(details: TmdbDetails, side: Dp, wide: Boolean) {
    var tab by rememberSaveable { mutableStateOf(MediaTab.Backdrops) }
    val images = when (tab) {
        MediaTab.Backdrops -> details.backdrops
        MediaTab.Posters -> details.posters
        MediaTab.Logos -> details.logos
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.padding(horizontal = side), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Media", color = LartColors.Ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(end = 8.dp))
            MediaTab.entries.forEach { entry ->
                val count = when (entry) {
                    MediaTab.Backdrops -> details.backdrops.size
                    MediaTab.Posters -> details.posters.size
                    MediaTab.Logos -> details.logos.size
                }
                if (count == 0) return@forEach
                val on = entry == tab
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(if (on) LartColors.Elevated else Color.Transparent).clickable { tab = entry }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(entry.label, color = if (on) LartColors.Ink else LartColors.InkMuted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("$count", color = LartColors.InkSubtle, fontSize = 12.sp)
                }
            }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = side), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(images) { url ->
                val (w, ratio) = when (tab) {
                    MediaTab.Backdrops -> (if (wide) 380.dp else 280.dp) to 16f / 9f
                    MediaTab.Posters -> (if (wide) 170.dp else 130.dp) to 2f / 3f
                    MediaTab.Logos -> (if (wide) 260.dp else 200.dp) to 16f / 9f
                }
                Box(Modifier.width(w).aspectRatio(ratio).clip(RoundedCornerShape(18.dp)).background(LartColors.Surface), contentAlignment = Alignment.Center) {
                    AsyncImage(
                        url,
                        null,
                        contentScale = if (tab == MediaTab.Logos) ContentScale.Fit else ContentScale.Crop,
                        modifier = if (tab == MediaTab.Logos) Modifier.fillMaxSize().padding(18.dp) else Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

/** Harbor's Information block (info-block.tsx), in its order. */
private fun infoOf(meta: Meta, details: TmdbDetails?): List<Pair<String, String>> = listOfNotNull(
    details?.status?.let { "Status" to it },
    details?.seasons?.takeIf { meta.type != "movie" }?.let { "Seasons" to listOfNotNull("$it", details.episodes?.let { e -> "$e episodes" }).joinToString("  ·  ") },
    details?.firstAired?.let { (if (meta.type == "movie") "Released" else "First aired") to it },
    details?.lastAired?.takeIf { meta.type != "movie" }?.let { "Last aired" to it },
    details?.schedule?.takeIf { details.lastAired == null }?.let { "Airs" to it },
    details?.networks?.takeIf { it.isNotEmpty() }?.let { "Networks" to it.joinToString("  ·  ") },
    details?.studios?.takeIf { it.isNotEmpty() }?.let { "Studio" to it.joinToString("  ·  ") },
    (details?.countries?.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: meta.countryName)?.let { "Country" to it },
    details?.originalLanguage?.let { "Original language" to it.replaceFirstChar { c -> c.titlecase(Locale.getDefault()) } },
    details?.originalTitle?.let { "Original title" to it },
    details?.budget?.let { "Budget" to money(it) },
    details?.revenue?.let { "Revenue" to money(it) },
    details?.voteAverage?.takeIf { details.voteCount > 0 }?.let { "Rating" to "%.1f  ·  %,d votes".format(Locale.getDefault(), it, details.voteCount) },
    meta.releaseInfo?.takeIf { details == null }?.let { "Released" to it },
    meta.awards?.takeIf { it.isNotBlank() }?.let { "Awards" to it },
    details?.officialSite?.takeIf { it.isNotBlank() }?.let { "Official site" to it.removePrefix("https://").removePrefix("http://").removeSuffix("/") },
)

private fun money(amount: Long): String = java.text.NumberFormat.getCurrencyInstance(Locale.US).apply { maximumFractionDigits = 0 }.format(amount)

/** AniList's facts for anime, where TMDB gave none. */
private fun animeInfo(anime: AnimeExtra?, details: TmdbDetails?): List<Pair<String, String>> {
    anime ?: return emptyList()
    return listOfNotNull(
        anime.studios.takeIf { it.isNotEmpty() && details?.studios.isNullOrEmpty() }?.let { "Studio" to it.take(3).joinToString(", ") },
        anime.source?.let { "Source" to it },
        anime.tags.takeIf { it.isNotEmpty() }?.let { "Tags" to it.take(6).joinToString(", ") },
        anime.favourites?.let { "Favourites" to "%,d on AniList".format(Locale.getDefault(), it) },
    )
}

@Composable
private fun Information(cells: List<Pair<String, String>>, columns: Int, side: Dp) = Titled("Information", cells, columns, side)

@Composable
private fun Titled(title: String, cells: List<Pair<String, String>>, columns: Int, side: Dp) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(title,color = LartColors.Ink, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = side))
        LabelGrid(cells, columns, side)
    }
}

/** Harbor's season picker: "Season 1 ▾", opening "Season N · X episodes · year" rows. */
@Composable
private fun SeasonPicker(seasons: List<Int>, selected: Int?, videos: List<Video>, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    fun name(s: Int?) = if (s == 0) "Specials" else "Season ${s ?: 1}"
    Box {
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(LartColors.Elevated).clickable { open = true }.padding(start = 18.dp, end = 12.dp, top = 11.dp, bottom = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(name(selected), color = LartColors.Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Icon(if (open) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, null, tint = LartColors.InkMuted, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(open, onDismissRequest = { open = false }, containerColor = LartColors.Elevated, shape = RoundedCornerShape(16.dp)) {
            seasons.forEach { s ->
                val inSeason = videos.filter { it.season == s }
                val year = inSeason.mapNotNull { it.released?.take(4) }.minOrNull()
                DropdownMenuItem(
                    text = {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            Text(name(s), color = LartColors.Ink, fontSize = 15.sp, fontWeight = if (s == selected) FontWeight.Bold else FontWeight.SemiBold)
                            Text(listOfNotNull("${inSeason.size} episodes", year).joinToString("  ·  "), color = LartColors.InkSubtle, fontSize = 12.5.sp)
                        }
                    },
                    onClick = {
                        onPick(s)
                        open = false
                    },
                    modifier = Modifier.widthIn(min = 230.dp).background(if (s == selected) LartColors.Raised else Color.Transparent),
                )
            }
        }
    }
}

/** Harbor's episode row: a numbered thumbnail, the title, "S1 E1 · date", and the description. */
@Composable
private fun EpisodeRow(video: Video, spoiler: Boolean, side: Dp, wide: Boolean, rating: String?, onClick: () -> Unit) {
    val display = LartDisplay.settings
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = side, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(if (wide) 30.dp else 14.dp),
        verticalAlignment = if (wide) Alignment.CenterVertically else Alignment.Top,
    ) {
        Box(
            Modifier.width((if (wide) 250.dp else 150.dp) * (1f + 0.3f * display.episodeCardSize)).aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)).background(LartColors.Elevated),
        ) {
            if (video.thumbnail != null) {
                AsyncImage(
                    video.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().then(if (spoiler && display.spoilerThumbnails) Modifier.blur(18.dp) else Modifier),
                )
            }
            // Harbor's "★ 8.5" at the bottom left of the still.
            rating?.takeIf { LartDisplay.settings.showEpisodeRating }?.let {
                Row(
                    Modifier.align(Alignment.BottomStart).padding(8.dp).clip(RoundedCornerShape(50)).background(Color(0xD9000000)).padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("★", color = Color(0xFFF5C518), fontSize = 11.sp)
                    Text(it, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            video.episode?.let { number ->
                Box(
                    Modifier.padding(8.dp).size(26.dp).clip(CircleShape).background(Color(0xCC000000)),
                    contentAlignment = Alignment.Center,
                ) { Text("$number", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                if (spoiler && display.spoilerTitles) "Episode ${video.episode ?: ""}" else video.label,
                fontSize = if (wide) 19.sp else 16.sp,
                color = LartColors.Ink,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val date = video.released?.take(10)?.let { formatDate(it) }
            val code = if (video.season != null && video.episode != null) "S${video.season} E${video.episode}" else null
            listOfNotNull(code, date).takeIf { it.isNotEmpty() }?.let {
                Text(it.joinToString("  ·  "), fontSize = 13.sp, color = LartColors.InkSubtle)
            }
            video.overview?.takeIf { display.showEpisodeDescription && it.isNotBlank() && !(spoiler && display.spoilerDescriptions) }?.let {
                Text(it, fontSize = if (wide) 15.sp else 13.5.sp, lineHeight = if (wide) 22.sp else 19.sp, color = LartColors.InkMuted, maxLines = if (wide) 2 else 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** "2008-01-20" as "20 Jan 2008", in the device's language. */
private fun formatDate(iso: String): String = runCatching {
    val date = java.time.LocalDate.parse(iso)
    date.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
}.getOrDefault(iso)

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

// ---------------- Awards (hero-awards.tsx and the Awards section) ----------------

private val AWARD_PRIORITY = listOf(
    AwardType.Oscar, AwardType.Emmy, AwardType.GoldenGlobe, AwardType.Bafta, AwardType.BaftaTv, AwardType.Cannes,
    AwardType.Venice, AwardType.Berlin, AwardType.Sag, AwardType.CriticsChoice, AwardType.Spirit, AwardType.Annie,
    AwardType.Cesar, AwardType.Goya, AwardType.BlueDragon, AwardType.Baeksang, AwardType.Bifa, AwardType.Saturn,
)

/** Harbor's hero laurel: "PRIMETIME EMMY WINNER", then "15 Emmys · 25 nominations" per big award. */
@Composable
private fun HeroAwards(awards: List<AwardEntry>, modifier: Modifier) {
    val byType = awards.groupBy { it.type }
    val ranked = AWARD_PRIORITY.filter { byType.containsKey(it) }
    if (ranked.isEmpty()) return
    val top = ranked.firstOrNull { t -> byType[t].orEmpty().any { it.won } } ?: ranked.first()
    val topWon = byType[top].orEmpty().any { it.won }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("🏆", fontSize = 30.sp)
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                "${top.title} ${if (topWon) "winner" else "nominee"}".uppercase(),
                color = Color(0xD9FFFFFF),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
            ranked.take(2).forEach { type ->
                val list = byType[type].orEmpty()
                val wins = list.count { it.won }
                val total = list.size
                val line = if (wins > 0) {
                    "$wins ${if (wins == 1) type.single else type.plural}  ·  $total ${if (total == 1) "nomination" else "nominations"}"
                } else "$total ${type.single} ${if (total == 1) "nomination" else "nominations"}"
                Text(line, color = Color.White, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Every award, newest first: the year (accent when won), the category, the award and who. */
@Composable
private fun AwardsSection(awards: List<AwardEntry>, side: Dp) {
    var all by rememberSaveable { mutableStateOf(false) }
    val wins = awards.count { it.won }
    Column(Modifier.padding(horizontal = side), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Awards", color = LartColors.Ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text("  ·  $wins wins  ·  ${awards.size} total", color = LartColors.InkMuted, fontSize = 15.sp)
        }
        (if (all) awards else awards.take(10)).forEach { award ->
            Column {
                RowRule()
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text(
                        award.year?.toString() ?: "—",
                        color = if (award.won) LartColors.Accent else LartColors.InkSubtle,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(44.dp),
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(award.category ?: award.awardName, color = LartColors.Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            listOfNotNull(if (award.type != AwardType.Other) award.type.title else award.awardName.takeIf { it != award.category }, award.recipient, if (award.won) "Won" else "Nominated").joinToString("  ·  "),
                            color = LartColors.InkMuted,
                            fontSize = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        if (awards.size > 10) {
            Text(
                if (all) "Show fewer" else "Show all ${awards.size}",
                color = LartColors.Accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { all = !all }.padding(vertical = 8.dp),
            )
        }
    }
}

/** MDBList's scores as hero facts, after OMDb's: Letterboxd, Trakt, RT audience, MDBList's own. */
private fun mdblistScores(m: Mdblist.Scores?): List<Score> {
    m ?: return emptyList()
    fun f(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else "%.1f".format(Locale.US, v)
    return listOfNotNull(
        m.letterboxd?.let { Score("Letterboxd", f(it)) },
        m.trakt?.let { Score("Trakt", "${f(it)}%") },
        m.rtAudience?.let { Score("Audience", "${f(it)}%") },
        m.score?.let { Score("MDBList", f(it)) },
    )
}
