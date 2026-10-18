package app.lart.mobile.ui

import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import app.lart.mobile.player.Anime4K
import app.lart.mobile.player.SubtitleText
import app.lart.mobile.player.languageName
import java.util.Locale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalUriHandler
import app.lart.mobile.data.DebridAccount
import app.lart.mobile.data.DebridService
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lart.mobile.data.Repository
import kotlinx.coroutines.launch

/** Harbor's settings groups on phones (`src/views/settings/groups.ts`), the ones Lart native has. */
private enum class SettingsPage(val label: String, val sub: String, val icon: ImageVector) {
    Account("Account & setup", "Your Stremio sign-in, profiles, and the trackers that follow what you watch.", Icons.Rounded.ManageAccounts),
    Playback("Playback", "How video plays, Anime4K, and what happens between episodes.", Icons.Rounded.PlayCircle),
    Languages("Languages", "Which subtitles turn on, and how they look.", Icons.Rounded.Translate),
    Sources("Sources & library", "Addons, debrid for torrents, and TMDB metadata.", Icons.Rounded.VideoLibrary),
    Help("Help & about", "This build, its licences, and where the code lives.", Icons.AutoMirrored.Rounded.HelpOutline),
}

@Composable
fun SettingsScreen(repository: Repository) {
    var page by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    BackHandler(enabled = page != null) { page = null }

    Column(Modifier.fillMaxSize().background(LartColors.Canvas).statusBarsPadding()) {
        val current = page
        if (current == null) {
            SettingsHome(repository) { page = it }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                IconButton(onClick = { page = null }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                Text(current.label, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(current.sub, color = LartColors.InkMuted, fontSize = 13.sp)
                when (current) {
                    SettingsPage.Account -> {
                        StremioCard(repository)
                        ProfilesCard(repository)
                        TrackersCard(repository)
                    }
                    SettingsPage.Playback -> PlaybackPage(repository)
                    SettingsPage.Languages -> LanguagesPage(repository)
                    SettingsPage.Sources -> {
                        AddonsCard(repository)
                        DebridCard(repository)
                        KeyCard(
                            title = "TMDB",
                            body = "With your own TMDB API key, Home, Movies and Shows use TMDB's rows, artwork, logos and cast, in your language, as in Harbor.",
                            label = "TMDB API key or read token",
                            current = repository.tmdbKey.collectAsState().value,
                            linkLabel = "Get a key",
                            link = "https://www.themoviedb.org/settings/api",
                            onSave = { repository.setTmdbKey(it) },
                        )
                    }
                    SettingsPage.Help -> AboutCard()
                }
            }
        }
    }
}

/** The rail Harbor shows on phones: who you are, two shortcuts, then the groups. */
@Composable
private fun SettingsHome(repository: Repository, onOpen: (SettingsPage) -> Unit) {
    val profile by repository.activeProfile.collectAsState()
    val account by repository.account.collectAsState()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Settings", fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = LartColors.Ink)
        Row(
            Modifier.fillMaxWidth().background(LartColors.Surface, RoundedCornerShape(18.dp)).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ProfileAvatar(profile, 56.dp)
            Column(Modifier.weight(1f)) {
                Text(profile?.name ?: "Me", color = LartColors.Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    account?.let { "Stremio · ${it.email}" } ?: "Not signed in to Stremio",
                    color = LartColors.InkMuted,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(Modifier.fillMaxWidth().background(LartColors.Surface, RoundedCornerShape(18.dp)).padding(vertical = 4.dp)) {
            RailRow(Icons.Rounded.Group, "Switch profile", null) { repository.showProfilePicker() }
            RailRow(Icons.Rounded.Extension, "Addons", null) { onOpen(SettingsPage.Sources) }
        }
        Column(Modifier.fillMaxWidth().background(LartColors.Surface, RoundedCornerShape(18.dp)).padding(vertical = 4.dp)) {
            SettingsPage.entries.forEach { entry -> RailRow(entry.icon, entry.label, entry.sub) { onOpen(entry) } }
        }
    }
}

@Composable
private fun RailRow(icon: ImageVector, label: String, sub: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(LartColors.Elevated), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = LartColors.Ink, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(label, color = LartColors.Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            sub?.let { Text(it, color = LartColors.InkSubtle, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = LartColors.InkSubtle)
    }
}

@Composable
private fun StremioCard(repository: Repository) {
    val account by repository.account.collectAsState()
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Card {
        SectionTitle("Stremio account")
        val current = account
        if (current != null) {
            Text("Signed in as ${current.email}", color = LartColors.InkMuted, fontSize = 14.sp)
            OutlinedButton(onClick = { scope.launch { busy = true; repository.signOut(); busy = false } }, enabled = !busy) {
                Text("Sign out")
            }
        } else {
            Text("Sign in to use your Stremio addons and library.", color = LartColors.InkMuted, fontSize = 14.sp)
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(it, color = androidx.compose.ui.graphics.Color(0xFFE57373), fontSize = 13.sp) }
            Button(
                onClick = {
                    scope.launch {
                        busy = true
                        error = runCatching { repository.signIn(email.trim(), password) }.exceptionOrNull()?.message
                        if (error == null) password = ""
                        busy = false
                    }
                },
                enabled = !busy && email.isNotBlank() && password.isNotEmpty(),
            ) { Text(if (busy) "Signing in…" else "Sign in") }
        }
    }
}

@Composable
private fun AddonsCard(repository: Repository) {
    val account by repository.account.collectAsState()
    val addons by repository.addons.collectAsState()
    Card {
        SectionTitle("Addons")
        Text(
            if (account == null) "Built-in addons until you sign in. Manage addons in Stremio for now."
            else "From your Stremio account. Manage them in Stremio for now.",
            color = LartColors.InkMuted,
            fontSize = 13.sp,
        )
        addons.forEach { addon ->
            Column(Modifier.padding(vertical = 4.dp)) {
                Text(addon.manifest.name, fontSize = 15.sp, color = LartColors.Ink)
                Text(
                    listOfNotNull(addon.manifest.version.takeIf { it.isNotBlank() }, addon.manifest.types.joinToString(", ")).joinToString("  ·  "),
                    fontSize = 12.sp,
                    color = LartColors.InkSubtle,
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontSize = 15.sp, color = LartColors.Ink)
            Text(body, fontSize = 12.sp, color = LartColors.InkSubtle)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipRow(options: List<Pair<T, String>>, selected: T, onPick: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = value == selected,
                onClick = { onPick(value) },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = androidx.compose.ui.graphics.Color.White,
                    selectedLabelColor = androidx.compose.ui.graphics.Color.Black,
                ),
            )
        }
    }
}

@Composable
private fun PlaybackPage(repository: Repository) {
    val autoPlay by repository.autoPlay.collectAsState()
    val prefs by repository.playerPrefs.collectAsState()
    Card {
        SectionTitle("Player")
        Text(
            "Lart plays with mpv, like Harbor's desktop player: styled subtitles, software decoding when the phone can't, and Anime4K. Phones where mpv can't start use Android's Media3 player instead.",
            color = LartColors.InkMuted,
            fontSize = 13.sp,
        )
    }
    Card {
        SectionTitle("When you press Play")
        SwitchRow(
            "Auto-play the best source",
            "Skips the list of sources. Lart ranks them by quality, size and whether your debrid has them cached.",
            autoPlay,
            repository::setAutoPlay,
        )
        SwitchRow(
            "Skip intros automatically",
            "When IntroDB, TheIntroDB, SkipDB or AniSkip know the episode.",
            prefs.autoSkipIntro,
        ) { v -> repository.updatePlayerPrefs { it.copy(autoSkipIntro = v) } }
    }
    Card {
        SectionTitle("Between episodes")
        Text("Ask \"Are you still watching?\" after", color = LartColors.Ink, fontSize = 15.sp)
        ChipRow(listOf(0 to "Never", 2 to "2 episodes", 3 to "3 episodes", 5 to "5 episodes"), prefs.stillWatchingAfter) { n ->
            repository.updatePlayerPrefs { it.copy(stillWatchingAfter = n) }
        }
    }
    Card {
        SectionTitle("Anime4K")
        Text(
            "Upscaling and line restoration for anime, on by default for every video once chosen. You can also change it in the player.",
            color = LartColors.InkMuted,
            fontSize = 13.sp,
        )
        ChipRow(listOf("" to "Off") + Anime4K.Mode.entries.map { it.name to it.label }, prefs.anime4kMode) { mode ->
            repository.updatePlayerPrefs { it.copy(anime4kMode = mode) }
        }
        ChipRow(listOf(false to "Fast", true to "HQ"), prefs.anime4kHq) { hq ->
            repository.updatePlayerPrefs { it.copy(anime4kHq = hq) }
        }
    }
}

@Composable
private fun LanguagesPage(repository: Repository) {
    val prefs by repository.playerPrefs.collectAsState()
    val device = Locale.getDefault().language
    Card {
        SectionTitle("Subtitle language")
        Text(
            "Turns subtitles on in this language, from the video or your subtitle addons. Picking a subtitle in the player changes it too.",
            color = LartColors.InkMuted,
            fontSize = 13.sp,
        )
        val languages = (listOf("", device, "en", "es", "fr", "de", "it", "pt", "ar", "ja", "ko", "zh", "ru", "tr", "hi")).distinct()
        ChipRow(languages.map { it to if (it.isEmpty()) "Off" else languageName(it) }, prefs.subLanguage) { lang ->
            repository.updatePlayerPrefs { it.copy(subLanguage = lang) }
        }
    }
    Card {
        SectionTitle("Subtitle style")
        Box(Modifier.fillMaxWidth().height(90.dp).clip(RoundedCornerShape(12.dp)).background(androidx.compose.ui.graphics.Color(0xFF3A4048)), contentAlignment = Alignment.Center) {
            SubtitleText("The quick brown fox", prefs)
        }
        Text("Size", color = LartColors.Ink, fontSize = 14.sp)
        Slider(value = prefs.subScale, onValueChange = { v -> repository.updatePlayerPrefs { it.copy(subScale = v) } }, valueRange = 0.6f..1.8f)
        Text("Colour", color = LartColors.Ink, fontSize = 14.sp)
        ChipRow(listOf(0xFFFFFFFF to "White", 0xFFFFE66D to "Yellow", 0xFF8BE9FD to "Cyan", 0xFFB8F5A0 to "Green"), prefs.subColor) { c ->
            repository.updatePlayerPrefs { it.copy(subColor = c) }
        }
        Text("Background", color = LartColors.Ink, fontSize = 14.sp)
        ChipRow(listOf("outline" to "Outline", "shadow" to "Shadow", "box" to "Box"), prefs.subBackground) { b ->
            repository.updatePlayerPrefs { it.copy(subBackground = b) }
        }
        Text("Position", color = LartColors.Ink, fontSize = 14.sp)
        Slider(value = prefs.subBottom, onValueChange = { v -> repository.updatePlayerPrefs { it.copy(subBottom = v) } }, valueRange = 0.02f..0.4f)
    }
}

@Composable
private fun AboutCard() {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() }
    Card {
        SectionTitle("Lart native")
        Text(
            "Version ${version ?: "?"}. A native rebuild of Lart (a Harbor fork) for Android, installed beside the regular Lart app while features are rebuilt.",
            color = LartColors.InkMuted,
            fontSize = 13.sp,
        )
        TextButton(onClick = { uriHandler.openUri("https://github.com/9vsv6/lart") }) { Text("Source code on GitHub") }
    }
    Card {
        SectionTitle("Licences")
        Text(
            "Lart includes mpv (libmpv-android, MIT; mpv LGPL), FFmpeg (LGPL/GPL), Jellyfin's Media3 FFmpeg decoder (GPL-3.0) and downloads Anime4K shaders (MIT) on first use. " +
                "Because of the GPL parts, anyone you share this APK with is entitled to Lart's source code.",
            color = LartColors.InkMuted,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun DebridCard(repository: Repository) {
    val debrid by repository.debrid.collectAsState()
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var service by remember { mutableStateOf(DebridService.RealDebrid) }
    var key by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Card {
        SectionTitle("Debrid")
        val current = debrid
        if (current != null) {
            Text("${current.service.label} is set up. Torrent sources play through it.", color = LartColors.InkMuted, fontSize = 14.sp)
            message?.let { Text(it, color = LartColors.InkSubtle, fontSize = 13.sp) }
            OutlinedButton(onClick = { scope.launch { repository.clearDebrid(); message = null } }) { Text("Remove") }
        } else {
            Text(
                "Torrent sources need a debrid service. Paste your API key; it stays on this device.",
                color = LartColors.InkMuted,
                fontSize = 14.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DebridService.entries.forEach { option ->
                    FilterChip(
                        selected = option == service,
                        onClick = { service = option },
                        label = { Text(option.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = androidx.compose.ui.graphics.Color.White,
                            selectedLabelColor = androidx.compose.ui.graphics.Color.Black,
                        ),
                    )
                }
            }
            OutlinedTextField(
                value = key,
                onValueChange = { key = it.trim() },
                label = { Text("${service.label} API key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            message?.let { Text(it, color = androidx.compose.ui.graphics.Color(0xFFE57373), fontSize = 13.sp) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            busy = true
                            message = runCatching { repository.setDebrid(DebridAccount(service, key)) }
                                .fold(onSuccess = { "Signed in as $it" }, onFailure = { "That key didn't work: ${it.message}" })
                            if (repository.debrid.value != null) key = ""
                            busy = false
                        }
                    },
                    enabled = !busy && key.isNotBlank(),
                ) { Text(if (busy) "Checking…" else "Save") }
                TextButton(onClick = { uriHandler.openUri(service.tokenPage) }) { Text("Get my key") }
            }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(LartColors.Surface, RoundedCornerShape(18.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

/** A card for one pasted API key: checks it on save, and shows Remove once set. */
@Composable
fun KeyCard(
    title: String,
    body: String,
    label: String,
    current: String?,
    linkLabel: String,
    link: String,
    onSave: suspend (String?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var key by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Card {
        SectionTitle(title)
        Text(body, color = LartColors.InkMuted, fontSize = 13.sp)
        if (current != null) {
            Text("Key saved (ends …${current.takeLast(4)}).", color = LartColors.InkSubtle, fontSize = 13.sp)
            OutlinedButton(onClick = { scope.launch { onSave(null) } }) { Text("Remove") }
        } else {
            OutlinedTextField(
                value = key,
                onValueChange = { key = it.trim() },
                label = { Text(label) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(it, color = androidx.compose.ui.graphics.Color(0xFFE57373), fontSize = 13.sp) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            busy = true
                            error = runCatching { onSave(key) }.exceptionOrNull()?.let { "That key didn't work: ${it.message?.take(120)}" }
                            if (error == null) key = ""
                            busy = false
                        }
                    },
                    enabled = !busy && key.isNotBlank(),
                ) { Text(if (busy) "Checking…" else "Save") }
                TextButton(onClick = { uriHandler.openUri(link) }) { Text(linkLabel) }
            }
        }
    }
}
