package app.lart.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lart.mobile.data.AddonClient
import app.lart.mobile.data.Meta
import app.lart.mobile.data.Repository
import coil.compose.AsyncImage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

private data class HomeRow(val key: String, val title: String, val metas: List<Meta>)

private const val MAX_ROWS = 12

@Composable
fun HomeScreen(repository: Repository, onOpen: (Meta) -> Unit) {
    val addons by repository.addons.collectAsState()
    val library by repository.library.collectAsState()
    var rows by remember { mutableStateOf<List<HomeRow>?>(null) }

    LaunchedEffect(addons) {
        if (addons.isEmpty()) return@LaunchedEffect
        rows = coroutineScope {
            addons.flatMap { addon ->
                addon.manifest.catalogs.filter { !it.requiresExtra }.map { addon to it }
            }.take(MAX_ROWS).map { (addon, catalog) ->
                async {
                    val metas = runCatching { AddonClient.catalog(addon, catalog) }.getOrDefault(emptyList())
                    val type = catalog.type.replaceFirstChar { it.uppercase() }
                    HomeRow("${addon.manifest.id}/${catalog.type}/${catalog.id}", "${catalog.name ?: catalog.id} · $type", metas)
                }
            }.awaitAll().filter { it.metas.isNotEmpty() }
        }
    }

    val continueWatching = remember(library) {
        library.filter { !it.removed && (it.state?.timeOffset ?: 0) > 0 }
            .sortedByDescending { it.state?.lastWatched ?: it.mtime ?: "" }
            .take(20)
            .map { it.toMeta() }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(LartColors.Canvas).statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        val current = rows
        when {
            current == null -> item { Loading(Modifier.padding(top = 120.dp)) }
            current.isEmpty() -> item {
                EmptyState("Nothing to show", "None of your addons returned a catalog. Check your connection or your addons in Settings.")
            }
            else -> {
                current.first().metas.firstOrNull()?.let { featured ->
                    item(key = "hero") { Hero(featured) { onOpen(featured) } }
                }
                if (continueWatching.isNotEmpty()) {
                    item(key = "cw") { PosterRow("Continue watching", continueWatching, onOpen) }
                }
                items(current, key = { it.key }) { row -> PosterRow(row.title, row.metas, onOpen) }
            }
        }
    }
}

@Composable
private fun Hero(meta: Meta, onOpen: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp)
            .heightIn(min = 220.dp, max = 420.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(LartColors.Elevated)
            .clickable(onClick = onOpen),
    ) {
        AsyncImage(
            model = meta.background ?: meta.poster,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 420.dp),
        )
        Box(
            Modifier
                .matchParentSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xEE000000)))),
        )
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (meta.logo != null) {
                AsyncImage(model = meta.logo, contentDescription = meta.name, modifier = Modifier.heightIn(max = 64.dp))
            } else {
                Text(meta.name, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
            val line = listOfNotNull(meta.releaseInfo, meta.imdbRating?.let { "IMDb $it" }, meta.genres?.firstOrNull())
            if (line.isNotEmpty()) Text(line.joinToString("  ·  "), fontSize = 13.sp, color = Color(0xCCFFFFFF))
        }
    }
}
