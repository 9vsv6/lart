package app.lart.mobile.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lart.mobile.data.AppSettings
import app.lart.mobile.data.DebridAccount
import app.lart.mobile.data.DebridService
import app.lart.mobile.data.Repository
import app.lart.mobile.data.StreamFilter
import app.lart.mobile.player.Anime4K
import app.lart.mobile.player.SubtitleText
import app.lart.mobile.player.languageName
import coil.imageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/** Harbor's settings groups (`src/views/settings/groups.ts`), the ones Lart native has. */
private enum class SettingsPage(val label: String, val sub: String, val icon: ImageVector) {
    Account("Account & setup", "Your Stremio sign-in, profiles, and the trackers that follow what you watch.", Icons.Rounded.ManageAccounts),
    Playback("Playback", "How video plays, what happens between episodes, mpv and Anime4K.", Icons.Rounded.PlayCircle),
    Languages("Languages & subtitles", "Audio and subtitle languages, how subtitles look, and the language of details.", Icons.Rounded.Translate),
    Sources("Sources", "Addons, debrid for torrents, and which sources show up and in what order.", Icons.Rounded.Tune),
    Library("Library & metadata", "TMDB and RPDB, Home rows, and episodes.", Icons.Rounded.VideoLibrary),
    Appearance("Appearance", "Accent colour and poster cards.", Icons.Rounded.Palette),
    System("System", "Storage, backup and restore, and resetting settings.", Icons.Rounded.Storage),
    Help("Help & about", "This build, reporting a problem, and licences.", Icons.AutoMirrored.Rounded.HelpOutline),
}

private val LANGUAGE_CODES = listOf("en", "es", "fr", "de", "it", "pt", "ar", "ja", "ko", "zh", "ru", "tr", "hi", "nl", "pl", "sv", "he", "id", "vi", "th")

@Composable
fun SettingsScreen(repository: Repository, onOpenAddons: () -> Unit) {
    var page by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    BackHandler(enabled = page != null) { page = null }

    Column(Modifier.fillMaxSize().background(LartColors.Canvas).statusBarsPadding()) {
        val current = page
        if (current == null) {
            SettingsHome(repository, onOpenAddons) { page = it }
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
                val settings by repository.settings.collectAsState()
                val set: ((AppSettings) -> AppSettings) -> Unit = repository::updateSettings
                when (current) {
                    SettingsPage.Account -> AccountPage(repository, settings, set)
                    SettingsPage.Playback -> PlaybackPage(repository, settings, set)
                    SettingsPage.Languages -> LanguagesPage(repository, settings, set)
                    SettingsPage.Sources -> SourcesPage(repository, settings, set, onOpenAddons)
                    SettingsPage.Library -> LibraryPage(repository, settings, set)
                    SettingsPage.Appearance -> AppearancePage(settings, set)
                    SettingsPage.System -> SystemPage(repository, set)
                    SettingsPage.Help -> AboutCard()
                }
            }
        }
    }
}

/** The rail Harbor shows on phones: who you are, two shortcuts, then the groups. */
@Composable
private fun SettingsHome(repository: Repository, onOpenAddons: () -> Unit, onOpen: (SettingsPage) -> Unit) {
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
            RailRow(Icons.Rounded.Extension, "Addons", null, onOpenAddons)
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

// ---------------- Building blocks ----------------

@Composable
private fun SwitchRow(title: String, body: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontSize = 15.sp, color = LartColors.Ink)
            body?.let { Text(it, fontSize = 12.sp, color = LartColors.InkSubtle) }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Label(title: String, body: String? = null) {
    Column {
        Text(title, fontSize = 15.sp, color = LartColors.Ink)
        body?.let { Text(it, fontSize = 12.sp, color = LartColors.InkSubtle) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipRow(options: List<Pair<T, String>>, selected: T, onPick: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) -> Chip(label, value == selected) { onPick(value) } }
    }
}

/** Several can be on at once; `selected` keeps the order they were picked in. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> MultiChipRow(options: List<Pair<T, String>>, selected: Collection<T>, onChange: (List<T>) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val on = value in selected
            val position = selected.indexOf(value)
            Chip(if (on && selected.size > 1 && selected is List<*>) "${position + 1}. $label" else label, on) {
                onChange(if (on) selected.filter { it != value } else selected.toList() + value)
            }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color.White, selectedLabelColor = Color.Black),
    )
}

private fun languageOptions(extra: List<String> = emptyList()): List<Pair<String, String>> {
    val device = Locale.getDefault().language
    return (listOf(device) + LANGUAGE_CODES + extra).distinct().map { it to languageName(it) }
}

// ---------------- Account & setup ----------------

@Composable
private fun AccountPage(repository: Repository, settings: AppSettings, set: ((AppSettings) -> AppSettings) -> Unit) {
    StremioCard(repository)
    ProfilesCard(repository)
    Card {
        SwitchRow(
            "Ask \"Who's watching?\" when Lart opens",
            "With more than one profile. Off opens the last profile used.",
            settings.askProfileOnLaunch,
        ) { v -> set { it.copy(askProfileOnLaunch = v) } }
    }
    TrackersCard(repository)
    Card {
        SwitchRow(
            "Trakt's paused titles in Continue watching",
            "Shows what you paused on other devices, as Harbor's \"Paused on Trakt\".",
            settings.traktInContinueWatching,
        ) { v -> set { it.copy(traktInContinueWatching = v) } }
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
            error?.let { Text(it, color = Color(0xFFE57373), fontSize = 13.sp) }
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

// ---------------- Playback ----------------

@Composable
private fun PlaybackPage(repository: Repository, settings: AppSettings, set: ((AppSettings) -> AppSettings) -> Unit) {
    val autoPlay by repository.autoPlay.collectAsState()
    val prefs by repository.playerPrefs.collectAsState()

    Card {
        SectionTitle("Player engine")
        ChipRow(listOf("mpv" to "mpv", "media3" to "Android (Media3)"), settings.engine) { v -> set { it.copy(engine = v) } }
        Text(
            if (settings.engine == "mpv") "Harbor's player: styled subtitles, software decoding when the phone can't, Anime4K. If mpv can't start, Lart uses Media3."
            else "Android's own player: lightest on battery. No Anime4K; Lart draws the subtitles.",
            color = LartColors.InkSubtle,
            fontSize = 12.sp,
        )
    }

    Card {
        SectionTitle("When you press Play")
        SwitchRow("Instant play", "Skips the list of sources and plays the best one.", autoPlay, repository::setAutoPlay)
        SwitchRow("Remember the source you picked", "Marks it next time, and instant play prefers it.", settings.rememberLastStream) { v -> set { it.copy(rememberLastStream = v) } }
        SwitchRow("Resume where you left off", "Off starts every video from the beginning.", settings.resumePlayback) { v -> set { it.copy(resumePlayback = v) } }
        SwitchRow("Show \"Resumed at\" with Start over", null, settings.resumeNotice) { v -> set { it.copy(resumeNotice = v) } }
        SwitchRow("Switch source when a stream stalls", "Moves to the next source after it buffers this long.", settings.autoNextStreamOnStall) { v -> set { it.copy(autoNextStreamOnStall = v) } }
        if (settings.autoNextStreamOnStall) {
            ChipRow(listOf(5, 10, 15, 30).map { it to "$it s" }, settings.stallSec) { v -> set { it.copy(stallSec = v) } }
        }
    }

    Card {
        SectionTitle("On-screen controls")
        Label("Rewind step")
        ChipRow(listOf(5, 10, 15, 30).map { it to "$it s" }, settings.seekBackSec) { v -> set { it.copy(seekBackSec = v) } }
        Label("Forward step")
        ChipRow(listOf(5, 10, 15, 30, 60).map { it to "$it s" }, settings.seekForwardSec) { v -> set { it.copy(seekForwardSec = v) } }
        Label("Aspect ratio", "How the picture fills the screen when a video starts.")
        ChipRow(listOf("Fit" to "Fit", "Fill" to "Fill", "Stretch" to "Stretch"), settings.defaultFit) { v -> set { it.copy(defaultFit = v) } }
        Label("Default speed")
        ChipRow(settings.playbackSpeeds.sorted().map { it to speedLabel(it) }, prefs.speed) { v -> repository.updatePlayerPrefs { it.copy(speed = v) } }
        Label("Speeds in the menu")
        MultiChipRow(listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f).map { it to speedLabel(it) }, settings.playbackSpeeds.toSet()) { v ->
            set { it.copy(playbackSpeeds = (v + 1f).distinct().sorted()) }
        }
        Label("Sleep timer choices")
        MultiChipRow(listOf(10, 15, 20, 30, 45, 60, 90, 120).map { it to "$it min" }, settings.sleepMinutes.toSet()) { v ->
            set { it.copy(sleepMinutes = v.sorted()) }
        }
        SwitchRow("Screen lock button", "Locks the controls so a pocket or a child can't tap them.", settings.screenLock) { v -> set { it.copy(screenLock = v) } }
    }

    Card {
        SectionTitle("Up next")
        SwitchRow("Auto-play next episode", "When an episode ends.", settings.autoPlayNextEpisode) { v -> set { it.copy(autoPlayNextEpisode = v) } }
        Label("Show Up next", "\"At the credits\" uses the intro databases' credit times when they have them.")
        ChipRow(listOf(0 to "At the credits", 30 to "30 s before", 45 to "45 s before", 60 to "1 min before", 90 to "90 s before"), settings.nextEpisodeLeadSec) { v ->
            set { it.copy(nextEpisodeLeadSec = v) }
        }
        SwitchRow("Keep the same source for the next episode", "Prefers the addon the current episode came from.", settings.keepSourceNextEpisode) { v -> set { it.copy(keepSourceNextEpisode = v) } }
        Label("Ask if you're still watching after")
        ChipRow(listOf(0 to "Never", 2 to "2 episodes", 3 to "3 episodes", 5 to "5 episodes"), prefs.stillWatchingAfter) { n ->
            repository.updatePlayerPrefs { it.copy(stillWatchingAfter = n) }
        }
    }

    Card {
        SectionTitle("Intros & credits")
        Text("From IntroDB, TheIntroDB, SkipDB and AniSkip, when they know the episode.", color = LartColors.InkSubtle, fontSize = 12.sp)
        SwitchRow("Show the Skip button", null, settings.showSkipButton) { v -> set { it.copy(showSkipButton = v) } }
        if (settings.showSkipButton) {
            Label("Auto-hide the Skip button after")
            ChipRow(listOf(0 to "Never", 5 to "5 s", 10 to "10 s", 15 to "15 s"), settings.skipButtonHideSec) { v -> set { it.copy(skipButtonHideSec = v) } }
        }
        SwitchRow("Auto-skip intros", null, prefs.autoSkipIntro) { v -> repository.updatePlayerPrefs { it.copy(autoSkipIntro = v) } }
        SwitchRow("Auto-skip recaps", null, settings.autoSkipRecap) { v -> set { it.copy(autoSkipRecap = v) } }
        SwitchRow("Auto-skip credit outros", "Goes straight to the next episode, or to the end of a movie.", settings.autoSkipOutro) { v -> set { it.copy(autoSkipOutro = v) } }
    }

    if (settings.engine == "mpv") {
        Card {
            SectionTitle("mpv")
            Label("Decoding")
            ChipRow(listOf("auto" to "Auto", "on" to "Hardware only", "off" to "Software"), settings.mpvHwdec) { v -> set { it.copy(mpvHwdec = v) } }
            Label("Playback quality", "Quality uses sharper scaling and debanding; Performance saves battery.")
            ChipRow(listOf("performance" to "Performance", "balanced" to "Balanced", "quality" to "Quality"), settings.mpvQuality) { v -> set { it.copy(mpvQuality = v) } }
            Label("Buffer")
            ChipRow(listOf("small" to "Small", "auto" to "Normal", "large" to "Large"), settings.bufferSize) { v -> set { it.copy(bufferSize = v) } }
            SwitchRow("Mix surround sound down to stereo", "Clearer dialogue on phone speakers and headphones.", settings.downmixStereo) { v -> set { it.copy(downmixStereo = v) } }
            SwitchRow("Normalize loudness", "Evens out quiet dialogue and loud scenes.", settings.audioNormalize) { v -> set { it.copy(audioNormalize = v) } }
            Label("Sound profile")
            ChipRow(listOf("off" to "Off", "bass" to "Bass boost", "voice" to "Voice", "bass-reduce" to "Less bass", "night" to "Night"), settings.audioProfile) { v ->
                set { it.copy(audioProfile = v) }
            }
            Label("Maximum volume boost", "Swipe up past full volume on the right edge to go louder.")
            ChipRow(listOf(1 to "Off", 2 to "200%", 3 to "300%"), settings.volumeBoostMax) { v -> set { it.copy(volumeBoostMax = v) } }
            MpvOptionsField(settings.mpvExtraOptions) { v -> set { it.copy(mpvExtraOptions = v) } }
        }
        Card {
            SectionTitle("Anime4K")
            Text("Upscaling and line restoration for anime. You can also change it in the player.", color = LartColors.InkMuted, fontSize = 13.sp)
            ChipRow(listOf("" to "Off") + Anime4K.Mode.entries.map { it.name to it.label }, prefs.anime4kMode) { mode ->
                repository.updatePlayerPrefs { it.copy(anime4kMode = mode) }
            }
            ChipRow(listOf(false to "Fast", true to "HQ"), prefs.anime4kHq) { hq -> repository.updatePlayerPrefs { it.copy(anime4kHq = hq) } }
            SwitchRow("Anime only", "Leaves live-action films and series untouched.", settings.anime4kAnimeOnly) { v -> set { it.copy(anime4kAnimeOnly = v) } }
            SwitchRow("Show an Anime4K badge while playing", null, settings.anime4kIndicator) { v -> set { it.copy(anime4kIndicator = v) } }
        }
    }
}

private fun speedLabel(speed: Float) = speed.toString().removeSuffix(".0") + "×"

@Composable
private fun MpvOptionsField(saved: String, onSave: (String) -> Unit) {
    var text by remember(saved) { mutableStateOf(saved) }
    Label("Extra mpv options", "One per line, as key=value (for example deband=yes). Applied when a video starts.")
    OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(), minLines = 3)
    if (text != saved) TextButton(onClick = { onSave(text.trim()) }) { Text("Save options") }
}

// ---------------- Languages & subtitles ----------------

@Composable
private fun LanguagesPage(repository: Repository, settings: AppSettings, set: ((AppSettings) -> AppSettings) -> Unit) {
    val prefs by repository.playerPrefs.collectAsState()
    Card {
        SectionTitle("Audio")
        Label("Audio languages", "In order: the first one a video has is picked. None picked follows the device.")
        MultiChipRow(languageOptions(), settings.audioLanguages) { v -> set { it.copy(audioLanguages = v) } }
    }
    Card {
        SectionTitle("Subtitles")
        SwitchRow("Subtitles off by default", "Turn them on yourself in the player.", settings.subtitlesOffByDefault) { v -> set { it.copy(subtitlesOffByDefault = v) } }
        if (!settings.subtitlesOffByDefault) {
            Label("Subtitle language", "Turns subtitles on in this language. Picking one in the player changes it too.")
            ChipRow(listOf("" to "None") + languageOptions(), prefs.subLanguage) { lang -> repository.updatePlayerPrefs { it.copy(subLanguage = lang) } }
            Label("If that isn't available, try")
            MultiChipRow(languageOptions(), settings.subtitleFallbacks) { v -> set { it.copy(subtitleFallbacks = v) } }
        }
        SwitchRow("Prefer subtitles inside the video", "They're timed for that exact file; addon ones may need syncing.", settings.preferEmbeddedSubs) { v -> set { it.copy(preferEmbeddedSubs = v) } }
        SwitchRow("Skip hearing-impaired (SDH) subtitles", "When picking automatically.", settings.hideSdh) { v -> set { it.copy(hideSdh = v) } }
        SwitchRow("Use subtitle addons", "OpenSubtitles and others you've installed.", settings.subtitleAddons) { v -> set { it.copy(subtitleAddons = v) } }
        if (settings.engine == "mpv") {
            Label("Second subtitle", "Shown at the top at the same time, for learning a language (mpv).")
            ChipRow(listOf("" to "Off") + languageOptions(), settings.secondarySubLanguage) { v -> set { it.copy(secondarySubLanguage = v) } }
        }
    }
    Card {
        SectionTitle("Subtitle style")
        Box(Modifier.fillMaxWidth().height(90.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF3A4048)), contentAlignment = Alignment.Center) {
            SubtitleText("The quick brown fox", prefs, settings)
        }
        Label("Size")
        Slider(value = prefs.subScale, onValueChange = { v -> repository.updatePlayerPrefs { it.copy(subScale = v) } }, valueRange = 0.6f..1.8f)
        Label("Colour")
        ChipRow(listOf(0xFFFFFFFF to "White", 0xFFFFE66D to "Yellow", 0xFF8BE9FD to "Cyan", 0xFFB8F5A0 to "Green"), prefs.subColor) { c ->
            repository.updatePlayerPrefs { it.copy(subColor = c) }
        }
        Label("Background")
        ChipRow(listOf("outline" to "Outline", "shadow" to "Shadow", "box" to "Box"), prefs.subBackground) { b -> repository.updatePlayerPrefs { it.copy(subBackground = b) } }
        if (prefs.subBackground == "box") {
            Label("Box opacity")
            Slider(value = settings.subBoxOpacity, onValueChange = { v -> set { it.copy(subBoxOpacity = v) } }, valueRange = 0.2f..1f)
        }
        SwitchRow("Bold", null, settings.subBold) { v -> set { it.copy(subBold = v) } }
        Label("Alignment")
        ChipRow(listOf("left" to "Left", "center" to "Centre", "right" to "Right"), settings.subAlignX) { v -> set { it.copy(subAlignX = v) } }
        Label("Position")
        Slider(value = prefs.subBottom, onValueChange = { v -> repository.updatePlayerPrefs { it.copy(subBottom = v) } }, valueRange = 0.02f..0.4f)
        if (settings.engine == "mpv") {
            Label("Styled (ASS) subtitles", "Anime subtitles often carry their own fonts and colours.")
            ChipRow(listOf("no" to "Keep their style", "scale" to "Keep style, my size", "force" to "Use my style"), settings.assOverride) { v ->
                set { it.copy(assOverride = v) }
            }
        }
    }
    Card {
        SectionTitle("Details language")
        Text("For titles and descriptions from TMDB (needs a TMDB key in Library & metadata).", color = LartColors.InkSubtle, fontSize = 12.sp)
        ChipRow(listOf("" to "Device") + languageOptions(), settings.metadataLanguage) { v -> set { it.copy(metadataLanguage = v) } }
    }
}

// ---------------- Sources ----------------

@Composable
private fun SourcesPage(repository: Repository, settings: AppSettings, set: ((AppSettings) -> AppSettings) -> Unit, onOpenAddons: () -> Unit) {
    val addons by repository.addons.collectAsState()
    Card {
        SectionTitle("Addons")
        Text("${addons.size} installed: ${addons.joinToString(", ") { it.manifest.name }}", color = LartColors.InkMuted, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Button(onClick = onOpenAddons) { Text("Manage addons") }
    }
    DebridCard(repository)
    Card {
        SectionTitle("Source filter")
        Text("Only sources that match show up. Empty rows mean any.", color = LartColors.InkSubtle, fontSize = 12.sp)
        val filter = settings.streamFilter
        fun update(change: (StreamFilter) -> StreamFilter) = set { it.copy(streamFilter = change(it.streamFilter)) }
        Label("Resolution")
        MultiChipRow(listOf(2160 to "4K", 1080 to "1080p", 720 to "720p", 480 to "SD"), filter.resolutions) { v -> update { it.copy(resolutions = v.toSet()) } }
        Label("Release")
        MultiChipRow(listOf("remux" to "Remux", "bluray" to "Blu-ray", "web" to "WEB"), filter.sources) { v -> update { it.copy(sources = v.toSet()) } }
        Label("Video codec")
        MultiChipRow(listOf("hevc" to "HEVC", "av1" to "AV1", "h264" to "H.264"), filter.codecs) { v -> update { it.copy(codecs = v.toSet()) } }
        SwitchRow("HDR only", null, filter.requireHdr) { v -> update { it.copy(requireHdr = v) } }
        SwitchRow("Cached on debrid only", "Hides torrents your debrid service doesn't already have.", filter.cachedOnly) { v -> update { it.copy(cachedOnly = v) } }
        Label("Largest file")
        ChipRow(listOf(0 to "Any", 2 to "2 GB", 5 to "5 GB", 10 to "10 GB", 20 to "20 GB", 40 to "40 GB"), filter.maxSizeGb) { v -> update { it.copy(maxSizeGb = v) } }
        if (filter.active) TextButton(onClick = { update { StreamFilter() } }) { Text("Clear the filter") }
    }
    Card {
        SectionTitle("Safety & order")
        Label("Stream safety filter", "Balanced hides CAM and telesync rips; Strict also hides tiny files, usually fakes.")
        ChipRow(listOf("off" to "Off", "balanced" to "Balanced", "strict" to "Strict"), settings.safetyFilter) { v -> set { it.copy(safetyFilter = v) } }
        Label("Order")
        ChipRow(listOf("lart" to "Best first", "addon" to "As the addons list them"), settings.streamSort) { v -> set { it.copy(streamSort = v) } }
        Label("Preferred languages", "Sources that mention these languages rank higher.")
        MultiChipRow(
            listOf("English", "Spanish", "French", "German", "Italian", "Portuguese", "Hindi", "Japanese", "Korean", "Russian", "Arabic", "Turkish", "Multi").map { it to it },
            settings.streamLanguages,
        ) { v -> set { it.copy(streamLanguages = v) } }
        if (settings.streamLanguages.isNotEmpty()) {
            SwitchRow("Only show those languages", null, settings.requireStreamLanguage) { v -> set { it.copy(requireStreamLanguage = v) } }
        }
        SwitchRow("No torrents", "Hide torrent sources, even with debrid.", settings.torrentsDisabled) { v -> set { it.copy(torrentsDisabled = v) } }
        SwitchRow("Show file names", "Under each source in the list.", settings.showFilename) { v -> set { it.copy(showFilename = v) } }
        Label("Addon timeout", "How long to wait for a slow addon before showing the rest.")
        ChipRow(listOf(10 to "10 s", 20 to "20 s", 30 to "30 s", 60 to "60 s"), settings.addonTimeoutSec) { v -> set { it.copy(addonTimeoutSec = v) } }
    }
}

// ---------------- Library & metadata ----------------

@Composable
private fun LibraryPage(repository: Repository, settings: AppSettings, set: ((AppSettings) -> AppSettings) -> Unit) {
    KeyCard(
        title = "TMDB",
        body = "With your own TMDB API key, Home, Movies and Shows use TMDB's rows, artwork, logos and cast, in your language, as in Harbor.",
        label = "TMDB API key or read token",
        current = repository.tmdbKey.collectAsState().value,
        linkLabel = "Get a key",
        link = "https://www.themoviedb.org/settings/api",
        onSave = { repository.setTmdbKey(it) },
    )
    KeyCard(
        title = "RPDB posters",
        body = "With a RatingPosterDB key, posters show IMDb and other ratings drawn on them.",
        label = "RPDB API key",
        current = repository.rpdbKey.collectAsState().value,
        linkLabel = "Get a key",
        link = "https://ratingposterdb.com",
        onSave = { repository.setRpdbKey(it) },
    )
    Card {
        SectionTitle("Home")
        SwitchRow("Top 10 row", null, settings.showTop10) { v -> set { it.copy(showTop10 = v) } }
        SwitchRow("Rotate the hero", "Moves to the next featured title every few seconds.", settings.heroAutoRotate) { v -> set { it.copy(heroAutoRotate = v) } }
        SwitchRow("Hide unreleased titles", null, settings.hideUnreleased) { v -> set { it.copy(hideUnreleased = v) } }
        SwitchRow("Hide anime", "Removes the Anime view and anime rows.", settings.hideAnime) { v -> set { it.copy(hideAnime = v) } }
    }
    Card {
        SectionTitle("Episodes")
        Label("Order")
        ChipRow(listOf(false to "Oldest first", true to "Newest first"), settings.episodesNewestFirst) { v -> set { it.copy(episodesNewestFirst = v) } }
        SwitchRow("Show episode descriptions", null, settings.showEpisodeDescription) { v -> set { it.copy(showEpisodeDescription = v) } }
        SwitchRow("Hide spoilers", "For episodes after the one you're on.", settings.hideSpoilers) { v -> set { it.copy(hideSpoilers = v) } }
        if (settings.hideSpoilers) {
            SwitchRow("Blur thumbnails", null, settings.spoilerThumbnails) { v -> set { it.copy(spoilerThumbnails = v) } }
            SwitchRow("Hide titles", null, settings.spoilerTitles) { v -> set { it.copy(spoilerTitles = v) } }
            SwitchRow("Hide descriptions", null, settings.spoilerDescriptions) { v -> set { it.copy(spoilerDescriptions = v) } }
        }
    }
}

// ---------------- Appearance ----------------

private val ACCENTS = listOf(0xFFF0A35E, 0xFFE57373, 0xFFF06292, 0xFFBA68C8, 0xFF7986CB, 0xFF4FC3F7, 0xFF4DB6AC, 0xFF81C784, 0xFFDCE775, 0xFFFFD54F)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearancePage(settings: AppSettings, set: ((AppSettings) -> AppSettings) -> Unit) {
    Card {
        SectionTitle("Accent colour")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ACCENTS.forEach { argb ->
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Color(argb.toInt()))
                        .border(if (settings.accent == argb) 3.dp else 0.dp, Color.White, CircleShape)
                        .clickable { set { it.copy(accent = argb) } },
                    contentAlignment = Alignment.Center,
                ) { if (settings.accent == argb) Icon(Icons.Rounded.Check, "Selected", tint = Color.Black) }
            }
        }
    }
    Card {
        SectionTitle("Poster cards")
        SwitchRow("Rating badge", "IMDb rating in the corner of each poster.", settings.showRatingBadge) { v -> set { it.copy(showRatingBadge = v) } }
        SwitchRow("Hide titles under posters", null, settings.hidePosterTitles) { v -> set { it.copy(hidePosterTitles = v) } }
        Label("Size")
        ChipRow(listOf(0.85f to "Small", 1f to "Normal", 1.15f to "Large"), settings.posterScale) { v -> set { it.copy(posterScale = v) } }
        Label("Corners")
        ChipRow(listOf(0 to "Square", 4 to "Slight", 8 to "Round", 14 to "Rounder"), settings.posterRadius) { v -> set { it.copy(posterRadius = v) } }
    }
}

// ---------------- System ----------------

@Composable
private fun SystemPage(repository: Repository, set: ((AppSettings) -> AppSettings) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun say(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val json = repository.exportBackup()
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } }
            }.onSuccess { say("Backup saved") }.onFailure { say("Backup failed: ${it.message}") }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val json = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } } ?: error("Couldn't read the file")
                repository.importBackup(json)
            }.onSuccess { say("Backup restored") }.onFailure { say(it.message ?: "Restore failed") }
        }
    }

    Card {
        SectionTitle("Backup & restore")
        Text(
            "Saves your profiles, lists, progress, settings and sign-ins to a file. It holds your account keys, so keep it private.",
            color = LartColors.InkMuted,
            fontSize = 13.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { exporter.launch("lart-backup.json") }) { Text("Back up") }
            OutlinedButton(onClick = { importer.launch(arrayOf("application/json", "*/*")) }) { Text("Restore") }
        }
    }
    Card {
        SectionTitle("Storage")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("Image cache", "Posters and artwork. They download again as needed.")
        }
        OutlinedButton(onClick = {
            context.imageLoader.memoryCache?.clear()
            context.imageLoader.diskCache?.clear()
            say("Image cache cleared")
        }) { Text("Clear image cache") }
        Label("Anime4K shaders", "They download again the next time Anime4K is on.")
        OutlinedButton(onClick = {
            File(context.filesDir, "anime4k").deleteRecursively()
            say("Anime4K shaders removed")
        }) { Text("Remove shaders") }
        Label("Watch positions on this device", "For this profile. Stremio's and Trakt's stay.")
        OutlinedButton(onClick = {
            repository.clearResumeHistory()
            say("Watch positions cleared")
        }) { Text("Clear watch positions") }
    }
    Card {
        SectionTitle("Reset")
        Text("Puts this profile's settings back to how Lart starts. Sign-ins, lists and progress stay.", color = LartColors.InkMuted, fontSize = 13.sp)
        OutlinedButton(onClick = {
            set { AppSettings() }
            say("Settings reset")
        }) { Text("Reset settings") }
    }
}

// ---------------- Help & about ----------------

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
        Row {
            TextButton(onClick = { uriHandler.openUri("https://github.com/9vsv6/lart/issues/new") }) { Text("Report a problem") }
            TextButton(onClick = { uriHandler.openUri("https://github.com/9vsv6/lart") }) { Text("Source code") }
        }
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
