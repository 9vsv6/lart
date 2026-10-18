package app.lart.mobile.ui

import androidx.compose.foundation.background
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

@Composable
fun SettingsScreen(repository: Repository) {
    val account by repository.account.collectAsState()
    val addons by repository.addons.collectAsState()
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().background(LartColors.Canvas).statusBarsPadding().verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)

        Card {
            SectionTitle("Stremio account")
            val current = account
            if (current != null) {
                Text("Signed in as ${current.email}", color = LartColors.InkMuted, fontSize = 14.sp)
                OutlinedButton(onClick = { scope.launch { busy = true; repository.signOut(); busy = false } }, enabled = !busy) {
                    Text("Sign out")
                }
            } else {
                Text(
                    "Sign in to use your Stremio addons, library and watch progress.",
                    color = LartColors.InkMuted,
                    fontSize = 14.sp,
                )
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

        DebridCard(repository)

        Card {
            SectionTitle("Playback")
            val autoPlay by repository.autoPlay.collectAsState()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Auto-play the best source", fontSize = 15.sp, color = LartColors.Ink)
                    Text(
                        "Skips the list of sources. Lart ranks them by quality, size and whether your debrid has them cached.",
                        fontSize = 12.sp,
                        color = LartColors.InkSubtle,
                    )
                }
                Switch(checked = autoPlay, onCheckedChange = repository::setAutoPlay)
            }
        }

        Card {
            SectionTitle("About")
            Text(
                "Lart native, phase 2 preview. It installs beside the regular Lart app while features are rebuilt natively.",
                color = LartColors.InkMuted,
                fontSize = 13.sp,
            )
        }
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
