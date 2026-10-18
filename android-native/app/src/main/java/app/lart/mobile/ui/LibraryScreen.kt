package app.lart.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as rowItems
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lart.mobile.data.CwItem
import app.lart.mobile.data.Meta
import app.lart.mobile.data.Repository

private enum class Shelf(val label: String) { MyList("My List"), Watching("Continue watching"), Stremio("Stremio library") }

private enum class Kind(val label: String) { All("All"), Movies("Movies"), Shows("Shows") }

/** Harbor's "My Stuff": My List, Continue watching, and the Stremio library. */
@Composable
fun LibraryScreen(repository: Repository, onOpen: (Meta) -> Unit, onResume: (CwItem) -> Unit) {
    val account by repository.account.collectAsState()
    val library by repository.library.collectAsState()
    val myList by repository.myList.collectAsState()
    val cw by repository.continueWatching.collectAsState()
    var shelf by rememberSaveable { mutableStateOf(Shelf.MyList) }
    var kind by rememberSaveable { mutableStateOf(Kind.All) }

    fun Meta.matches() = when (kind) {
        Kind.All -> true
        Kind.Movies -> type == "movie"
        Kind.Shows -> type != "movie"
    }

    // Like Stremio's Library: everything added, minus items it adds just for opening a page.
    val stremio = remember(library) {
        library.filter { !it.removed && (!it.temp || (it.state?.timeOffset ?: 0) > 0) }
            .sortedByDescending { it.state?.lastWatched ?: it.mtime ?: "" }
            .map { it.toMeta() }
            .distinctBy { it.id }
    }

    Column(Modifier.fillMaxSize().background(LartColors.Canvas).statusBarsPadding()) {
        Text(
            "My Stuff",
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            color = LartColors.Ink,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            rowItems(Shelf.entries) { s ->
                FilterChip(
                    selected = s == shelf,
                    onClick = { shelf = s },
                    label = { Text(s.label) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color.White, selectedLabelColor = Color.Black),
                )
            }
        }
        if (shelf != Shelf.Watching) {
            Row(Modifier.padding(start = 16.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Kind.entries.forEach { k ->
                    FilterChip(
                        selected = k == kind,
                        onClick = { kind = k },
                        label = { Text(k.label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = LartColors.Raised, selectedLabelColor = LartColors.Ink),
                    )
                }
            }
        }
        when (shelf) {
            Shelf.MyList -> {
                val shown = myList.filter { it.matches() }
                if (shown.isEmpty()) EmptyState("Your list is empty", "Tap + on any title to keep it here. My List stays on this device.")
                else PosterGrid(shown, onOpen)
            }
            Shelf.Watching -> {
                if (cw.isEmpty()) EmptyState("Nothing in progress", "Titles you start watching show up here.")
                else LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 112.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text("Tap a title to pick up where you left off.", color = LartColors.InkSubtle, fontSize = 13.sp)
                    }
                    items(cw, key = { it.meta.id }) { item -> PosterTile(item.meta) { onResume(item) } }
                }
            }
            Shelf.Stremio -> when {
                account == null -> EmptyState("Sign in to Stremio", "Your Stremio library shows up here. Sign in from Profile.")
                else -> {
                    val shown = stremio.filter { it.matches() }
                    if (shown.isEmpty()) EmptyState("Nothing here yet", "Titles you add in Stremio appear here.")
                    else PosterGrid(shown, onOpen)
                }
            }
        }
    }
}

@Composable
private fun PosterGrid(metas: List<Meta>, onOpen: (Meta) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 112.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(metas, key = { it.type + it.id }) { meta -> PosterTile(meta) { onOpen(meta) } }
    }
}
