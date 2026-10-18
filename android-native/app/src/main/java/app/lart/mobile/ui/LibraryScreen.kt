package app.lart.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lart.mobile.data.Meta
import app.lart.mobile.data.Repository

private enum class Shelf(val label: String) { All("All"), Movies("Movies"), Series("Series"), Watching("Continue watching") }

@Composable
fun LibraryScreen(repository: Repository, onOpen: (Meta) -> Unit) {
    val account by repository.account.collectAsState()
    val library by repository.library.collectAsState()
    var shelf by rememberSaveable { mutableStateOf(Shelf.All) }

    // Like Stremio's Library: everything added, minus items it adds just for opening a page.
    val items = remember(library, shelf) {
        library.filter { !it.removed && !it.temp || (it.state?.timeOffset ?: 0) > 0 && !it.removed }
            .filter {
                when (shelf) {
                    Shelf.All -> true
                    Shelf.Movies -> it.type == "movie"
                    Shelf.Series -> it.type == "series"
                    Shelf.Watching -> (it.state?.timeOffset ?: 0) > 0
                }
            }
            .sortedByDescending { it.state?.lastWatched ?: it.mtime ?: "" }
            .map { it.toMeta() }
            .distinctBy { it.type + it.id }
    }

    Column(Modifier.fillMaxSize().background(LartColors.Canvas).statusBarsPadding()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("MY LIBRARY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LartColors.InkSubtle, letterSpacing = 2.sp)
            Text("Your collection", fontSize = 26.sp, fontWeight = FontWeight.Medium, color = LartColors.Ink)
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Shelf.entries.forEach { s ->
                FilterChip(
                    selected = s == shelf,
                    onClick = { shelf = s },
                    label = { Text(s.label) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color.White, selectedLabelColor = Color.Black),
                )
            }
        }
        when {
            account == null -> EmptyState("Sign in to Stremio", "Your Stremio library shows up here. Sign in from Settings.")
            items.isEmpty() -> EmptyState("Nothing here yet", "Titles you add in Stremio or Lart appear here.")
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 112.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(items, key = { it.type + it.id }) { meta -> PosterCard(meta, width = null) { onOpen(meta) } }
            }
        }
    }
}
