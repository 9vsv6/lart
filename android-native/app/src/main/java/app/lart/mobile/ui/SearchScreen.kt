package app.lart.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lart.mobile.data.AddonClient
import app.lart.mobile.data.Meta
import app.lart.mobile.data.Repository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

/** Genre tiles shown before a search, each with its own colour pair. */
private val GENRES = listOf(
    Triple("Action", "movie", 0xFFB23A48 to 0xFF5C1A2B),
    Triple("Comedy", "movie", 0xFFE0A030 to 0xFF8A4B12),
    Triple("Drama", "series", 0xFF4F6DB8 to 0xFF243566),
    Triple("Sci-Fi", "movie", 0xFF2E9C9C to 0xFF13474F),
    Triple("Crime", "series", 0xFF5E5E6E to 0xFF26262E),
    Triple("Horror", "movie", 0xFF7A1F1F to 0xFF2A0A0A),
    Triple("Animation", "movie", 0xFF8E5BD8 to 0xFF3E2470),
    Triple("Documentary", "series", 0xFF3E8E5A to 0xFF173B25),
    Triple("Romance", "movie", 0xFFD0587A to 0xFF6A1F38),
    Triple("Thriller", "movie", 0xFF3B4A5C to 0xFF141B24),
    Triple("Mystery", "series", 0xFF6B4E9B to 0xFF2A1E40),
    Triple("Family", "movie", 0xFF4AA3D8 to 0xFF1D4A6A),
)

@Composable
fun SearchScreen(repository: Repository, onOpen: (Meta) -> Unit, onGenre: (type: String, genre: String) -> Unit) {
    val addons by repository.addons.collectAsState()
    val builtins by repository.builtins.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Meta>?>(null) }
    var loading by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(query, addons, builtins) {
        val q = query.trim()
        if (q.length < 2) {
            results = null
            return@LaunchedEffect
        }
        delay(350) // Wait for typing to pause.
        loading = true
        val sources = repository.metaAddons()
        results = coroutineScope {
            sources.flatMap { addon -> addon.manifest.catalogs.filter { it.supportsSearch }.map { addon to it } }
                .map { (addon, catalog) ->
                    async {
                        runCatching { AddonClient.catalog(addon, catalog, mapOf("search" to q)) }
                            .onFailure { android.util.Log.w("LartSearch", "${addon.manifest.name}/${catalog.id} failed", it) }
                            .getOrDefault(emptyList())
                    }
                }.awaitAll()
                // Interleave the addons' results so one addon's long list doesn't bury the others.
                .let { lists -> (0 until (lists.maxOfOrNull { it.size } ?: 0)).flatMap { i -> lists.mapNotNull { it.getOrNull(i) } } }
                .distinctBy { it.id }
        }
        loading = false
    }

    Column(Modifier.fillMaxSize().background(LartColors.Canvas).statusBarsPadding()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            placeholder = { Text("Movies, shows, anime") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = LartColors.Accent,
                unfocusedBorderColor = LartColors.Elevated,
                focusedContainerColor = LartColors.Surface,
                unfocusedContainerColor = LartColors.Surface,
            ),
        )
        val current = results
        when {
            loading && current == null -> Loading()
            current == null -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("Browse by genre", color = LartColors.Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }
                items(GENRES, key = { it.first }) { (genre, type, colors) ->
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp))
                            .background(Brush.linearGradient(listOf(Color(colors.first.toInt()), Color(colors.second.toInt()))))
                            .clickable { onGenre(type, genre) }.padding(14.dp),
                        contentAlignment = Alignment.BottomStart,
                    ) {
                        Text(genre, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            current.isEmpty() -> EmptyState("No results", "None of your addons found \"${query.trim()}\".")
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 112.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(current, key = { it.type + it.id }) { meta -> PosterTile(meta) { onOpen(meta) } }
            }
        }
    }
}
