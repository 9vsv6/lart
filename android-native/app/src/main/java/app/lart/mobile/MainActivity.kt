package app.lart.mobile

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.lart.mobile.data.AddonClient
import app.lart.mobile.data.CwItem
import app.lart.mobile.data.Meta
import app.lart.mobile.data.Repository
import app.lart.mobile.player.PlayerActivity
import app.lart.mobile.ui.BrowseNav
import app.lart.mobile.ui.CatalogRow
import app.lart.mobile.ui.CatalogScreen
import app.lart.mobile.ui.DetailScreen
import app.lart.mobile.ui.HomeScreen
import app.lart.mobile.ui.LartColors
import app.lart.mobile.ui.LartTheme
import app.lart.mobile.ui.LibraryScreen
import app.lart.mobile.ui.SearchScreen
import app.lart.mobile.ui.SettingsScreen
import app.lart.mobile.ui.StreamsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val repository = (application as LartApp).repository
        setContent { LartTheme { LartRoot(repository) } }
    }
}

/** Harbor's phone tabs, in its order. Profile opens Settings, as in the Tauri phone app. */
private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Search("search", "Search", Icons.Rounded.Search),
    Home("home", "Home", Icons.Rounded.Home),
    MyStuff("mystuff", "My Stuff", Icons.Rounded.Bookmark),
    Profile("profile", "Profile", Icons.Rounded.Person),
}

@Composable
private fun LartRoot(repository: Repository) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = Tab.entries.any { it.route == route }
    val context = LocalContext.current

    val browse = object : BrowseNav {
        override fun openMeta(meta: Meta) = nav.openDetail(meta)

        override fun playMeta(meta: Meta) {
            if (meta.type == "movie") nav.openStreams(meta.type, meta.id, meta.id, meta.name)
            else nav.openDetail(meta)
        }

        override fun resume(item: CwItem) {
            val episode = if (item.season != null && item.episode != null) " · S${item.season} · E${item.episode}" else ""
            nav.openStreams(item.meta.type, item.meta.id, item.videoId, item.meta.name + episode)
        }

        override fun openCatalog(row: CatalogRow) =
            nav.openCatalog(row.addon.manifest.id, row.catalog.type, row.catalog.id, row.genre, row.title)
    }

    Scaffold(
        containerColor = LartColors.Canvas,
        bottomBar = { if (showBar) TabBar(route) { nav.selectTab(it) } },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
            NavHost(nav, startDestination = Tab.Home.route) {
                composable(Tab.Home.route) { HomeScreen(repository, browse) }
                composable(Tab.Search.route) {
                    SearchScreen(repository, onOpen = { nav.openDetail(it) }) { type, genre ->
                        nav.openCatalog(AddonClient.CINEMETA_ID, type, "top", genre, "$genre ${if (type == "movie") "Movies" else "Shows"}")
                    }
                }
                composable(Tab.MyStuff.route) { LibraryScreen(repository, onOpen = { nav.openDetail(it) }, onResume = browse::resume) }
                composable(Tab.Profile.route) { SettingsScreen(repository) }
                composable(
                    "detail/{type}/{id}",
                    arguments = listOf(navArgument("type") { type = NavType.StringType }, navArgument("id") { type = NavType.StringType }),
                ) { back ->
                    val type = back.arguments?.getString("type").orEmpty()
                    val id = back.arguments?.getString("id").orEmpty()
                    DetailScreen(
                        repository,
                        type,
                        id,
                        onBack = { nav.popBackStack() },
                        onStreams = { videoId, title -> nav.openStreams(type, id, videoId, title) },
                    )
                }
                composable(
                    "catalog/{addon}/{type}/{id}?genre={genre}&title={title}",
                    arguments = listOf(
                        navArgument("addon") { type = NavType.StringType },
                        navArgument("type") { type = NavType.StringType },
                        navArgument("id") { type = NavType.StringType },
                        navArgument("genre") { type = NavType.StringType; defaultValue = "" },
                        navArgument("title") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) { back ->
                    val args = back.arguments
                    CatalogScreen(
                        repository,
                        addonId = args?.getString("addon").orEmpty(),
                        type = args?.getString("type").orEmpty(),
                        catalogId = args?.getString("id").orEmpty(),
                        genre = args?.getString("genre")?.ifEmpty { null },
                        title = args?.getString("title").orEmpty(),
                        onBack = { nav.popBackStack() },
                        onOpen = { nav.openDetail(it) },
                    )
                }
                composable(
                    "streams/{type}/{metaId}/{videoId}?title={title}",
                    arguments = listOf(
                        navArgument("type") { type = NavType.StringType },
                        navArgument("metaId") { type = NavType.StringType },
                        navArgument("videoId") { type = NavType.StringType },
                        navArgument("title") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) { back ->
                    val args = back.arguments
                    val type = args?.getString("type").orEmpty()
                    val metaId = args?.getString("metaId").orEmpty()
                    val videoId = args?.getString("videoId").orEmpty()
                    val title = args?.getString("title").orEmpty()
                    StreamsScreen(repository, type, videoId, title, onBack = { nav.popBackStack() }) { ready ->
                        context.startActivity(PlayerActivity.intent(context, ready, type, metaId, videoId, title))
                    }
                }
            }
        }
    }
}

/** Harbor's floating tab bar: icon-only, the active tab on a light chip. */
@Composable
private fun TabBar(route: String?, onSelect: (Tab) -> Unit) {
    val account = (LocalContext.current.applicationContext as LartApp).repository.account.collectAsState().value
    Box(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, LartColors.Canvas.copy(alpha = 0.92f), LartColors.Canvas)))
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.widthIn(max = 400.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(LartColors.Elevated.copy(alpha = 0.92f))
                .border(1.dp, LartColors.EdgeSoft, RoundedCornerShape(12.dp))
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Tab.entries.forEach { tab ->
                val on = route == tab.route
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.width(52.dp).height(40.dp).clip(RoundedCornerShape(13.dp))
                            .background(if (on) LartColors.Ink else Color.Transparent)
                            .clickable { onSelect(tab) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (tab == Tab.Profile && account != null) {
                            // The account's initial, like Harbor's profile avatar.
                            Box(
                                Modifier.size(23.dp).clip(CircleShape).background(LartColors.Accent),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(account.email.take(1).uppercase(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Icon(
                                tab.icon,
                                contentDescription = tab.label,
                                tint = if (on) LartColors.Canvas else LartColors.InkSubtle,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun enc(value: String): String = Uri.encode(value)

private fun NavHostController.selectTab(tab: Tab) = navigate(tab.route) {
    popUpTo(Tab.Home.route) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

private fun NavHostController.openDetail(meta: Meta) = navigate("detail/${enc(meta.type)}/${enc(meta.id)}")

private fun NavHostController.openStreams(type: String, metaId: String, videoId: String, title: String) =
    navigate("streams/${enc(type)}/${enc(metaId)}/${enc(videoId)}?title=${enc(title)}")

private fun NavHostController.openCatalog(addonId: String, type: String, catalogId: String, genre: String?, title: String) =
    navigate("catalog/${enc(addonId)}/${enc(type)}/${enc(catalogId)}?genre=${enc(genre.orEmpty())}&title=${enc(title)}")
