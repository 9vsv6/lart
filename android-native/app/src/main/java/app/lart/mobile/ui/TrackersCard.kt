package app.lart.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lart.mobile.data.AniList
import app.lart.mobile.data.DeviceCode
import app.lart.mobile.data.Repository
import app.lart.mobile.data.Simkl
import app.lart.mobile.data.Trakt
import app.lart.mobile.data.TrackerAuth
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Settings → Trackers, like Harbor's: Trakt and Simkl scrobble what you watch (80% marks
 * it watched) and Trakt's paused titles appear in Continue watching; AniList keeps
 * anime episode progress. Each needs an app registered with the tracker by the user.
 */
@Composable
fun TrackersCard(repository: Repository) {
    val trackers by repository.trackers.collectAsState()
    Column(
        Modifier.fillMaxWidth().background(LartColors.Surface, RoundedCornerShape(18.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionTitle("Trackers")
        Text(
            "Sign-ins belong to the current profile. Each tracker needs a free API app you register yourself; paste its client ID below.",
            color = LartColors.InkMuted,
            fontSize = 13.sp,
        )
        DeviceTracker(
            name = "Trakt",
            auth = trackers.trakt,
            needsSecret = true,
            registerUrl = "https://trakt.tv/oauth/applications/new",
            registerHint = "Redirect URI: urn:ietf:wg:oauth:2.0:oob",
            start = { id, _ -> Trakt.deviceCode(id) },
            await = { id, secret, code -> Trakt.awaitToken(id, secret.orEmpty(), code) },
            onSave = { auth -> repository.updateTrackers { it.copy(trakt = auth) } },
            after = { repository.refreshTrackers() },
        )
        DeviceTracker(
            name = "Simkl",
            auth = trackers.simkl,
            needsSecret = false,
            registerUrl = "https://simkl.com/settings/developer/new/",
            registerHint = "Redirect URI: urn:ietf:wg:oauth:2.0:oob",
            start = { id, _ -> Simkl.pin(id) },
            await = { id, _, code -> Simkl.awaitToken(id, code) },
            onSave = { auth -> repository.updateTrackers { it.copy(simkl = auth) } },
            after = {},
        )
        AniListTracker(repository, trackers.anilist)
    }
}

@Composable
private fun DeviceTracker(
    name: String,
    auth: TrackerAuth?,
    needsSecret: Boolean,
    registerUrl: String,
    registerHint: String,
    start: suspend (clientId: String, secret: String?) -> DeviceCode,
    await: suspend (clientId: String, secret: String?, code: DeviceCode) -> TrackerAuth,
    onSave: (TrackerAuth?) -> Unit,
    after: suspend () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    var clientId by remember { mutableStateOf(auth?.clientId.orEmpty()) }
    var secret by remember { mutableStateOf(auth?.clientSecret.orEmpty()) }
    var code by remember { mutableStateOf<DeviceCode?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(name, color = LartColors.Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        if (auth?.accessToken != null) {
            Text("Connected${auth.user?.let { " as $it" } ?: ""}.", color = LartColors.InkMuted, fontSize = 13.sp)
            OutlinedButton(onClick = { onSave(null) }) { Text("Disconnect") }
            return@Column
        }
        val pending = code
        if (pending != null) {
            Text("Go to ${pending.url} and enter this code:", color = LartColors.InkMuted, fontSize = 13.sp)
            Text(pending.userCode, color = LartColors.Accent, fontSize = 26.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { uri.openUri(pending.url) }) { Text("Open $name") }
                TextButton(onClick = {
                    job?.cancel()
                    code = null
                }) { Text("Cancel") }
            }
            Text("Waiting for you to approve…", color = LartColors.InkSubtle, fontSize = 12.sp)
            return@Column
        }
        OutlinedTextField(
            value = clientId,
            onValueChange = { clientId = it.trim() },
            label = { Text("$name client ID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (needsSecret) {
            OutlinedTextField(
                value = secret,
                onValueChange = { secret = it.trim() },
                label = { Text("$name client secret") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        error?.let { Text(it, color = Color(0xFFE57373), fontSize = 13.sp) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = clientId.isNotBlank() && (!needsSecret || secret.isNotBlank()),
                onClick = {
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
                },
            ) { Text("Connect") }
            TextButton(onClick = { uri.openUri(registerUrl) }) { Text("Register an app") }
        }
        Text(registerHint, color = LartColors.InkSubtle, fontSize = 11.sp)
    }
}

@Composable
private fun AniListTracker(repository: Repository, auth: TrackerAuth?) {
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    var clientId by remember { mutableStateOf(auth?.clientId.orEmpty()) }
    var token by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("AniList", color = LartColors.Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        if (auth?.accessToken != null) {
            Text("Connected${auth.user?.let { " as $it" } ?: ""}. Anime episodes update your list at 80%.", color = LartColors.InkMuted, fontSize = 13.sp)
            OutlinedButton(onClick = { repository.updateTrackers { it.copy(anilist = null) } }) { Text("Disconnect") }
            return@Column
        }
        OutlinedTextField(
            value = clientId,
            onValueChange = { clientId = it.trim() },
            label = { Text("AniList client ID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = clientId.isNotBlank(), onClick = { uri.openUri(AniList.authorizeUrl(clientId)) }) { Text("Get token") }
            TextButton(onClick = { uri.openUri("https://anilist.co/settings/developer") }) { Text("Register an app") }
        }
        Text("Set the app's redirect URL to https://anilist.co/api/v2/oauth/pin, then paste the token AniList shows.", color = LartColors.InkSubtle, fontSize = 11.sp)
        OutlinedTextField(
            value = token,
            onValueChange = { token = it.trim() },
            label = { Text("Token") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let { Text(it, color = Color(0xFFE57373), fontSize = 13.sp) }
        Button(
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
