package app.lart.mobile.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lart.mobile.R
import app.lart.mobile.data.AniList
import app.lart.mobile.data.AppSettings
import app.lart.mobile.data.DeviceCode
import app.lart.mobile.data.Repository
import app.lart.mobile.data.Simkl
import app.lart.mobile.data.Trakt
import app.lart.mobile.data.TrackerAuth
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/*
 * Settings → Account & setup → Trackers, as Harbor's (trackers-panel.tsx, tracker-connect.tsx,
 * tracker-identity.tsx): "N of 3 connected", then each tracker in turn: either the connect card
 * (logo tile, name, what it does, Connect / About) or "Your … account" with the identity row.
 * Harbor connects with its own registered apps; those ids are Harbor's, so here the first
 * connect asks for the user's own app id (kept per profile).
 */

private enum class Tracker(val label: String, val logo: Int, val blurb: String, val site: String) {
    Trakt("Trakt", R.drawable.logo_trakt, "Scrobbles what you play, syncs your watchlist, and powers recommendations.", "https://trakt.tv"),
    Simkl("Simkl", R.drawable.logo_simkl, "Marks finished shows as watched and keeps plan-to-watch in step across apps.", "https://simkl.com"),
    AniList("AniList", R.drawable.logo_anilist, "Shows your anime lists as rails and keeps episode progress up to date.", "https://anilist.co"),
}

@Composable
fun TrackersPanel(repository: Repository, settings: AppSettings, set: ((AppSettings) -> AppSettings) -> Unit) {
    val trackers by repository.trackers.collectAsState()
    fun authOf(t: Tracker) = when (t) {
        Tracker.Trakt -> trackers.trakt
        Tracker.Simkl -> trackers.simkl
        Tracker.AniList -> trackers.anilist
    }
    val connected = Tracker.entries.count { authOf(it)?.accessToken != null }

    if (connected > 0) StatusDot("$connected of ${Tracker.entries.size} connected")

    // Every tracker on the page, one after another, as their own sections.
    Tracker.entries.forEachIndexed { index, t -> Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
        if (index > 0) RowRule()
        val auth = authOf(t)
        if (auth?.accessToken != null) {
            SettingsSection(
                "Your ${t.label} account",
                when (t) {
                    Tracker.Trakt -> "Lart scrobbles your playback to Trakt and marks episodes watched at 80%."
                    Tracker.Simkl -> "Lart scrobbles your playback to Simkl and marks episodes watched at 80%."
                    Tracker.AniList -> "Anime episodes update your AniList progress at 80%."
                },
            ) {
                TrackerIdentity(
                    tracker = t,
                    handle = auth.user,
                    profileUrl = auth.user?.let {
                        when (t) {
                            Tracker.Trakt -> "https://trakt.tv/users/$it"
                            Tracker.AniList -> "https://anilist.co/user/$it"
                            Tracker.Simkl -> null
                        }
                    },
                    onDisconnect = {
                        val signedOut = auth.copy(accessToken = null, refreshToken = null, user = null)
                        repository.updateTrackers {
                            when (t) {
                                Tracker.Trakt -> it.copy(trakt = signedOut)
                                Tracker.Simkl -> it.copy(simkl = signedOut)
                                Tracker.AniList -> it.copy(anilist = signedOut)
                            }
                        }
                    },
                )
            }
            if (t == Tracker.Trakt) {
                SettingsSection("Continue watching", "What you paused on other devices, as Harbor's \"Paused on Trakt\".") {
                    ToggleRow("Show Trakt's paused titles", null, settings.traktInContinueWatching) { v -> set { it.copy(traktInContinueWatching = v) } }
                }
            }
        } else when (t) {
            Tracker.Trakt -> DeviceConnect(
                tracker = Tracker.Trakt,
                auth = auth,
                needsSecret = true,
                registerUrl = "https://trakt.tv/oauth/applications/new",
                start = { id, _ -> Trakt.deviceCode(id) },
                await = { id, secret, code -> Trakt.awaitToken(id, secret.orEmpty(), code) },
                onSave = { signed -> repository.updateTrackers { it.copy(trakt = signed) } },
                after = { repository.refreshTrackers() },
            )
            Tracker.Simkl -> DeviceConnect(
                tracker = Tracker.Simkl,
                auth = auth,
                needsSecret = false,
                registerUrl = "https://simkl.com/settings/developer/new/",
                start = { id, _ -> Simkl.pin(id) },
                await = { id, _, code -> Simkl.awaitToken(id, code) },
                onSave = { signed -> repository.updateTrackers { it.copy(simkl = signed) } },
                after = {},
            )
            Tracker.AniList -> AniListConnect(repository, auth)
        }
    } }
    Text("Sign-ins belong to the current profile.", color = LartColors.InkSubtle, fontSize = 13.5.sp, modifier = Modifier.padding(top = 4.dp))
}

/** tracker-connect.tsx: a 72 dp logo tile, the name, what it does, Connect and About. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrackerConnectCard(tracker: Tracker, onConnect: () -> Unit) {
    val uri = LocalUriHandler.current
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Box(Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)).background(LartColors.Elevated), contentAlignment = Alignment.Center) {
            Image(painterResource(tracker.logo), null, Modifier.size(40.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(tracker.label, color = LartColors.Ink, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp)
            Text(tracker.blurb, color = LartColors.InkMuted, fontSize = 15.5.sp, lineHeight = 23.sp)
            FlowRow(
                Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HButton(onClick = onConnect) { Text("Connect ${tracker.label}") }
                HLinkButton(onClick = { uri.openUri(tracker.site) }) {
                    Text("About ${tracker.label}")
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(15.dp))
                }
            }
        }
    }
}

/** tracker-identity.tsx: the logo in a circle, @handle, "Service · Connected", Open profile, Disconnect. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrackerIdentity(tracker: Tracker, handle: String?, profileUrl: String?, onDisconnect: () -> Unit) {
    val uri = LocalUriHandler.current
    Column {
        FlowRow(
            Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(56.dp).clip(CircleShape).background(LartColors.Elevated), contentAlignment = Alignment.Center) {
                    Image(painterResource(tracker.logo), null, Modifier.size(28.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        handle?.let { "@$it" } ?: "Connected",
                        color = LartColors.Ink,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(Success))
                        Text("${tracker.label} · Connected", color = LartColors.InkSubtle, fontSize = 15.5.sp)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                profileUrl?.let { url ->
                    HLinkButton(onClick = { uri.openUri(url) }) {
                        Text("Open profile")
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(16.dp))
                    }
                }
                HLinkButton(onClick = onDisconnect) {
                    Icon(Icons.AutoMirrored.Rounded.Logout, null, Modifier.size(16.dp))
                    Text("Disconnect")
                }
            }
        }
        RowRule()
    }
}

/** Where to register the app, and the fields for its id (and secret). */
@Composable
private fun AppSetup(
    tracker: Tracker,
    registerUrl: String,
    hint: String,
    clientId: String,
    onClientId: (String) -> Unit,
    secret: String?,
    onSecret: ((String) -> Unit)?,
) {
    val uri = LocalUriHandler.current
    SettingsSection(
        "Your own ${tracker.label} app",
        "Lart signs in through an app you register with ${tracker.label} (free, about a minute). Paste its details once; they're kept for this profile.",
    ) {
        HTextField(clientId, onClientId, Modifier.fillMaxWidth(), label = { Text("${tracker.label} client ID") }, singleLine = true)
        if (onSecret != null) {
            HTextField(
                secret.orEmpty(),
                onSecret,
                Modifier.fillMaxWidth(),
                label = { Text("${tracker.label} client secret") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
        }
        HLinkButton(onClick = { uri.openUri(registerUrl) }) {
            Text("Register an app")
            Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(15.dp))
        }
        Text(hint, color = LartColors.InkSubtle, fontSize = 13.5.sp)
    }
}

@Composable
private fun DeviceConnect(
    tracker: Tracker,
    auth: TrackerAuth?,
    needsSecret: Boolean,
    registerUrl: String,
    start: suspend (clientId: String, secret: String?) -> DeviceCode,
    await: suspend (clientId: String, secret: String?, code: DeviceCode) -> TrackerAuth,
    onSave: (TrackerAuth?) -> Unit,
    after: suspend () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    var clientId by remember(tracker) { mutableStateOf(auth?.clientId.orEmpty()) }
    var secret by remember(tracker) { mutableStateOf(auth?.clientSecret.orEmpty()) }
    var setup by remember(tracker) { mutableStateOf(false) }
    var code by remember(tracker) { mutableStateOf<DeviceCode?>(null) }
    var error by remember(tracker) { mutableStateOf<String?>(null) }
    var job by remember(tracker) { mutableStateOf<Job?>(null) }
    val ready = clientId.isNotBlank() && (!needsSecret || secret.isNotBlank())

    fun connect() {
        error = null
        job = scope.launch {
            runCatching {
                val device = start(clientId, secret.ifBlank { null })
                code = device
                val signedIn = await(clientId, secret.ifBlank { null }, device)
                onSave(signedIn)
                after()
            }.onFailure { if (it !is kotlinx.coroutines.CancellationException) error = it.message ?: "Sign-in failed" }
            code = null
        }
    }

    val pending = code
    if (pending != null) {
        SettingsSection("Approve Lart on ${tracker.label}", "Go to ${pending.url} and enter this code. Lart connects as soon as you approve.") {
            Text(pending.userCode, color = LartColors.Ink, fontSize = 34.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, letterSpacing = 4.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HButton(onClick = { uri.openUri(pending.url) }) {
                    Text("Open ${tracker.label}")
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(15.dp))
                }
                HGhostButton(onClick = {
                    job?.cancel()
                    code = null
                }) { Text("Cancel") }
            }
        }
        return
    }

    TrackerConnectCard(tracker) { if (ready) connect() else setup = true }
    error?.let { Text(it, color = Color(0xFFE57373), fontSize = 14.sp) }
    if (setup) {
        AppSetup(
            tracker,
            registerUrl,
            "Use urn:ietf:wg:oauth:2.0:oob as the redirect URI.",
            clientId,
            { clientId = it.trim() },
            secret,
            if (needsSecret) ({ value: String -> secret = value.trim() }) else null,
        )
        HButton(onClick = { setup = false; connect() }, enabled = ready) { Text("Continue") }
    }
}

@Composable
private fun AniListConnect(repository: Repository, auth: TrackerAuth?) {
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    var clientId by remember { mutableStateOf(auth?.clientId.orEmpty()) }
    var token by remember { mutableStateOf("") }
    var open by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    TrackerConnectCard(Tracker.AniList) {
        open = true
        if (clientId.isNotBlank()) uri.openUri(AniList.authorizeUrl(clientId))
    }
    if (!open) return
    AppSetup(
        Tracker.AniList,
        "https://anilist.co/settings/developer",
        "Set the app's redirect URL to https://anilist.co/api/v2/oauth/pin.",
        clientId,
        { clientId = it.trim() },
        null,
        null,
    )
    SettingsSection("Paste the token", "After you approve Lart, AniList shows a token. Paste it here.") {
        HGhostButton(onClick = { uri.openUri(AniList.authorizeUrl(clientId)) }, enabled = clientId.isNotBlank()) {
            Text("Get a token")
            Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(15.dp))
        }
        HTextField(
            token,
            { token = it.trim() },
            Modifier.fillMaxWidth(),
            label = { Text("Token") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        error?.let { Text(it, color = Color(0xFFE57373), fontSize = 14.sp) }
        HButton(
            enabled = !busy && clientId.isNotBlank() && token.isNotBlank(),
            onClick = {
                scope.launch {
                    busy = true
                    runCatching { AniList.signIn(clientId, token) }
                        .onSuccess { signed -> repository.updateTrackers { it.copy(anilist = signed) } }
                        .onFailure { error = "That token didn't work: ${it.message?.take(100)}" }
                    busy = false
                }
            },
        ) { Text(if (busy) "Checking…" else "Connect") }
    }
}
