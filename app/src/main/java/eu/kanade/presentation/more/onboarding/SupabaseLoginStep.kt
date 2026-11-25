package eu.kanade.presentation.more.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.tachiyomi.data.sync.SyncManager
import eu.kanade.tachiyomi.data.sync.service.SupabaseAuth
import kotlinx.coroutines.launch
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->
/** First-run page: sign in to the Supabase account so the library can sync across phones. Optional. */
internal class SupabaseLoginStep : OnboardingStep {

    // Signing in is optional, so the flow can always be finished.
    override val isComplete: Boolean = true

    @Composable
    override fun Content() {
        val syncPrefs = remember { Injekt.get<SyncPreferences>() }
        val auth = remember { SupabaseAuth(syncPrefs) }
        val scope = rememberCoroutineScope()

        var url by remember { mutableStateOf(syncPrefs.supabaseUrl().get()) }
        var anon by remember { mutableStateOf(syncPrefs.supabaseAnonKey().get()) }
        var email by remember { mutableStateOf(syncPrefs.supabaseEmail().get()) }
        var password by remember { mutableStateOf("") }
        var message by remember { mutableStateOf<String?>(null) }
        var busy by remember { mutableStateOf(false) }

        val okText = stringResource(KMR.strings.supabase_signed_in_ok)
        val confirmText = stringResource(KMR.strings.supabase_confirm_email)
        val errorFmt = stringResource(KMR.strings.supabase_error, "%s")

        fun submit(signUp: Boolean) {
            syncPrefs.supabaseUrl().set(url.trim())
            syncPrefs.supabaseAnonKey().set(anon.trim())
            busy = true
            scope.launch {
                message = try {
                    if (signUp) {
                        if (auth.signUp(email.trim(), password)) {
                            syncPrefs.syncService().set(SyncManager.SyncService.SUPABASE.value)
                            okText
                        } else {
                            confirmText
                        }
                    } else {
                        auth.signIn(email.trim(), password)
                        syncPrefs.syncService().set(SyncManager.SyncService.SUPABASE.value)
                        okText
                    }
                } catch (e: Exception) {
                    errorFmt.replace("%s", e.message.orEmpty())
                }
                busy = false
            }
        }

        Column(
            // The onboarding page already scrolls, so no verticalScroll here (it would crash).
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(KMR.strings.system_account_sync_summary))
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                singleLine = true,
                label = { Text(stringResource(KMR.strings.supabase_url)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = anon,
                onValueChange = { anon = it },
                singleLine = true,
                label = { Text(stringResource(KMR.strings.supabase_anon_key)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                singleLine = true,
                label = { Text(stringResource(KMR.strings.supabase_email)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                singleLine = true,
                label = { Text(stringResource(KMR.strings.supabase_password)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !busy, onClick = { submit(false) }) {
                    Text(stringResource(KMR.strings.supabase_sign_in))
                }
                OutlinedButton(enabled = !busy, onClick = { submit(true) }) {
                    Text(stringResource(KMR.strings.supabase_sign_up))
                }
            }
            message?.let { Text(it) }
            Text(stringResource(KMR.strings.onboarding_login_skip_hint))
        }
    }
}
// KMK <--
