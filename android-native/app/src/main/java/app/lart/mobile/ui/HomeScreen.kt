package app.lart.mobile.ui

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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** A catalog row and what "See all" opens. */
data class CatalogRow(
    val key: String,
    val title: String,
    val metas: List<Meta>,
    val addon: Addon,
    val catalog: CatalogDef,
    val genre: String? = null,
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
fun HomeScreen(repository: Repository, nav: BrowseNav) {
    val addons by repository.addons.collectAsState()
    val builtins by repository.builtins.collectAsState()
    val cw by repository.continueWatching.collectAsState()
    var view by rememberSaveable { mutableStateOf(BrowseView.Home) }
    val rows = remember { mutableStateMapOf<BrowseView, List<CatalogRow>>() }
    val failed = remember { mutableStateMapOf<BrowseView, Boolean>() }

    LaunchedEffect(view, addons, builtins) {
        if (addons.isEmpty() || rows[view] != null) return@LaunchedEffect
        val built = buildRows(view, addons, repository.metaAddons())
        if (built.isNotEmpty() || builtins.isNotEmpty()) {
            rows[view] = built
            failed[view] = built.isEmpty()
        }
    }

    Box(Modifier.fillMaxSize().background(LartColors.Canvas)) {
        val current = rows[view]
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
                    current.firstOrNull()?.let { first ->
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
                    items(current.drop(1), key = { it.key }) { row ->
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
            ViewSwitcher(view) { view = it }
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
            val movies = rows.firstOrNull { it.catalog.type == "movie" }?.metas.orEmpty()
            val shows = rows.firstOrNull { it.catalog.type == "series" }?.metas.orEmpty()
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
            CatalogRow("${addon.manifest.id}/${catalog.type}/${catalog.id}/${genre.orEmpty()}", title, metas, addon, catalog, genre)
        }
    }.awaitAll().filter { it.metas.isNotEmpty() }
}

private val CatalogDef.supportsSearchOnly: Boolean get() = id.contains("search", ignoreCase = true)
