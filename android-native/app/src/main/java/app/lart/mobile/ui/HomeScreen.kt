package app.lart.mobile.ui

import app.lart.mobile.data.Jikan
import app.lart.mobile.data.HarborRows
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lart.mobile.data.Addon
import app.lart.mobile.data.AddonClient
import app.lart.mobile.data.CatalogDef
import app.lart.mobile.data.CwItem
import app.lart.mobile.data.Meta
import app.lart.mobile.data.Repository
import app.lart.mobile.data.Tmdb
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** A catalog row and what "See all" opens. */
data class CatalogRow(
    val key: String,
    val title: String,
    val metas: List<Meta>,
    val type: String,
    val addon: Addon? = null,
    val catalog: CatalogDef? = null,
    val genre: String? = null,
    /** Set for TMDB rows, which "See all" pages through TMDB instead of an addon. */
    val tmdb: Tmdb.RowSpec? = null,
)

/** Where a title or a row leads; the NavHost turns these into routes. */
interface BrowseNav {
    fun openMeta(meta: Meta)
    fun playMeta(meta: Meta)
    fun resume(item: CwItem)
    fun openCatalog(row: CatalogRow)
}

private val MOVIE_GENRES = listOf("Action", "Comedy", "Drama", "Sci-Fi", "Thriller", "Animation", "Horror", "Romance", "Crime", "Family")
private val SHOW_GENRES = listOf("Drama", "Comedy", "Crime", "Sci-Fi", "Animation", "Documentary", "Mystery", "Reality-TV", "Action", "Family")

@Composable
/**
 * `view` is Home, Movies, Shows or Anime; phones switch it from the pill at the top right,
 * tablets from the side rail (Harbor's desktop sidebar), so it lives with the caller.
 */
fun HomeScreen(repository: Repository, nav: BrowseNav, view: BrowseView, onView: (BrowseView) -> Unit, showSwitcher: Boolean = true) {
    val addons by repository.addons.collectAsState()
    val builtins by repository.builtins.collectAsState()
    val cw by repository.continueWatching.collectAsState()
    val tmdbKey by repository.tmdbKey.collectAsState()
    // Rebuilt from scratch when the TMDB key changes.
    val rows = remember(tmdbKey) { mutableStateMapOf<BrowseView, List<CatalogRow>>() }
    val failed = remember(tmdbKey) { mutableStateMapOf<BrowseView, Boolean>() }

    LaunchedEffect(view, addons, builtins, tmdbKey) {
        if (addons.isEmpty() || rows[view] != null) return@LaunchedEffect
        val key = tmdbKey
        // Harbor's rows: TMDB lists with a key (Cinemeta's on Home without one), MyAnimeList for anime.
        val built = when {
            view == BrowseView.Anime -> emptyList()
            key != null -> tmdbRows(view, key).ifEmpty { buildRows(view, addons, repository.metaAddons()) }
            view == BrowseView.Home -> cinemetaRows(repository.metaAddons()).ifEmpty { buildRows(view, addons, repository.metaAddons()) }
            else -> buildRows(view, addons, repository.metaAddons())
        }
        if (view != BrowseView.Anime && (built.isNotEmpty() || builtins.isNotEmpty())) {
            rows[view] = built
            failed[view] = built.isEmpty()
        }
        // Anime rows arrive one at a time (the anime catalogs allow a few calls a second).
        val anime = when {
            view == BrowseView.Anime -> Jikan.animeRows()
            view == BrowseView.Home && !LartDisplay.settings.hideAnime -> Jikan.homeRows()
            else -> emptyList()
        }
        for (spec in anime) {
            val metas = runCatching { Jikan.row(spec) }.getOrDefault(emptyList())
            if (metas.size < 6) continue
            rows[view] = rows[view].orEmpty() + CatalogRow("jikan${spec.path}${spec.params}", spec.title, metas, "series")
        }
        if (view == BrowseView.Anime && rows[view] == null) {
            val fallback = buildRows(view, addons, repository.metaAddons())
            rows[view] = fallback
            failed[view] = fallback.isEmpty()
        }
    }

    val display = LartDisplay.settings
    LaunchedEffect(display.hideAnime) { if (display.hideAnime && view == BrowseView.Anime) onView(BrowseView.Home) }

    Box(Modifier.fillMaxSize().background(LartColors.Canvas)) {
        val current = rows[view]?.let { tidyRows(it, display) }
        val listState = rememberLazyListState()
        LaunchedEffect(view) { listState.scrollToItem(0) }
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 68.dp
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = top, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                current == null -> item { Loading(Modifier.padding(top = 120.dp)) }
                current.isEmpty() && (view != BrowseView.Home || cw.isEmpty()) -> item {
                    EmptyState(
                        if (failed[view] == true) "Couldn't load ${view.label.lowercase()}" else "Nothing here yet",
                        "Lart couldn't reach the catalog servers. Check your connection and try again.",
                    )
                }
                else -> {
                    val heroSlides = heroFor(view, current)
                    item(key = "hero") {
                        HeroCarousel(
                            slides = heroSlides,
                            badge = { i, meta -> "#${i + 1} in ${typeLabel(meta.type, view)} Today" },
                            inMyList = { repository.inMyList(it.id) },
                            onPlay = nav::playMeta,
                            onToggleList = repository::toggleMyList,
                            onOpen = nav::openMeta,
                        )
                    }
                    if (view == BrowseView.Home && cw.isNotEmpty()) {
                        item(key = "cw") { ContinueWatchingRow(cw, nav::resume, repository::dismissContinueWatching) }
                    }
                    current.firstOrNull()?.takeIf { display.showTop10 }?.let { first ->
                        item(key = "top10") {
                            RankRail(
                                when (view) {
                                    BrowseView.Home -> "Top 10 Today"
                                    else -> "Top 10 ${view.label} Today"
                                },
                                first.metas.distinctBy { it.id },
                                nav::openMeta,
                            )
                        }
                    }
                    items(if (display.showTop10) current.drop(1) else current, key = { it.key }) { row ->
                        Rail(row.title, row.metas.distinctBy { it.id }.take(18), nav::openMeta) { nav.openCatalog(row) }
                    }
                }
            }
        }

        // Harbor's floating header: the wordmark and the view switcher, over a fade.
        Box(
            Modifier.fillMaxWidth().height(top + 12.dp)
                .background(Brush.verticalGradient(0f to LartColors.Canvas, 0.78f to LartColors.Canvas.copy(alpha = 0.96f), 1f to Color.Transparent)),
        )
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Lart", color = LartColors.Ink, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp)
            Spacer(Modifier.weight(1f))
            if (showSwitcher) ViewSwitcher(view, onView)
        }
    }
}

private fun typeLabel(type: String, view: BrowseView): String = when {
    view == BrowseView.Anime -> "Anime"
    type == "series" -> "Series"
    else -> "Movies"
}

private fun heroFor(view: BrowseView, rows: List<CatalogRow>): List<Meta> {
    val pool = when (view) {
        BrowseView.Home -> {
            // Alternate the first movie and series rows, like Harbor's mixed hero.
            if (rows.firstOrNull()?.tmdb != null) return rows.first().metas.filter { it.background != null }.take(8)
            val movies = rows.firstOrNull { it.type == "movie" }?.metas.orEmpty()
            val shows = rows.firstOrNull { it.type == "series" }?.metas.orEmpty()
            movies.zip(shows).flatMap { (a, b) -> listOf(a, b) }.ifEmpty { rows.firstOrNull()?.metas.orEmpty() }
        }
        else -> rows.firstOrNull()?.metas.orEmpty()
    }
    return pool.filter { it.background != null }.distinctBy { it.name.lowercase() }.take(8)
}

private fun plural(type: String): String = when (type) {
    "movie" -> "Movies"
    "series" -> "Shows"
    "anime" -> "Anime"
    "channel" -> "Channels"
    "tv" -> "TV"
    else -> type.replaceFirstChar { it.uppercase() }
}

/** "Popular" + movie -> "Popular Movies"; names that already say the type stay as they are. */
fun rowTitle(catalog: CatalogDef, genre: String? = null): String {
    val name = catalog.name?.takeIf { it.isNotBlank() } ?: catalog.id
    val type = plural(catalog.type)
    if (genre != null) return "$genre $type"
    val lower = name.lowercase()
    val says = listOf("movie", "film", "show", "series", "anime", "tv", "channel").any { lower.contains(it) }
    return if (says) name else "$name $type"
}

private suspend fun buildRows(view: BrowseView, addons: List<Addon>, metaAddons: List<Addon>): List<CatalogRow> = coroutineScope {
    val plain = { addon: Addon -> addon.manifest.catalogs.filter { !it.requiresExtra && !it.supportsSearchOnly } }
    val specs: List<Triple<Addon, CatalogDef, String?>> = when (view) {
        BrowseView.Home -> addons.flatMap { a -> plain(a).filter { it.type == "movie" || it.type == "series" }.map { Triple(a, it, null) } }.take(14)
        BrowseView.Movies, BrowseView.Shows -> {
            val type = if (view == BrowseView.Movies) "movie" else "series"
            val catalogs = metaAddons.flatMap { a -> plain(a).filter { it.type == type && a.manifest.id != AddonClient.KITSU_ID }.map { Triple(a, it, null as String?) } }
            // Genre rows from Cinemeta's Popular catalog, as Harbor's Movies and Shows views have.
            val cinemeta = metaAddons.firstOrNull { it.manifest.id == AddonClient.CINEMETA_ID }
            val popular = cinemeta?.manifest?.catalogs?.firstOrNull { it.type == type && it.id == "top" }
            val genres = if (popular != null) {
                (if (type == "movie") MOVIE_GENRES else SHOW_GENRES)
                    .filter { g -> popular.extra.any { e -> e.name == "genre" && e.options?.contains(g) != false } }
                    .map { Triple(cinemeta, popular, it as String?) }
            } else emptyList()
            catalogs.take(8) + genres
        }
        BrowseView.Anime -> {
            val kitsu = metaAddons.filter { it.manifest.id == AddonClient.KITSU_ID }
            val fromKitsu = kitsu.flatMap { a -> plain(a).filter { it.type == "anime" }.map { Triple(a, it, null as String?) } }
            val others = addons.filter { it.manifest.id != AddonClient.KITSU_ID }
                .flatMap { a -> plain(a).filter { it.type == "anime" }.map { Triple(a, it, null as String?) } }
            fromKitsu + others
        }
    }
    specs.map { (addon, catalog, genre) ->
        async {
            val extra = genre?.let { mapOf("genre" to it) } ?: emptyMap()
            val metas = runCatching { AddonClient.catalog(addon, catalog, extra) }.getOrDefault(emptyList())
            val title = when {
                view == BrowseView.Anime -> catalog.name?.removePrefix("Kitsu ")?.let { "$it Anime" } ?: rowTitle(catalog)
                else -> rowTitle(catalog, genre)
            }
            CatalogRow("${addon.manifest.id}/${catalog.type}/${catalog.id}/${genre.orEmpty()}", title, metas, catalog.type, addon, catalog, genre)
        }
    }.awaitAll().filter { it.metas.isNotEmpty() }
}

/** Harbor's TMDB rows; the first row's hero titles get their logos. */
private suspend fun tmdbRows(view: BrowseView, key: String): List<CatalogRow> = coroutineScope {
    val specs = when (view) {
        BrowseView.Movies -> HarborRows.movies()
        BrowseView.Shows -> HarborRows.shows()
        else -> HarborRows.home()
    }
    specs.mapIndexed { index, spec ->
        async {
            val metas = runCatching { Tmdb.row(key, spec) }.getOrDefault(emptyList()).let { list -> if (spec.path == "/movie/now_playing") list.map { it.copy(inTheaters = true) } else list }
            val shown = if (index == 0) {
                val hero = metas.filter { it.background != null }.take(8)
                val withLogos = Tmdb.withLogos(key, hero).associateBy { it.id }
                metas.map { withLogos[it.id] ?: it }
            } else metas
            CatalogRow("tmdb${spec.path}${spec.params}", spec.title, shown, spec.type ?: "movie", tmdb = spec)
        }
    }.awaitAll().filter { it.metas.isNotEmpty() }
}

private val CatalogDef.supportsSearchOnly: Boolean get() = id.contains("search", ignoreCase = true)

/** Settings → Library: drop unreleased titles and anime from the rows when asked. */
private fun tidyRows(rows: List<CatalogRow>, settings: app.lart.mobile.data.AppSettings): List<CatalogRow> {
    if (!settings.hideUnreleased && !settings.hideAnime) return rows
    val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
    return rows.map { row ->
        row.copy(
            metas = row.metas.filter { meta ->
                val unreleased = settings.hideUnreleased && (meta.year?.toIntOrNull()?.let { it > year } ?: false)
                val anime = settings.hideAnime && (meta.type == "anime" || meta.genres.orEmpty().any { it.equals("anime", true) })
                !unreleased && !anime
            },
        )
    }.filter { it.metas.isNotEmpty() }
}

/** Harbor's Home without a TMDB key (buildCinemetaRows): Cinemeta's top lists, overall and by genre. */
private suspend fun cinemetaRows(metaAddons: List<Addon>): List<CatalogRow> = coroutineScope {
    val cinemeta = metaAddons.firstOrNull { it.manifest.id == AddonClient.CINEMETA_ID } ?: return@coroutineScope emptyList()
    fun top(type: String) = cinemeta.manifest.catalogs.firstOrNull { it.type == type && it.id == "top" }
    val specs = listOf(
        "Top 10 on Stremio" to Triple("movie", null as String?, 0..9),
        "Popular Movies" to Triple("movie", null, 10..39),
        "Top 10 Drama" to Triple("movie", "Drama", 0..9),
        "Trending Series" to Triple("series", null, 0..29),
        "Top 10 Comedy" to Triple("movie", "Comedy", 0..9),
        "Action Hits" to Triple("movie", "Action", 0..29),
        "Sci-Fi & Fantasy" to Triple("movie", "Sci-Fi", 0..29),
        "Thrillers" to Triple("movie", "Thriller", 0..29),
        "Animated Movies" to Triple("movie", "Animation", 0..29),
        "Horror" to Triple("movie", "Horror", 0..29),
        "Romance" to Triple("movie", "Romance", 0..29),
        "Adventure" to Triple("movie", "Adventure", 0..29),
        "Documentaries" to Triple("movie", "Documentary", 0..29),
        "Mystery" to Triple("movie", "Mystery", 0..29),
        "Fantasy" to Triple("movie", "Fantasy", 0..29),
        "Drama Series" to Triple("series", "Drama", 0..29),
        "Comedy Series" to Triple("series", "Comedy", 0..29),
        "Crime Series" to Triple("series", "Crime", 0..29),
    )
    val pages = mutableMapOf<Pair<String, String?>, kotlinx.coroutines.Deferred<List<Meta>>>()
    specs.map { (title, spec) ->
        val (type, genre, range) = spec
        val catalog = top(type) ?: return@map null
        val page = pages.getOrPut(type to genre) {
            async {
                val first = runCatching { AddonClient.catalog(cinemeta, catalog, genre?.let { mapOf("genre" to it) } ?: emptyMap()) }.getOrDefault(emptyList())
                // "Popular Movies" reads past the first page of Cinemeta's list.
                if (genre == null && type == "movie" && first.size < 40) {
                    first + runCatching { AddonClient.catalog(cinemeta, catalog, mapOf("skip" to "${first.size}")) }.getOrDefault(emptyList())
                } else first
            }
        }
        Triple(title, type, Pair(range, page)) to Pair(catalog, genre)
    }.filterNotNull().map { (row, source) ->
        val (title, type, data) = row
        val (range, page) = data
        val metas = page.await().let { list -> list.subList(range.first.coerceAtMost(list.size), (range.last + 1).coerceAtMost(list.size)) }
        CatalogRow("cinemeta/$title", title, metas, type, cinemeta, source.first, source.second)
    }.filter { it.metas.isNotEmpty() }
}
