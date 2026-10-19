package app.lart.mobile.ui

import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
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
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.QueuePlayNext
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.SmartScreen
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.AccountBox
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Support
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
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
import app.lart.mobile.data.ProviderKey
import app.lart.mobile.data.Languages
import app.lart.mobile.data.Lang
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

/*
 * Settings, laid out as Harbor's phone settings (src/views/settings/groups.ts and nav.tsx):
 * a home rail of groups, a landing per group listing its sections, and flat section pages
 * with Harbor's header (Back, title, search). Groups and sections Lart native has nothing
 * for yet (Plugins, Controls & devices, relay, webhooks…) are left out.
 */

/**
 * Harbor's settings sub-pages (src/views/settings/tab-registry.ts), the ones Lart native
 * has: each shows one section of the app's settings, or some of its parts.
 */
private enum class Section(
    val label: String,
    val sub: String,
    val icon: ImageVector,
    val keywords: String,
    val parts: Set<String>? = null,
) {
    Profiles("Profiles", "The profiles on this device, and who's watching.", Icons.Outlined.Group, "profile who's watching", setOf("Profiles")),
    Stremio("Stremio", "Your Stremio sign-in: addons and library come from it.", Icons.Outlined.AccountBox, "stremio sign in email password", setOf("Stremio")),
    Trackers("Trackers", "Trakt, Simkl and AniList: scrobbling and progress.", Icons.Outlined.Sync, "trakt simkl anilist scrobble watched"),
    Play("Play", "The player engine, and what happens when you press Play.", Icons.Outlined.PlayCircle, "engine media3 mpv instant resume stall", setOf("Player engine", "When you press Play")),
    OnScreen("On screen", "The player's controls: seek steps, speeds, sleep timer and lock.", Icons.Outlined.SmartScreen, "seek speed sleep lock aspect xray x-ray cast quality badge stream quality title", setOf("On-screen controls")),
    Intros("Intros", "Skip intros, recaps and credits.", Icons.Outlined.FastForward, "skip intro recap credits", setOf("Intros & credits")),
    UpNext("Up next", "What happens between episodes.", Icons.Outlined.QueuePlayNext, "up next autoplay still watching", setOf("Up next")),
    Picture("Picture", "Decoding, picture quality and buffering (mpv).", Icons.Outlined.Image, "hwdec hardware decoding quality buffer", setOf("Video")),
    Audio("Audio", "Downmix, loudness, sound profiles and volume boost (mpv).", Icons.Outlined.VolumeUp, "downmix stereo volume boost loudness sound profile", setOf("Sound")),
    MpvConf("mpv.conf", "Your own mpv options.", Icons.Outlined.Code, "mpv options advanced", setOf("Advanced")),
    Shaders("Anime Shaders", "Anime4K upscaling and line restoration.", Icons.Outlined.AutoAwesome, "anime4k shaders upscale"),
    AppLanguage("App language", "Metadata language, region and translation.", Icons.Outlined.Translate, "display language metadata region translate titles overviews image languages logos", setOf("App language")),
    AudioLanguage("Audio languages", "Which audio track plays, and the tracks never to pick.", Icons.Outlined.GraphicEq, "audio language dub commentary skip tracks", setOf("Audio languages")),
    WhatYouSee("Discovery languages", "Home rows by language, your region, and stream languages.", Icons.Outlined.Visibility, "home catalogs languages region favour stream ranking only show streams", setOf("Discovery languages")),
    SubtitleLanguages("Subtitle languages", "Which subtitles turn on, dual subtitles and choosing a track.", Icons.Outlined.Subtitles, "subtitles languages dual second forced embedded sdh start off choose upgrade indicator", setOf("Subtitle languages")),
    SubtitleSources("Subtitle sources", "SubDL, Subsource, Wyzie, OpenSubtitles and your addons.", Icons.Outlined.Download, "subdl subsource wyzie opensubtitles api key addons sources", setOf("Subtitle sources")),
    SubtitleTiming("Subtitle timing", "The sync indicator and nudging subtitle timing.", Icons.Outlined.Timer, "sync timing delay offset indicator", setOf("Subtitle timing")),
    SubtitleLook("Subtitle style", "Background, outline, colours, font, position and size.", Icons.Outlined.TextFields, "subtitle style size colour color bold position ass font outline background opacity pip", setOf("Subtitle style")),
    Services("Streaming services", "Your addons and debrid service.", Icons.Outlined.Inventory2, "addons debrid real-debrid torbox", setOf("Addons", "Debrid")),
    SourcePreferences("Source preferences", "Safety, order, languages and what shows in the list.", Icons.Outlined.Tune, "order sort safety cam torrents file names timeout languages", setOf("Safety & order")),
    StreamFilters("Stream filters", "Only show sources that match.", Icons.Outlined.FilterList, "filter resolution 4k 1080p remux bluray web hevc av1 hdr cached size"),
    HomeRows("Home", "Rows and the hero on Home.", Icons.Outlined.Home, "home top 10 hero unreleased anime room watched hide catalogs new episodes row", setOf("Home")),
    PosterCards("Poster cards", "What a poster card shows.", Icons.Outlined.GridView, "poster card badge corners size watched check watchlist bookmark", setOf("Poster cards")),
    DetailPages("Detail pages", "Spoilers, episode cards, comments and reviews.", Icons.Outlined.Article, "episodes spoilers order description card size trakt comments letterboxd reviews community show pages full descriptions remember season imdb keyless free data", setOf("Show pages", "Spoilers", "Episode cards", "Community")),
    Metadata("Metadata providers", "Where titles, artwork and scores come from.", Icons.Outlined.Storage, "tmdb omdb tvdb thetvdb mdblist fanart rpdb custom posters ratings keys metadata"),
    Theme("Theme", "Accent colour.", Icons.Outlined.Palette, "accent colour color theme", setOf("Accent colour")),
    Storage("Storage", "Caches, watch positions on this device, and resetting settings.", Icons.Outlined.DeleteSweep, "cache images shaders watch positions reset downloads delete after watching"),
    Backup("Updates & backup", "Updates, and a copy of your setup.", Icons.Outlined.Backup, "backup restore export import file updates"),
    About("About", "This build, reporting a problem, and licences.", Icons.Outlined.Info, "version report bug github licences licenses"),
}

private enum class Group(val label: String, val section: String, val icon: ImageVector, val children: List<Section>) {
    Account("Account & setup", "Setup", Icons.Outlined.AccountBox, listOf(Section.Profiles, Section.Stremio, Section.Trackers)),
    Playback(
        "Playback", "Watching", Icons.Outlined.PlayCircle,
        listOf(Section.Play, Section.OnScreen, Section.Intros, Section.UpNext, Section.Picture, Section.Audio, Section.MpvConf, Section.Shaders),
    ),
    Languages("Languages & subtitles", "Language", Icons.Outlined.Translate, listOf(Section.AppLanguage, Section.AudioLanguage, Section.WhatYouSee, Section.SubtitleLanguages, Section.SubtitleSources, Section.SubtitleTiming, Section.SubtitleLook)),
    Sources(
        "Library & metadata", "Content", Icons.Outlined.LibraryBooks,
        listOf(Section.Services, Section.SourcePreferences, Section.StreamFilters, Section.HomeRows, Section.PosterCards, Section.DetailPages, Section.Metadata),
    ),
    Appearance("Appearance", "Look & feel", Icons.Outlined.Palette, listOf(Section.Theme)),
    System("System", "System", Icons.Outlined.Tune, listOf(Section.Storage, Section.Backup)),
    Help("Help & about", "Help", Icons.Outlined.Support, listOf(Section.About)),
}

private fun groupOf(section: Section) = Group.entries.first { section in it.children }

private val LANGUAGE_CODES = listOf("en", "es", "fr", "de", "it", "pt", "ar", "ja", "ko", "zh", "ru", "tr", "hi", "nl", "pl", "sv", "he", "id", "vi", "th")

@Composable
fun SettingsScreen(repository: Repository, onOpenAddons: () -> Unit, onExit: () -> Unit = {}) {
    // Phones: "" is the home rail, else an open group. Tablets: the open sub-page.
    var groupName by rememberSaveable { mutableStateOf("") }
    var sectionName by rememberSaveable { mutableStateOf(Section.Profiles.name) }
    var query by rememberSaveable { mutableStateOf("") }
    var target by remember { mutableStateOf<Section?>(null) }

    BoxWithConstraints(Modifier.fillMaxSize().background(LartColors.Canvas).statusBarsPadding()) {
        // Tablets get Harbor's desktop layout: the sidebar on the left, the open page beside it.
        val wide = maxWidth >= 720.dp
        val section = Section.valueOf(sectionName)
        val group = if (wide) groupOf(section) else groupName.takeIf { it.isNotEmpty() }?.let { Group.valueOf(it) }
        fun open(g: Group, s: Section? = null) {
            query = ""
            groupName = g.name
            if (wide) sectionName = (s ?: g.children.first()).name
            target = s
        }
        BackHandler(enabled = query.isNotEmpty() || (!wide && group != null)) {
            if (query.isNotEmpty()) query = "" else groupName = ""
        }

        if (wide && group != null) {
            Row(Modifier.fillMaxSize()) {
                Column(
                    Modifier.width(340.dp).fillMaxHeight().background(LartColors.Surface)
                        .verticalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, top = 20.dp, bottom = 32.dp),
                ) {
                    // Harbor's "← Back" out of Settings; the app's own rail is hidden here.
                    Row(
                        Modifier.padding(bottom = 14.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onExit).padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(androidx.compose.material.icons.Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = LartColors.InkMuted, modifier = Modifier.size(18.dp))
                        Text("Back", color = LartColors.InkMuted, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }
                    SettingsSearchField(query) { query = it }
                    if (query.isNotBlank()) SearchResults(query) { open(groupOf(it), it) }
                    else SettingsRail(repository, onOpenAddons, selected = group, selectedSection = section, onOpen = { g, s -> open(g, s) })
                }
                // Harbor's page: the group's title, then the open sub-page, in a readable column.
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState(), enabled = true)
                        .padding(start = 56.dp, end = 40.dp, top = 28.dp, bottom = 48.dp),
                ) {
                    Column(Modifier.widthIn(max = 980.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                        Text(group.label, color = LartColors.Ink, fontSize = 36.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.7).sp, lineHeight = 42.sp)
                        SectionPage(section, repository, onOpenAddons)
                    }
                }
            }
        } else if (group == null) {
            Column(Modifier.fillMaxSize()) {
                SettingsHeader("Settings", null, query) { query = it }
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 40.dp)) {
                    if (query.isNotBlank()) SearchResults(query) { open(groupOf(it), it) }
                    else SettingsRail(repository, onOpenAddons, selected = null, selectedSection = null, onOpen = { g, s -> open(g, s) })
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                SettingsHeader(group.label, { query = ""; groupName = "" }, query) { query = it }
                if (query.isNotBlank()) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                        SearchResults(query) { open(groupOf(it), it) }
                    }
                } else GroupContent(group, repository, onOpenAddons, target, horizontal = 16.dp) { target = null }
            }
        }
    }
}

/**
 * Harbor's rail: who you are, Switch profile and Addons, then every group. On tablets the
 * open group lists its sub-pages with their icons, as Harbor's desktop sidebar does.
 */
@Composable
private fun SettingsRail(repository: Repository, onOpenAddons: () -> Unit, selected: Group?, selectedSection: Section?, onOpen: (Group, Section?) -> Unit) {
    val profile by repository.activeProfile.collectAsState()
    val account by repository.account.collectAsState()
    Column {
        Row(
            Modifier.fillMaxWidth().padding(start = 2.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ProfileAvatar(profile, 58.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(profile?.name ?: "Me", color = LartColors.Ink, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                if (account != null) {
                    Text("Signed in to Stremio", color = LartColors.InkMuted, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else Row {
                    Text("Sign in to ", color = LartColors.InkMuted, fontSize = 15.sp)
                    Text("Stremio", color = LartColors.Ink, fontSize = 15.sp, modifier = Modifier.clickable { onOpen(Group.Account, Section.Stremio) })
                }
            }
        }
        NavRow(Icons.Outlined.Group, "Switch profile", chevron = false) { repository.showProfilePicker() }
        NavRow(Icons.Outlined.Inventory2, "Addons", chevron = false, onClick = onOpenAddons)
        Group.entries.forEach { entry ->
            val on = entry == selected
            NavRow(entry.icon, entry.label, chevron = true, selected = on && selectedSection == null, expanded = on) { onOpen(entry, null) }
            // Harbor's sub-page list: icon and label, the open one highlighted.
            if (on && selectedSection != null) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(LartColors.Elevated.copy(alpha = 0.5f)).padding(6.dp),
                ) {
                    entry.children.forEach { child ->
                        val open = child == selectedSection
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (open) LartColors.Raised else Color.Transparent)
                                .clickable { onOpen(entry, child) }.padding(horizontal = 12.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Icon(child.icon, null, tint = if (open) LartColors.Ink else LartColors.InkMuted, modifier = Modifier.size(20.dp))
                            Text(child.label, color = if (open) LartColors.Ink else LartColors.InkMuted, fontSize = 15.5.sp, fontWeight = if (open) FontWeight.SemiBold else FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResults(query: String, onOpen: (Section) -> Unit) {
    val words = query.lowercase().split(' ').filter { it.isNotBlank() }
    val matches = Section.entries.filter { s ->
        val text = "${s.label} ${s.sub} ${s.keywords} ${groupOf(s).label}".lowercase()
        words.all { it in text }
    }
    Column(Modifier.padding(top = 10.dp)) {
        SettingsLabel(if (matches.isEmpty()) "No matches" else "Results")
        matches.forEach { s -> LinkRow(s.label, "${groupOf(s).label} · ${s.sub}") { onOpen(s) } }
    }
}

/** A tablet sub-page: its title and what it is, then its settings. */
/** Pages ported whole from Harbor: they open with their own headings, as Harbor's do. */
private val SELF_TITLED = setOf(
    Section.AppLanguage, Section.AudioLanguage, Section.WhatYouSee, Section.SubtitleLanguages,
    Section.SubtitleSources, Section.SubtitleTiming, Section.SubtitleLook,
)

@Composable
private fun SectionPage(section: Section, repository: Repository, onOpenAddons: () -> Unit) {
    val settings by repository.settings.collectAsState()
    val set: ((AppSettings) -> AppSettings) -> Unit = repository::updateSettings
    if (section !in SELF_TITLED) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(section.label, color = LartColors.Ink, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp)
        Text(section.sub, color = LartColors.InkMuted, fontSize = 15.5.sp, lineHeight = 22.sp)
    }
    SectionBody(section, repository, settings, set, onOpenAddons)
}

/**
 * Phones: every sub-page of a group on one page, each under its own heading, so nothing
 * needs opening. `target` scrolls to one (from search).
 */
@Composable
private fun GroupContent(
    group: Group,
    repository: Repository,
    onOpenAddons: () -> Unit,
    target: Section?,
    horizontal: androidx.compose.ui.unit.Dp,
    onTargetShown: () -> Unit,
) {
    val scroll = remember(group) { ScrollState(0) }
    val offsets = remember(group) { mutableStateMapOf<Section, Int>() }
    val settings by repository.settings.collectAsState()
    val set: ((AppSettings) -> AppSettings) -> Unit = repository::updateSettings
    LaunchedEffect(group, target, offsets[target]) {
        val y = target?.let { offsets[it] } ?: return@LaunchedEffect
        scroll.animateScrollTo(y)
        onTargetShown()
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(scroll).padding(start = horizontal, end = horizontal, top = 6.dp, bottom = 48.dp),
    ) {
        group.children.forEachIndexed { index, section ->
            Column(
                Modifier.fillMaxWidth().onGloballyPositioned { offsets[section] = it.positionInParent().y.toInt() },
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                if (index > 0) Box(Modifier.padding(top = 30.dp).fillMaxWidth().height(1.dp).background(LartColors.EdgeSoft))
                if (section !in SELF_TITLED) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(section.label, color = LartColors.Ink, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp)
                    Text(section.sub, color = LartColors.InkMuted, fontSize = 15.5.sp, lineHeight = 22.sp)
                }
                SectionBody(section, repository, settings, set, onOpenAddons)
            }
        }
    }
}

@Composable
private fun SectionBody(
    section: Section,
    repository: Repository,
    settings: AppSettings,
    set: ((AppSettings) -> AppSettings) -> Unit,
    onOpenAddons: () -> Unit,
) {
    androidx.compose.runtime.CompositionLocalProvider(LocalParts provides section.parts) {
        when (section) {
            Section.Profiles, Section.Stremio -> AccountSection(repository, settings, set)
            Section.Trackers -> TrackersPanel(repository, settings, set)
            Section.Play, Section.OnScreen, Section.Intros, Section.UpNext -> PlayerSection(repository, settings, set)
            Section.Picture, Section.Audio, Section.MpvConf -> MpvSection(settings, set)
            Section.Shaders -> ShadersSection(repository, settings, set)
            Section.AppLanguage, Section.AudioLanguage, Section.WhatYouSee -> LanguageSection(repository, settings, set)
            Section.SubtitleLanguages, Section.SubtitleSources, Section.SubtitleTiming, Section.SubtitleLook -> SubtitlesSection(repository, settings, set)
            Section.Services, Section.SourcePreferences -> StreamingSection(repository, settings, set, onOpenAddons)
            Section.StreamFilters -> StreamFiltersSection(settings, set)
            Section.HomeRows, Section.DetailPages -> LibrarySection(repository, settings, set)
            Section.PosterCards, Section.Theme -> AppearanceSection(settings, set)
            Section.Metadata -> ProvidersSection(repository)
            Section.Storage -> StorageSection(repository, set)
            Section.Backup -> BackupSection(repository)
            Section.About -> AboutSection()
        }
    }
}

// ---------------- Building blocks ----------------

/** Which parts of a section a sub-page shows; null shows them all (the phone's stacked page). */
private val LocalParts = androidx.compose.runtime.compositionLocalOf<Set<String>?> { null }

/** A part of a section, shown when the open sub-page includes it. */
@Composable
private fun Part(id: String, content: @Composable () -> Unit) {
    val parts = LocalParts.current
    if (parts == null || id in parts) content()
}

/** A labelled group of rows: Harbor's SettingGroup, one part of a section. */
@Composable
private fun Card(id: String = "", content: @Composable () -> Unit) = Part(id) { SettingsGroup(content) }

@Composable
private fun SwitchRow(title: String, body: String?, checked: Boolean, onChange: (Boolean) -> Unit) = ToggleRow(title, body, checked, onChange)

/** A row whose control sits under its text (Harbor's wide rows). */
@Composable
private fun Label(title: String, body: String? = null) {
    Column {
        RowRule()
        RowText(title, body, Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun <T> ChipRow(options: List<Pair<T, String>>, selected: T, onPick: (T) -> Unit) =
    OptionScale(options, { it == selected }, onPick)

/** Several can be on at once; `selected` keeps the order they were picked in. */
@Composable
private fun <T> MultiChipRow(options: List<Pair<T, String>>, selected: Collection<T>, onChange: (List<T>) -> Unit) {
    val ordered = selected is List<*> && selected.size > 1
    OptionScale(
        options.map { (value, label) ->
            val position = selected.indexOf(value)
            value to if (ordered && position >= 0) "${position + 1}. $label" else label
        },
        { it in selected },
    ) { value -> onChange(if (value in selected) selected.filter { it != value } else selected.toList() + value) }
}

@Composable
private fun Muted(text: String) = Text(text, color = LartColors.InkMuted, fontSize = 15.sp, lineHeight = 21.sp)

@Composable
private fun HSlider(value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Slider(
        value = value,
        onValueChange = onChange,
        valueRange = range,
        colors = SliderDefaults.colors(thumbColor = LartColors.Ink, activeTrackColor = LartColors.Ink, inactiveTrackColor = LartColors.Raised),
    )
}

private fun languageOptions(extra: List<String> = emptyList()): List<Pair<String, String>> {
    val device = Locale.getDefault().language
    return (listOf(device) + LANGUAGE_CODES + extra).distinct().map { it to languageName(it) }
}

// ---------------- Account & setup ----------------

@Composable
private fun AccountSection(repository: Repository, settings: AppSettings, set: ((AppSettings) -> AppSettings) -> Unit) {
    Part("Stremio") { StremioCard(repository) }
    Part("Profiles") { ProfilesCard(repository) }
    Card("Profiles") {
        SwitchRow(
            "Ask \"Who's watching?\" when Lart opens",
            "With more than one profile. Off opens the last profile used.",
            settings.askProfileOnLaunch,
        ) { v -> set { it.copy(askProfileOnLaunch = v) } }
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
    val current = account
    SettingsSection(
        if (current != null) "Your Stremio account" else "Sign in to Stremio",
        if (current != null) "Your addons and library come from this account." else "Sign in to use your Stremio addons and library.",
    ) {
        if (current != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(current.email, color = LartColors.Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                HGhostButton(onClick = { scope.launch { busy = true; repository.signOut(); busy = false } }, enabled = !busy) { Text("Sign out") }
            }
        } else {
            HTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            HTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(it, color = Color(0xFFE57373), fontSize = 14.sp) }
            HButton(
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
private fun PlayerSection(repository: Repository, settings: AppSettings, set: ((AppSettings) -> AppSettings) -> Unit) {
    val autoPlay by repository.autoPlay.collectAsState()
    val prefs by repository.playerPrefs.collectAsState()

    Card("Player engine") {
        SettingsLabel("Player engine")
        ChipRow(listOf("mpv" to "mpv", "media3" to "Android (Media3)"), settings.engine) { v -> set { it.copy(engine = v) } }
        Muted(
            if (settings.engine == "mpv") "Harbor's player: styled subtitles, software decoding when the phone can't, Anime4K. If mpv can't start, Lart uses Media3."
            else "Android's own player: lightest on battery. No Anime4K; Lart draws the subtitles.",
        )
    }

    Card("When you press Play") {
        SettingsLabel("When you press Play")
        SwitchRow("Instant play", "Skips the list of sources and plays the best one.", autoPlay, repository::setAutoPlay)
        SwitchRow("Remember the source you picked", "Marks it next time, and instant play prefers it.", settings.rememberLastStream) { v -> set { it.copy(rememberLastStream = v) } }
        SwitchRow("Resume where you left off", "Off starts every video from the beginning.", settings.resumePlayback) { v -> set { it.copy(resumePlayback = v) } }
        SwitchRow("Show \"Resumed at\" with Start over", null, settings.resumeNotice) { v -> set { it.copy(resumeNotice = v) } }
        SwitchRow("Switch source when a stream stalls", "Moves to the next source after it buffers this long.", settings.autoNextStreamOnStall) { v -> set { it.copy(autoNextStreamOnStall = v) } }
        if (settings.autoNextStreamOnStall) {
            ChipRow(listOf(5, 10, 15, 30).map { it to "$it s" }, settings.stallSec) { v -> set { it.copy(stallSec = v) } }
        }
    }

    Card("On-screen controls") {
        SettingsLabel("On-screen controls")
        SwitchRow("X-Ray", "An X-Ray button in the player: the cast with photos, the crew and the facts about what you're watching.", settings.xrayEnabled) { v -> set { it.copy(xrayEnabled = v) } }
        SwitchRow("Show stream quality under the title", "The source's quality, such as 4K or 1080p, next to the title in the player.", settings.qualityInPlayer) { v -> set { it.copy(qualityInPlayer = v) } }
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

    Card("Up next") {
        SettingsLabel("Up next")
        SwitchRow("Auto-play next episode", "When an episode ends.", settings.autoPlayNextEpisode) { v -> set { it.copy(autoPlayNextEpisode = v) } }
        Label("Show Up next", "\"At the credits\" uses the intro databases' credit times when they have them.")
        ChipRow(listOf(0 to "At the credits", 30 to "30 s", 45 to "45 s", 60 to "1 min", 90 to "90 s"), settings.nextEpisodeLeadSec) { v ->
            set { it.copy(nextEpisodeLeadSec = v) }
        }
        SwitchRow("Keep the same source for the next episode", "Prefers the addon the current episode came from.", settings.keepSourceNextEpisode) { v -> set { it.copy(keepSourceNextEpisode = v) } }
        Label("Ask if you're still watching after")
        ChipRow(listOf(0 to "Never", 2 to "2 episodes", 3 to "3", 5 to "5"), prefs.stillWatchingAfter) { n ->
            repository.updatePlayerPrefs { it.copy(stillWatchingAfter = n) }
        }
    }

    Card("Intros & credits") {
        SettingsLabel("Intros & credits")
        Muted("From IntroDB, TheIntroDB, SkipDB and AniSkip, when they know the episode.")
        SwitchRow("Show the Skip button", null, settings.showSkipButton) { v -> set { it.copy(showSkipButton = v) } }
        if (settings.showSkipButton) {
            Label("Auto-hide the Skip button after")
            ChipRow(listOf(0 to "Never", 5 to "5 s", 10 to "10 s", 15 to "15 s"), settings.skipButtonHideSec) { v -> set { it.copy(skipButtonHideSec = v) } }
        }
        SwitchRow("Auto-skip intros", null, prefs.autoSkipIntro) { v -> repository.updatePlayerPrefs { it.copy(autoSkipIntro = v) } }
        SwitchRow("Auto-skip recaps", null, settings.autoSkipRecap) { v -> set { it.copy(autoSkipRecap = v) } }
        SwitchRow("Auto-skip credit outros", "Goes straight to the next episode, or to the end of a movie.", settings.autoSkipOutro) { v -> set { it.copy(autoSkipOutro = v) } }
    }
}

@Composable
private fun MpvSection(settings: AppSettings, set: ((AppSettings) -> AppSettings) -> Unit) {
    if (settings.engine != "mpv") Muted("These apply when the player engine is mpv (Playback → Player).")
    Card("Video") {
        SettingsLabel("Video")
        Label("Decoding")
        ChipRow(listOf("auto" to "Auto", "on" to "Hardware only", "off" to "Software"), settings.mpvHwdec) { v -> set { it.copy(mpvHwdec = v) } }
        Label("Playback quality", "Quality uses sharper scaling and debanding; Performance saves battery.")
        ChipRow(listOf("performance" to "Performance", "balanced" to "Balanced", "quality" to "Quality"), settings.mpvQuality) { v -> set { it.copy(mpvQuality = v) } }
        Label("Buffer")
        ChipRow(listOf("small" to "Small", "auto" to "Normal", "large" to "Large"), settings.bufferSize) { v -> set { it.copy(bufferSize = v) } }
    }
    Card("Sound") {
        SettingsLabel("Sound")
        SwitchRow("Mix surround sound down to stereo", "Clearer dialogue on phone speakers and headphones.", settings.downmixStereo) { v -> set { it.copy(downmixStereo = v) } }
        SwitchRow("Normalize loudness", "Evens out quiet dialogue and loud scenes.", settings.audioNormalize) { v -> set { it.copy(audioNormalize = v) } }
        Label("Sound profile")
        ChipRow(listOf("off" to "Off", "bass" to "Bass boost", "voice" to "Voice", "bass-reduce" to "Less bass", "night" to "Night"), settings.audioProfile) { v ->
            set { it.copy(audioProfile = v) }
        }
        Label("Maximum volume boost", "Swipe up past full volume on the right edge to go louder.")
        ChipRow(listOf(1 to "Off", 2 to "200%", 3 to "300%"), settings.volumeBoostMax) { v -> set { it.copy(volumeBoostMax = v) } }
    }
    Card("Advanced") {
        SettingsLabel("Advanced")
        MpvOptionsField(settings.mpvExtraOptions) { v -> set { it.copy(mpvExtraOptions = v) } }
    }
}

@Composable
private fun ShadersSection(repository: Repository, settings: AppSettings, set: ((AppSettings) -> AppSettings) -> Unit) {
    val prefs by repository.playerPrefs.collectAsState()
    if (settings.engine != "mpv") Muted("Anime4K needs the mpv engine (Playback → Player).")
    Card("Mode") {
        SettingsLabel("Mode")
        Muted("You can also change it from the player's Anime4K button.")
        ChipRow(listOf("" to "Off") + Anime4K.Mode.entries.map { it.name to it.label }, prefs.anime4kMode) { mode ->
            repository.updatePlayerPrefs { it.copy(anime4kMode = mode) }
        }
        Label("Quality")
        ChipRow(listOf(false to "Fast", true to "HQ"), prefs.anime4kHq) { hq -> repository.updatePlayerPrefs { it.copy(anime4kHq = hq) } }
        SwitchRow("Anime only", "Leaves live-action films and series untouched.", settings.anime4kAnimeOnly) { v -> set { it.copy(anime4kAnimeOnly = v) } }
        SwitchRow("Show an Anime4K badge while playing", null, settings.anime4kIndicator) { v -> set { it.copy(anime4kIndicator = v) } }
    }
}

private fun speedLabel(speed: Float) = speed.toString().removeSuffix(".0") + "×"

@Composable
private fun MpvOptionsField(saved: String, onSave: (String) -> Unit) {
    var text by remember(saved) { mutableStateOf(saved) }
    Label("Extra mpv options", "One per line, as key=value (for example deband=yes). Applied when a video starts.")
    HTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(), minLines = 3, label = { Text("deband=yes") })
    if (text != saved) HButton(onClick = { onSave(text.trim()) }) { Text("Save options") }
}

// ---------------- Languages ----------------

@Composable
private fun LanguageSection(repository: Repository, settings: AppSettings, set: ((AppSettings) -> AppSettings) -> Unit) {
    // ---- App language ----
    Part("App language") {
        HarborHeading("Display language", "Lart's own words are in English for now.")
        HarborSelect(listOf("en" to "🇺🇸  English"), "en") {}
        HarborHeading("Metadata language", "Titles, descriptions and episode names from TMDB, in this language (needs a TMDB key).")
        HarborSelect(listOf("" to "Same as the device") + Languages.all.map { it.code to "${it.flag}  ${it.name}" }, settings.metadataLanguage) { v -> set { it.copy(metadataLanguage = v) } }
        ToggleRow("Translate titles", "Show titles in the metadata language. Off keeps them as the addon has them.", settings.translateTitles) { v -> set { it.copy(translateTitles = v) } }
        ToggleRow("Translate overviews", "Show descriptions in the metadata language. Off shows them in English.", settings.translateOverviews) { v -> set { it.copy(translateOverviews = v) } }
        HarborHeading("Image languages", "Which language logos and posters come in. Same as metadata follows the setting above.")
        HarborSelect(listOf("" to "Same as metadata", "null" to "No text (textless)") + Languages.all.map { it.code to "${it.flag}  ${it.name}" }, settings.imageLanguage) { v -> set { it.copy(imageLanguage = v) } }
        HarborHeading("Region & language", "Your country, for what's in cinemas, release dates and where to watch.")
        HarborSelect(
            listOf("" to "Same as the device") + java.util.Locale.getISOCountries().map { c -> c to "${Lang("", "", "", c).flag}  ${java.util.Locale("", c).displayCountry}" }.sortedBy { it.second.drop(4) },
            settings.region,
        ) { v -> set { it.copy(region = v) } }
    }
    // ---- Audio languages ----
    Part("Audio languages") {
        HarborHeading("Audio languages", "When a release ships multiple audio tracks, Lart selects the first match from this list.")
        LanguageOrder(settings.audioLanguages) { v -> set { it.copy(audioLanguages = v) } }
        HarborHeading("Skip these tracks", "Applies to both audio and subtitle tracks. You can still pick a skipped track by hand in the player.")
        IconHeading(Icons.Outlined.Block, "Never auto-select tracks containing", "Tracks whose name contains one of these words are skipped.")
        WordList(settings.skipTrackWords, "Type a word and press Enter") { v -> set { it.copy(skipTrackWords = v) } }
    }
    // ---- Discovery languages ----
    Part("Discovery languages") {
        HarborHeading("Home catalogs", "Only show titles in these original languages on the Home rows. Leave all off to show everything.")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 6.dp)) {
            Icon(Icons.Outlined.Language, null, tint = LartColors.InkSubtle, modifier = Modifier.size(20.dp))
            Text(
                if (settings.homeLanguages.isEmpty()) "No filter. Home shows every language."
                else "Home shows ${settings.homeLanguages.joinToString(", ") { Languages.of(it).name }}.",
                color = LartColors.InkMuted,
                fontSize = 15.5.sp,
            )
        }
        LanguageTiles(Languages.home, settings.homeLanguages.toSet()) { code ->
            set { s -> s.copy(homeLanguages = if (code in s.homeLanguages) s.homeLanguages - code else s.homeLanguages + code) }
        }
        ToggleRow(
            "Favour titles from where you are",
            "Nudge the Home rows toward your region and languages so local releases surface instead of the same worldwide list. Turn it off to see the unweighted picks.",
            settings.favourRegion,
        ) { v -> set { it.copy(favourRegion = v) } }
        HarborHeading("Stream ranking", "Sources in these languages sort to the top of the picker.")
        LanguageOrder(settings.streamLanguages.mapNotNull { Languages.byName(it)?.code }) { codes -> set { it.copy(streamLanguages = codes.map { c -> Languages.of(c).name }) } }
        ToggleRow("Only show streams in my languages", "Hide sources that name a language and none of yours. Sources that name no language stay.", settings.requireStreamLanguage) { v -> set { it.copy(requireStreamLanguage = v) } }
    }
}

@Composable
private fun SubtitlesSection(repository: Repository, settings: AppSettings, set: ((AppSettings) -> AppSettings) -> Unit) {
    val prefs by repository.playerPrefs.collectAsState()
    val keys by repository.providerKeys.collectAsState()
    val uri = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    // ---- Subtitle languages ----
    Part("Subtitle languages") {
        HarborHeading("Subtitle languages", "Lart looks for subtitles in this order. Put your preferred language first.")
        val order = (listOf(prefs.subLanguage) + settings.subtitleFallbacks).filter { it.isNotEmpty() }.distinct()
        LanguageOrder(order) { v ->
            repository.updatePlayerPrefs { it.copy(subLanguage = v.firstOrNull().orEmpty()) }
            set { it.copy(subtitleFallbacks = v.drop(1)) }
        }
        HarborHeading("Dual subtitles", "Show two subtitle languages at once. Useful for learning a language or watching together.")
        IconHeading(Icons.Outlined.Translate, "Second subtitle language", "Shown at the same time as your main subtitle (mpv).")
        HarborSelect(listOf("" to "Off") + Languages.all.map { it.code to "${it.flag}  ${it.name}" }, settings.secondarySubLanguage) { v -> set { it.copy(secondarySubLanguage = v) } }
        if (settings.secondarySubLanguage.isNotEmpty()) {
            IconHeading(Icons.Outlined.FormatSize, "Second line size", "How big the second subtitle is next to the first.")
            TickSlider(settings.secondSubScale, listOf(0.6f, 0.7f, 0.8f, 0.85f, 0.9f, 1f), { "${(it * 100).toInt()}%" }, 0.85f) { v -> set { it.copy(secondSubScale = v) } }
        }
        HarborHeading("Choosing a track", "Which subtitle Lart lands on when more than one is available.")
        ToggleRow("Prefer embedded subtitles", "Keep the subtitle track included in the video file instead of switching to a downloaded one.", settings.preferEmbeddedSubs) { v -> set { it.copy(preferEmbeddedSubs = v) } }
        ToggleRow(
            "Forced subs with native audio",
            "When the audio already matches your subtitle language, pick a forced track (foreign dialogue and signs only) instead of full subtitles. If the file has no forced track, subtitles stay off.",
            settings.forcedSubsNativeAudio,
        ) { v -> set { it.copy(forcedSubsNativeAudio = v) } }
        ToggleRow("Start with subtitles off", "Whether subtitles are showing the moment a video starts. Turn them on yourself in the player.", settings.subtitlesOffByDefault) { v -> set { it.copy(subtitlesOffByDefault = v) } }
        ToggleRow("Choose subtitles before playback", "Opens the subtitle menu when a video starts, so you pick before watching.", settings.chooseSubsBeforePlayback) { v -> set { it.copy(chooseSubsBeforePlayback = v) } }
        ToggleRow("Upgrade subtitles when better ones load", "If no subtitle matched at first, switch to one in your language when the sources answer.", settings.upgradeSubtitles) { v -> set { it.copy(upgradeSubtitles = v) } }
        ToggleRow("Hide sound effects and speaker names", "Skip hearing-impaired (SDH) tracks when picking automatically.", settings.hideSdh) { v -> set { it.copy(hideSdh = v) } }
        ToggleRow("Subtitle indicator dot", "A dot on the subtitles button while subtitles are on.", settings.subIndicatorDot) { v -> set { it.copy(subIndicatorDot = v) } }
    }
    // ---- Subtitle sources ----
    Part("Subtitle sources") {
        HarborHeading("Subtitle sources", "Where Lart looks for subtitles besides the video itself. Each one searches in your subtitle languages.")
        ToggleRow("Subtitle addons", "OpenSubtitles v3 and any other subtitle addon you've installed.", settings.subtitleAddons) { v -> set { it.copy(subtitleAddons = v) } }
        fun saveKey(name: String, value: String?) { scope.launch { repository.setProviderKey(name, value) } }
        ProviderKeyBlock(
            "S", Color(0xFFF5C518), "SUBDL", "Search SUBDL for subtitles in multiple languages. Requires an API key.",
            settings.subdlEnabled, { v -> set { it.copy(subdlEnabled = v) } },
            "SUBDL API key", keys[ProviderKey.SUBDL], { saveKey(ProviderKey.SUBDL, it) },
            "SUBDL returns nothing until a key is saved here.", "Get a free key at subdl.com",
        ) { uri.openUri("https://subdl.com/panel/api") }
        ProviderKeyBlock(
            "≡", Color(0xFF8BD5D0), "Subsource", "Search the Subsource community database. Requires an API key.",
            settings.subsourceEnabled, { v -> set { it.copy(subsourceEnabled = v) } },
            "Subsource API key", keys[ProviderKey.SUBSOURCE], { saveKey(ProviderKey.SUBSOURCE, it) },
            "Subsource returns nothing until a key is saved here.", "Get your key at subsource.net",
        ) { uri.openUri("https://subsource.net/api-docs") }
        ProviderKeyBlock(
            "W", Color(0xFFB794F4), "Wyzie", "Searches many subtitle sites at once. Needs a free key.",
            settings.wyzieEnabled, { v -> set { it.copy(wyzieEnabled = v) } },
            "Wyzie key", keys[ProviderKey.WYZIE], { saveKey(ProviderKey.WYZIE, it) },
            "Wyzie answers only with a key.", "Claim a free key at wyzie.io",
        ) { uri.openUri("https://store.wyzie.io/redeem") }
        ProviderKeyBlock(
            "O", Color(0xFF7CC0F0), "OpenSubtitles", "The OpenSubtitles.com API, with your own key (downloads count against its daily limit).",
            settings.openSubtitlesEnabled, { v -> set { it.copy(openSubtitlesEnabled = v) } },
            "OpenSubtitles API key", keys[ProviderKey.OPENSUBTITLES], { saveKey(ProviderKey.OPENSUBTITLES, it) },
            "Create a consumer on OpenSubtitles.com to get one.", "opensubtitles.com/consumers",
        ) { uri.openUri("https://www.opensubtitles.com/en/consumers") }
        HarborHeading("Preferred languages", "All subtitle sources use the same preferred language order.")
        val order = (listOf(prefs.subLanguage) + settings.subtitleFallbacks).filter { it.isNotEmpty() }.distinct()
        Text(
            if (order.isEmpty()) "No subtitle language yet. Add one under Subtitle languages."
            else order.joinToString("  ·  ") { "${Languages.of(it).flag} ${Languages.of(it).name}" },
            color = LartColors.InkMuted,
            fontSize = 15.5.sp,
        )
    }
    // ---- Subtitle timing ----
    Part("Subtitle timing") {
        HarborHeading("Subtitle timing", "Nudge a subtitle earlier or later from the player's subtitle menu (Sync Offset).")
        ToggleRow("Show subtitle sync indicator", "Puts the current offset on screen while you nudge subtitle timing, so you can see how far you have shifted them.", settings.subSyncIndicator) { v -> set { it.copy(subSyncIndicator = v) } }
        Text(
            "Harbor's audio-based auto-sync and its community corrections run on the desktop; they aren't in the Android app.",
            color = LartColors.InkSubtle,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
    // ---- Subtitle style ----
    Part("Subtitle style") {
        HarborHeading("Subtitle style", "Adjust the text and background. The preview shows your changes as you make them.")
        SubtitlePreview(prefs, settings)
        SettingsLabel("Background")
        CheckList(
            listOf(
                Triple("shadow", "Drop shadow", "Soft halo around the text. Cleanest on most content."),
                Triple("outline", "Outline", "Hard stroke around each letter. High contrast."),
                Triple("box", "Black bar", "Rounded background panel behind the text. Most readable."),
            ),
            prefs.subBackground,
        ) { v -> repository.updatePlayerPrefs { it.copy(subBackground = v) } }
        when (prefs.subBackground) {
            "outline" -> {
                RowText("Outline thickness", "How heavy the stroke around each letter is.")
                TickSlider(settings.subOutlineSize, (0..10).map { it.toFloat() }, { "${it.toInt()}px" }, 2f) { v -> set { it.copy(subOutlineSize = v) } }
            }
            "box" -> {
                RowText("Box color", "The panel behind the text.")
                ColorSwatches(SWATCHES, settings.subBoxColor, 0xFF000000) { c -> set { it.copy(subBoxColor = c) } }
                RowText("Background opacity", "How solid the panel is.")
                TickSlider(settings.subBoxOpacity, listOf(0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1f), { "${(it * 100).toInt()}%" }, 0.6f) { v -> set { it.copy(subBoxOpacity = v) } }
            }
        }
        SettingsLabel("Text")
        RowText("Size", "How large subtitles are drawn on the video, at any screen size.")
        TickSlider(prefs.subScale, listOf(0.6f, 0.7f, 0.8f, 0.9f, 1f, 1.1f, 1.2f, 1.35f, 1.5f, 1.65f, 1.8f), { "${(it * 100).toInt()}%" }, 1f) { v -> repository.updatePlayerPrefs { it.copy(subScale = v) } }
        ToggleRow("Bold text", "Thicker letters, easier to read on busy pictures.", settings.subBold) { v -> set { it.copy(subBold = v) } }
        RowText("Text color", "The colour of the letters.")
        ColorSwatches(SWATCHES, prefs.subColor, 0xFFFFFFFF) { c -> repository.updatePlayerPrefs { it.copy(subColor = c) } }
        RowText("Opacity", "How see-through the letters are.")
        TickSlider(settings.subOpacity, listOf(0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1f), { "${(it * 100).toInt()}%" }, 1f) { v -> set { it.copy(subOpacity = v) } }
        RowText("Outline color", "The stroke or halo drawn behind the letters.")
        ColorSwatches(SWATCHES, settings.subOutlineColor, 0xFF000000) { c -> set { it.copy(subOutlineColor = c) } }
        SettingsLabel("Font")
        Text("The typeface subtitles are drawn in.", color = LartColors.InkMuted, fontSize = 15.sp)
        TileChoices(
            listOf("inter" to "Inter", "system" to "System", "condensed" to "Condensed", "serif" to "Serif", "mono" to "Mono", "arabic" to "Arabic"),
            settings.subFont,
            fontFor = { app.lart.mobile.player.subFontFamily(it) },
        ) { v -> set { it.copy(subFont = v) } }
        SettingsLabel("Styled (ASS) subtitles")
        CheckList(
            listOf(
                Triple("no", "Keep original", "Styled (ASS) subs keep their own font, colour and size. Truest to the release, but the size can vary a lot between files."),
                Triple("scale", "Normalize embedded subtitle size", "Keep their style, at your size."),
                Triple("force", "Use my style", "Draw styled subtitles in your style, like any other."),
            ),
            settings.assOverride,
        ) { v -> set { it.copy(assOverride = v) } }
        SettingsLabel("Sync indicator")
        ToggleRow("Subtitle sync indicator", "Puts the current offset on screen while you nudge subtitle timing.", settings.subSyncIndicator) { v -> set { it.copy(subSyncIndicator = v) } }
        SettingsLabel("Position")
        PositionGrid(settings.subPosition) { v -> set { it.copy(subPosition = v, subAlignX = "center") } }
        if (settings.subPosition.startsWith("bottom")) {
            RowText("Distance from bottom", "How far above the bottom edge subtitles sit.")
            TickSlider(prefs.subBottom, listOf(0.02f, 0.04f, 0.06f, 0.08f, 0.1f, 0.12f, 0.15f, 0.2f, 0.25f, 0.3f), { "${(it * 100).toInt()}%" }, 0.08f) { v ->
                repository.updatePlayerPrefs { it.copy(subBottom = v) }
            }
        }
        SettingsLabel("Size")
        PillChoice(listOf(0.85f to "Smaller", 1f to "Default", 1.2f to "Larger"), listOf(0.85f, 1f, 1.2f).minByOrNull { kotlin.math.abs(it - prefs.subScale) } ?: 1f) { v ->
            repository.updatePlayerPrefs { it.copy(subScale = v) }
        }
        ToggleRow("Show subtitles in Picture-in-Picture", "Keep subtitles on while the video plays in a small window.", settings.subsInPip) { v -> set { it.copy(subsInPip = v) } }
        Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            HGhostButton(onClick = {
                repository.updatePlayerPrefs { it.copy(subScale = 1f, subColor = 0xFFFFFFFF, subBackground = "outline", subBottom = 0.08f) }
                set {
                    it.copy(
                        subBold = false, subBoxOpacity = 0.6f, subAlignX = "center", assOverride = "scale", subOutlineColor = 0xFF000000,
                        subOutlineSize = 2f, subFont = "inter", subPosition = "bottom", subOpacity = 1f, subBoxColor = 0xFF000000,
                    )
                }
            }) { Text("Reset subtitle style") }
            Text("Puts every subtitle option back to the Harbor default.", color = LartColors.InkSubtle, fontSize = 14.sp)
        }
    }
}

private val SWATCHES = listOf(0xFFFFFFFF, 0xFF000000, 0xFFFF3B30, 0xFFFF9500, 0xFFFFCC00, 0xFF34C759, 0xFF5AC8FA, 0xFF007AFF, 0xFFAF52DE, 0xFFFF2D92)

/** Harbor's subtitle preview: a film still with the sample line in your style. */
@Composable
private fun SubtitlePreview(prefs: app.lart.mobile.data.PlayerPrefs, settings: AppSettings) {
    Box(
        Modifier.fillMaxWidth().widthIn(max = 640.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp))
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF3B2F2A), Color(0xFF1B1A1E), Color(0xFF4A3426)))),
    ) {
        coil.compose.AsyncImage(
            "https://images.metahub.space/background/medium/tt0068646/img",
            null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        val align = when {
            settings.subPosition.startsWith("top") -> Alignment.TopCenter
            settings.subPosition in setOf("left", "center", "right") -> Alignment.Center
            else -> Alignment.BottomCenter
        }
        Box(Modifier.align(align).fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp)) {
            app.lart.mobile.player.SubtitleText("This is how your\nsubtitles will look.", prefs, settings, Modifier.align(Alignment.Center))
        }
    }
}

// ---------------- Sources & library ----------------

@Composable
private fun StreamingSection(repository: Repository, settings: AppSettings, set: ((AppSettings) -> AppSettings) -> Unit, onOpenAddons: () -> Unit) {
    val addons by repository.addons.collectAsState()
    Part("Addons") {
        SettingsSection("Addons", "${addons.size} installed: ${addons.joinToString(", ") { it.manifest.name }}") {
            HButton(onClick = onOpenAddons) { Text("Manage addons") }
        }
    }
    Part("Debrid") { DebridCard(repository) }
    Card("Safety & order") {
        SettingsLabel("Safety & order")
        Label("Stream safety filter", "Balanced hides CAM and telesync rips; Strict also hides tiny files, usually fakes.")
        ChipRow(listOf("off" to "Off", "balanced" to "Balanced", "strict" to "Strict"), settings.safetyFilter) { v -> set { it.copy(safetyFilter = v) } }
        Label("Order")
        ChipRow(listOf("lart" to "Best first", "addon" to "As the addons list them"), settings.streamSort) { v -> set { it.copy(streamSort = v) } }
        SwitchRow("No torrents", "Hide torrent sources, even with debrid.", settings.torrentsDisabled) { v -> set { it.copy(torrentsDisabled = v) } }
        SwitchRow("Show file names", "Under each source in the list.", settings.showFilename) { v -> set { it.copy(showFilename = v) } }
        Label("Addon timeout", "How long to wait for a slow addon before showing the rest.")
        ChipRow(listOf(10 to "10 s", 20 to "20 s", 30 to "30 s", 60 to "60 s"), settings.addonTimeoutSec) { v -> set { it.copy(addonTimeoutSec = v) } }
    }
}

@Composable
private fun StreamFiltersSection(settings: AppSettings, set: ((AppSettings) -> AppSettings) -> Unit) {
    val filter = settings.streamFilter
    fun update(change: (StreamFilter) -> StreamFilter) = set { it.copy(streamFilter = change(it.streamFilter)) }
    Card("Quality") {
        SettingsLabel("Quality")
        Muted("Only sources that match show up. Nothing picked in a row means any.")
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
        if (filter.active) HGhostButton(onClick = { update { StreamFilter() } }) { Text("Clear the filter") }
    }
}

@Composable
private fun LibrarySection(repository: Repository, settings: AppSettings, set: ((AppSettings) -> AppSettings) -> Unit) {
    Card("Home") {
        SettingsLabel("Home")
        SwitchRow("Top 10 row", null, settings.showTop10) { v -> set { it.copy(showTop10 = v) } }
        SwitchRow("Rotate the hero", "Moves to the next featured title every few seconds.", settings.heroAutoRotate) { v -> set { it.copy(heroAutoRotate = v) } }
        SwitchRow("Hide unreleased titles", null, settings.hideUnreleased) { v -> set { it.copy(hideUnreleased = v) } }
        SwitchRow("Hide anime", "Removes the Anime view and anime rows.", settings.hideAnime) { v -> set { it.copy(hideAnime = v) } }
        SwitchRow("Keep anime in the anime room only", "Anime stays in the Anime view, out of Home, Movies and Shows.", settings.animeRoomOnly) { v -> set { it.copy(animeRoomOnly = v) } }
        SwitchRow("Hide watched titles in catalogs", "Titles you have finished drop out of the rows.", settings.hideWatched) { v -> set { it.copy(hideWatched = v) } }
        SwitchRow("New episodes row", "The last two weeks of episodes from the shows you follow, on Home.", settings.newEpisodesRow) { v -> set { it.copy(newEpisodesRow = v) } }
    }
    // Harbor's Detail pages tab: Spoilers, then Episode cards.
    Part("Show pages") {
        SettingsSection("Show pages", "How a show or movie page behaves when you open it.") {
            Column {
                ToggleRow("Show full descriptions", "Opens with the whole synopsis instead of the first lines.", settings.fullDescriptions) { v -> set { it.copy(fullDescriptions = v) } }
                ToggleRow("Remember your place on show pages", "Reopens a show on the season you last looked at.", settings.rememberSeason) { v -> set { it.copy(rememberSeason = v) } }
                ToggleRow("Use free IMDb data without a TMDB key", "Cast photos, networks and air dates from TVMaze and imdbapi.dev when there is no TMDB key.", settings.keylessDetails) { v -> set { it.copy(keylessDetails = v) } }
            }
        }
    }
    Part("Spoilers") {
        SettingsSection("Spoilers", "Keep unwatched episode details hidden.") {
            Column {
                ToggleRow("Blur spoilers", "Hides spoiler-prone episode details in episode lists until you have watched them.", settings.hideSpoilers) { v -> set { it.copy(hideSpoilers = v) } }
                if (settings.hideSpoilers) {
                    ToggleRow("Blur thumbnails", null, settings.spoilerThumbnails) { v -> set { it.copy(spoilerThumbnails = v) } }
                    ToggleRow("Hide titles", null, settings.spoilerTitles) { v -> set { it.copy(spoilerTitles = v) } }
                    ToggleRow("Hide descriptions", null, settings.spoilerDescriptions) { v -> set { it.copy(spoilerDescriptions = v) } }
                }
            }
        }
    }
    Part("Episode cards") {
        SettingsSection("Episode cards", "What an episode shows on a title page.") {
            Column {
                SettingsLabel("On the card")
                ToggleRow("Show IMDb rating on episodes", "Shows each episode's rating. Add your free OMDb API key for real IMDb scores; without it, ratings fall back to TMDB.", settings.showEpisodeRating) { v -> set { it.copy(showEpisodeRating = v) } }
                ToggleRow("Show episode description", "Shows the episode synopsis on the cards. Turn it off to hide it.", settings.showEpisodeDescription) { v -> set { it.copy(showEpisodeDescription = v) } }
                Label("Order")
                Box(Modifier.padding(top = 10.dp, bottom = 6.dp)) {
                    ChipRow(listOf(false to "Oldest first", true to "Newest first"), settings.episodesNewestFirst) { v -> set { it.copy(episodesNewestFirst = v) } }
                }
                Label("Card size", "How big episode cards are. Bigger cards show larger artwork.")
                Box(Modifier.padding(top = 10.dp)) {
                    ChipRow(listOf(0 to "Default", 1 to "Large", 2 to "Extra large"), settings.episodeCardSize) { v -> set { it.copy(episodeCardSize = v) } }
                }
            }
        }
    }
    Part("Community") {
        SettingsSection("Community", "What other people say about a title.") {
            Column {
                ToggleRow("Trakt comments", "Shows Trakt comments and reviews on title pages.", settings.showTraktComments) { v -> set { it.copy(showTraktComments = v) } }
                ToggleRow("Letterboxd reviews", "Shows Letterboxd's average and popular reviews on movie pages.", settings.showLetterboxdDetail) { v -> set { it.copy(showLetterboxdDetail = v) } }
            }
        }
    }
}

/** A metadata source, as a row in Harbor's Metadata providers list. */
private class Provider(
    val name: String,
    val badge: String?,
    val body: String,
    val current: String?,
    val label: String,
    val link: String,
    val save: suspend (String?) -> Unit,
)

/**
 * Harbor's Metadata providers (library-panel/providers-tab.tsx): each provider with what it
 * adds, Saved or Not set, and Manage or Add key, which opens the key field under it.
 */
@Composable
private fun ProvidersSection(repository: Repository) {
    val keys by repository.providerKeys.collectAsState()
    val providers = listOf(
        Provider(
            "TMDB", "Recommended", "Trending, Popular, In Theaters, the per service rails, cast, artwork and details.",
            repository.tmdbKey.collectAsState().value, "TMDB API key or read token", "https://www.themoviedb.org/settings/api",
        ) { repository.setTmdbKey(it) },
        Provider(
            "OMDb", null, "Real IMDb, Rotten Tomatoes and Metacritic scores on title pages.",
            repository.omdbKey.collectAsState().value, "OMDb API key", "https://www.omdbapi.com/apikey.aspx",
        ) { repository.setOmdbKey(it) },
        Provider(
            "TheTVDB", null, "Episode titles, stills and descriptions in your language, and network info.",
            keys[ProviderKey.TVDB], "TheTVDB API key", "https://thetvdb.com/api-information",
        ) { repository.setProviderKey(ProviderKey.TVDB, it) },
        Provider(
            "MDBList", null, "Letterboxd, Trakt, Metacritic and audience scores.",
            keys[ProviderKey.MDBLIST], "MDBList API key", "https://mdblist.com/preferences/",
        ) { repository.setProviderKey(ProviderKey.MDBLIST, it) },
        Provider(
            "Fanart.tv", null, "Logos and backdrops where TMDB comes up empty.",
            keys[ProviderKey.FANART], "Fanart.tv API key", "https://fanart.tv/get-an-api-key/",
        ) { repository.setProviderKey(ProviderKey.FANART, it) },
        Provider(
            "RPDB", null, "Paid. Bakes scores into the poster image itself.",
            repository.rpdbKey.collectAsState().value, "RPDB API key", "https://ratingposterdb.com",
        ) { repository.setRpdbKey(it) },
        Provider(
            "Custom poster service", null, "Swap in Better Posters, PostersPlus, or your own URL template with {imdbId}, {tmdbId} or {type}.",
            keys[ProviderKey.POSTERS], "Poster URL or template", "https://btttr.cc",
        ) { repository.setProviderKey(ProviderKey.POSTERS, it) },
    )
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    Text("Add TMDB for more catalogs and artwork. Other providers add the features listed below. You can use Lart with Cinemeta without adding a key.", color = LartColors.InkMuted, fontSize = 15.5.sp, lineHeight = 22.sp)
    run {
        Column {
            providers.forEachIndexed { index, provider ->
                if (index > 0) RowRule()
                Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(provider.name, color = LartColors.Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            provider.badge?.let {
                                Text(
                                    it.uppercase(),
                                    color = LartColors.Accent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(LartColors.Accent.copy(alpha = 0.15f)).padding(horizontal = 7.dp, vertical = 2.dp),
                                )
                            }
                        }
                        Text(provider.body, color = LartColors.InkMuted, fontSize = 15.sp, lineHeight = 21.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(if (provider.current != null) Success else LartColors.Raised))
                        Text(if (provider.current != null) "Saved" else "Not set", color = LartColors.InkMuted, fontSize = 14.sp)
                        HGhostButton(onClick = { open = if (open == provider.name) null else provider.name }) {
                            Text(if (provider.current != null) "Manage" else "Add key")
                        }
                    }
                }
                if (open == provider.name) {
                    Box(Modifier.padding(bottom = 14.dp)) {
                        KeyCard(provider.name, "Paste your ${provider.name} key; it stays on this device.", provider.label, provider.current, "Get a key", provider.link, provider.save)
                    }
                }
            }
        }
    }
}

// ---------------- Appearance ----------------

private val ACCENTS = listOf(0xFFF0A35E, 0xFFE57373, 0xFFF06292, 0xFFBA68C8, 0xFF7986CB, 0xFF4FC3F7, 0xFF4DB6AC, 0xFF81C784, 0xFFDCE775, 0xFFFFD54F)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearanceSection(settings: AppSettings, set: ((AppSettings) -> AppSettings) -> Unit) {
    Card("Accent colour") {
        SettingsLabel("Accent colour")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ACCENTS.forEach { argb ->
                Box(
                    Modifier.size(42.dp).clip(CircleShape).background(Color(argb.toInt()))
                        .border(if (settings.accent == argb) 3.dp else 0.dp, Color.White, CircleShape)
                        .clickable { set { it.copy(accent = argb) } },
                    contentAlignment = Alignment.Center,
                ) { if (settings.accent == argb) Icon(Icons.Rounded.Check, "Selected", tint = Color.Black) }
            }
        }
    }
    Card("Poster cards") {
        SettingsLabel("Poster cards")
        SwitchRow("Rating badge", "IMDb rating in the corner of each poster.", settings.showRatingBadge) { v -> set { it.copy(showRatingBadge = v) } }
        SwitchRow("In cinema and New badges", "Marks movies now in theaters, and titles from this year.", settings.showCardBadges) { v -> set { it.copy(showCardBadges = v) } }
        SwitchRow("Award labels", "The top award a title won, such as OSCAR or 4 EMMYS (from Wikidata).", settings.showAwardBadge) { v -> set { it.copy(showAwardBadge = v) } }
        SwitchRow("Dub badge", "Marks anime with an English dub airing now (from AniSchedule).", settings.showDubBadge) { v -> set { it.copy(showDubBadge = v) } }
        SwitchRow("Hide titles under posters", null, settings.hidePosterTitles) { v -> set { it.copy(hidePosterTitles = v) } }
        SwitchRow("Watched badge", "A check on the posters of titles you have watched.", settings.watchedBadge) { v -> set { it.copy(watchedBadge = v) } }
        SwitchRow("Watchlist bookmark", "A bookmark on the posters of titles in your list.", settings.watchlistBookmark) { v -> set { it.copy(watchlistBookmark = v) } }
        Label("Size")
        ChipRow(listOf(0.85f to "Small", 1f to "Normal", 1.15f to "Large"), settings.posterScale) { v -> set { it.copy(posterScale = v) } }
        Label("Corners")
        ChipRow(listOf(0 to "Square", 4 to "Slight", 8 to "Round", 14 to "Rounder"), settings.posterRadius) { v -> set { it.copy(posterRadius = v) } }
    }
}

// ---------------- System ----------------

@Composable
private fun StorageSection(repository: Repository, set: ((AppSettings) -> AppSettings) -> Unit) {
    val context = LocalContext.current
    fun say(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    Card("Storage") {
        SettingsLabel("Storage")
        ActionRow("Image cache", "Posters and artwork. They download again as needed.", "Clear") {
            context.imageLoader.memoryCache?.clear()
            context.imageLoader.diskCache?.clear()
            say("Image cache cleared")
        }
        ActionRow("Anime4K shaders", "They download again the next time Anime4K is on.", "Remove") {
            File(context.filesDir, "anime4k").deleteRecursively()
            say("Anime4K shaders removed")
        }
        ActionRow("Watch positions on this device", "For this profile. Stremio's and Trakt's stay.", "Clear") {
            repository.clearResumeHistory()
            say("Watch positions cleared")
        }
        SwitchRow("Delete downloads after I finish watching", "Frees the space once a downloaded movie or episode is watched.", LartDisplay.settings.deleteAfterWatching) { v -> set { it.copy(deleteAfterWatching = v) } }
    }
    Card("Reset") {
        SettingsLabel("Reset")
        ActionRow("Reset settings", "Puts this profile's settings back to how Lart starts. Sign-ins, lists and progress stay.", "Reset") {
            set { AppSettings() }
            say("Settings reset")
        }
    }
}

/** A row with a ghost button on the right, as Harbor's storage rows. */
@Composable
private fun ActionRow(title: String, body: String, action: String, onClick: () -> Unit) {
    Column {
        RowRule()
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            RowText(title, body, Modifier.weight(1f).padding(end = 16.dp))
            HGhostButton(onClick = onClick) { Text(action) }
        }
    }
}

// ---------------- Updates & backup ----------------

@Composable
private fun BackupSection(repository: Repository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    fun say(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { target ->
        if (target == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val json = repository.exportBackup()
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(target)?.use { it.write(json.toByteArray()) } }
            }.onSuccess { say("Backup saved") }.onFailure { say("Backup failed: ${it.message}") }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { source ->
        if (source == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val json = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(source)?.use { it.readBytes().decodeToString() } } ?: error("Couldn't read the file")
                repository.importBackup(json)
            }.onSuccess { say(it) }.onFailure { say(it.message ?: "Restore failed") }
        }
    }

    SettingsSection("Updates", "Lart native ${version ?: "?"}. New previews are posted on GitHub; install them over this one.") {
        HGhostButton(onClick = { uri.openUri("https://github.com/9vsv6/lart/releases") }) { Text("See releases") }
    }
    SettingsSection("Backup & restore", "Saves your profiles, lists, progress, settings and sign-ins to a file. It holds your account keys, so keep it private. Restore also takes a backup from Harbor or Lart on the desktop: its keys, addons, watchlist and watch positions come over.") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HButton(onClick = { exporter.launch("lart-backup.json") }) { Text("Back up") }
            HGhostButton(onClick = { importer.launch(arrayOf("application/json", "*/*")) }) { Text("Restore") }
        }
    }
}

// ---------------- Help & about ----------------

@Composable
private fun AboutSection() {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() }
    SettingsSection("Lart native", "Version ${version ?: "?"}. A native rebuild of Lart (a Harbor fork) for Android, installed beside the regular Lart app while features are rebuilt.") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HButton(onClick = { uriHandler.openUri("https://github.com/9vsv6/lart/issues/new") }) { Text("Report a problem") }
            HGhostButton(onClick = { uriHandler.openUri("https://github.com/9vsv6/lart") }) { Text("Source code") }
        }
    }
    SettingsSection(
        "Licences",
        "Lart includes mpv (libmpv-android, MIT; mpv LGPL), FFmpeg (LGPL/GPL), Jellyfin's Media3 FFmpeg decoder (GPL-3.0) and downloads Anime4K shaders (MIT) on first use. " +
            "Because of the GPL parts, anyone you share this APK with is entitled to Lart's source code.",
    ) {}
}

// ---------------- Debrid and API keys ----------------

@Composable
private fun DebridCard(repository: Repository) {
    val debrid by repository.debrid.collectAsState()
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var service by remember { mutableStateOf(DebridService.RealDebrid) }
    var key by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val current = debrid
    SettingsSection(
        "Debrid",
        if (current != null) "${current.service.label} is set up. Torrent sources play through it."
        else "Torrent sources need a debrid service. Paste your API key; it stays on this device.",
    ) {
        if (current != null) {
            message?.let { Muted(it) }
            HGhostButton(onClick = { scope.launch { repository.clearDebrid(); message = null } }) { Text("Remove") }
        } else {
            OptionScale(DebridService.entries.map { it to it.label }, { it == service }) { service = it }
            HTextField(
                value = key,
                onValueChange = { key = it.trim() },
                label = { Text("${service.label} API key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            message?.let { Text(it, color = Color(0xFFE57373), fontSize = 14.sp) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                HButton(
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
                HLinkButton(onClick = { uriHandler.openUri(service.tokenPage) }) { Text("Get my key") }
            }
        }
    }
}

/** One pasted API key: checks it on save, and shows Remove once set. */
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
    SettingsSection(title, body) {
        if (current != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Key saved (ends …${current.takeLast(4)})", color = LartColors.InkMuted, fontSize = 15.sp, modifier = Modifier.weight(1f))
                HGhostButton(onClick = { scope.launch { onSave(null) } }) { Text("Remove") }
            }
        } else {
            HTextField(
                value = key,
                onValueChange = { key = it.trim() },
                label = { Text(label) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(it, color = Color(0xFFE57373), fontSize = 14.sp) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                HButton(
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
                HLinkButton(onClick = { uriHandler.openUri(link) }) { Text(linkLabel) }
            }
        }
    }
}
