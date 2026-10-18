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
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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

        Card {
            SectionTitle("About")
            Text(
                "Lart native, phase 1 preview. It installs beside the regular Lart app while features are rebuilt natively.",
                color = LartColors.InkMuted,
                fontSize = 13.sp,
            )
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
