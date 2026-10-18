package app.lart.mobile

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.lart.mobile.data.Meta
import app.lart.mobile.data.Repository
import app.lart.mobile.player.PlayerActivity
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

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Home", Icons.Rounded.Home),
    Search("search", "Search", Icons.Rounded.Search),
    Library("library", "Library", Icons.Rounded.Bookmarks),
    Settings("settings", "Settings", Icons.Rounded.Settings),
}

@Composable
private fun LartRoot(repository: Repository) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = Tab.entries.any { it.route == route }
    val context = LocalContext.current

    Scaffold(
        containerColor = LartColors.Canvas,
        bottomBar = {
            if (showBar) {
                NavigationBar(containerColor = LartColors.Surface) {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(Tab.Home.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.Black,
                                indicatorColor = Color.White,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
            NavHost(nav, startDestination = Tab.Home.route) {
                composable(Tab.Home.route) { HomeScreen(repository) { nav.openDetail(it) } }
                composable(Tab.Search.route) { SearchScreen(repository) { nav.openDetail(it) } }
                composable(Tab.Library.route) { LibraryScreen(repository) { nav.openDetail(it) } }
                composable(Tab.Settings.route) { SettingsScreen(repository) }
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
                        onStreams = { videoId, title ->
                            nav.navigate("streams/${enc(type)}/${enc(id)}/${enc(videoId)}?title=${enc(title)}")
                        },
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
                        context.startActivity(PlayerActivity.intent(context, ready, type, metaId, videoId))
                    }
                }
            }
        }
    }
}

private fun enc(value: String): String = Uri.encode(value)

private fun NavHostController.openDetail(meta: Meta) = navigate("detail/${enc(meta.type)}/${enc(meta.id)}")
