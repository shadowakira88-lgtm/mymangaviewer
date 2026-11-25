package eu.kanade.presentation.kodi

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.more.settings.screen.SettingsAdvancedScreen
import eu.kanade.presentation.more.settings.screen.SettingsAppearanceScreen
import eu.kanade.presentation.more.settings.screen.SettingsBrowseScreen
import eu.kanade.presentation.more.settings.screen.SettingsConnectionScreen
import eu.kanade.presentation.more.settings.screen.SettingsDataScreen
import eu.kanade.presentation.more.settings.screen.SettingsDownloadScreen
import eu.kanade.presentation.more.settings.screen.SettingsEhScreen
import eu.kanade.presentation.more.settings.screen.SettingsLibraryScreen
import eu.kanade.presentation.more.settings.screen.SettingsMangadexScreen
import eu.kanade.presentation.more.settings.screen.SettingsReaderScreen
import eu.kanade.presentation.more.settings.screen.SettingsSecurityScreen
import eu.kanade.presentation.more.settings.screen.SettingsTrackingScreen
import eu.kanade.presentation.more.settings.screen.SettingsVrScreen
import eu.kanade.presentation.more.settings.screen.about.AboutScreen
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.sync.SyncDataJob
import eu.kanade.tachiyomi.data.sync.SyncManager
import eu.kanade.tachiyomi.data.sync.service.SupabaseAuth
import eu.kanade.tachiyomi.data.updater.ElenaUpdater
import eu.kanade.tachiyomi.ui.base.ControllerMapping
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.launch
import tachiyomi.domain.manga.model.MangaCover as DomainMangaCover
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import cafe.adriel.voyager.core.screen.Screen as VoyagerScreen

// KMK -->
private val KodiBg = Color(0xFF0E1216)
private val KodiBlue = Color(0xFF6CA9D9)
private val KodiLine = Color(0xFFE0E0E0)
private val KodiDim = Color(0xFFB0B0B0)

data class KodiListItem(
    val title: String,
    val subtitle: String? = null,
    val cover: DomainMangaCover? = null,
    val onLongClick: (() -> Unit)? = null,
    val onClick: () -> Unit,
)

/**
 * Generic Kodi-style settings page: title on the left, a focusable list on the right.
 * Works with touch and with a gamepad (D-pad to move, A to select).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KodiListPage(title: String, items: List<KodiListItem>, headerActions: List<KodiListItem> = emptyList()) {
    var focused by remember { mutableStateOf(0) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    KodiWallpaperBox(fallback = KodiBg) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Column(
            modifier = Modifier
                .width(220.dp)
                .fillMaxHeight()
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            headerActions.forEach { KodiHeaderButton(it) }
            items.getOrNull(focused)?.subtitle?.let {
                Text(it, color = KodiDim, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
            }
            items.getOrNull(focused)?.cover?.let {
                MangaCover.Book(data = it, modifier = Modifier.padding(top = 12.dp).height(140.dp))
            }
        }
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .border(1.dp, Color(0xFF2A333C), RoundedCornerShape(4.dp)),
        ) {
            itemsIndexed(items) { index, item ->
                var hasFocus by remember { mutableStateOf(false) }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (index == 0) Modifier.focusRequester(first) else Modifier)
                        .onFocusChanged {
                            hasFocus = it.isFocused
                            if (it.isFocused) focused = index
                        }
                        .border(
                            BorderStroke(1.5.dp, if (hasFocus) KodiLine else Color.Transparent),
                            RoundedCornerShape(3.dp),
                        )
                        .onKeyEvent { event ->
                            if (event.type != KeyEventType.KeyUp) {
                                false
                            } else if (event.key == Key.ButtonA) {
                                item.onClick()
                                true
                            } else if (event.key == Key.ButtonY && item.onLongClick != null) {
                                item.onLongClick.invoke()
                                true
                            } else {
                                false
                            }
                        }
                        .combinedClickable(onClick = item.onClick, onLongClick = item.onLongClick)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Text(
                        item.title,
                        color = if (hasFocus) KodiBlue else Color.White,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    item.subtitle?.let {
                        Text(it, color = KodiDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KodiHeaderButton(item: KodiListItem) {
    var hasFocus by remember { mutableStateOf(false) }
    Text(
        text = item.title,
        color = if (hasFocus) KodiBlue else Color.White,
        fontSize = 14.sp,
        modifier = Modifier
            .padding(top = 10.dp)
            .onFocusChanged { hasFocus = it.isFocused }
            .border(
                BorderStroke(1.5.dp, if (hasFocus) KodiLine else Color(0xFF555555)),
                RoundedCornerShape(3.dp),
            )
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp && event.key == Key.ButtonA) {
                    item.onClick()
                    true
                } else {
                    false
                }
            }
            .combinedClickable(onClick = item.onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@Composable
private fun KodiInputDialog(
    title: String,
    initial: String,
    password: Boolean = false,
    onDismiss: () -> Unit,
    onDone: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (password) KeyboardType.Password else KeyboardType.Uri,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = { onDone(text.trim()) }) { Text(stringResource(KMR.strings.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(KMR.strings.action_cancel_kodi)) } },
    )
}

@Composable
private fun KodiCredentialsDialog(
    title: String,
    initialEmail: String,
    onDismiss: () -> Unit,
    onDone: (email: String, password: String) -> Unit,
) {
    var email by remember { mutableStateOf(initialEmail) }
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    singleLine = true,
                    label = { Text(stringResource(KMR.strings.supabase_email)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    label = { Text(stringResource(KMR.strings.supabase_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onDone(email.trim(), password) }) {
                Text(stringResource(KMR.strings.action_ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(KMR.strings.action_cancel_kodi)) } },
    )
}

/** "Setting Komik": the regular Komikku settings categories as a Kodi-style list. */
object KomikSettingsScreen : Screen() {
    @Suppress("unused")
    private fun readResolve(): Any = KomikSettingsScreen

    private class Entry(val title: StringResource, val subtitle: StringResource?, val screen: VoyagerScreen)

    private val entries = listOf(
        Entry(MR.strings.pref_category_appearance, MR.strings.pref_appearance_summary, SettingsAppearanceScreen),
        Entry(MR.strings.pref_category_library, MR.strings.pref_library_summary, SettingsLibraryScreen),
        Entry(MR.strings.pref_category_reader, MR.strings.pref_reader_summary, SettingsReaderScreen),
        Entry(MR.strings.pref_category_downloads, MR.strings.pref_downloads_summary, SettingsDownloadScreen),
        Entry(MR.strings.pref_category_tracking, MR.strings.pref_tracking_summary, SettingsTrackingScreen),
        Entry(KMR.strings.pref_category_connections, KMR.strings.pref_connections_summary, SettingsConnectionScreen),
        Entry(MR.strings.browse, MR.strings.pref_browse_summary, SettingsBrowseScreen),
        Entry(MR.strings.label_data_storage, MR.strings.pref_backup_summary, SettingsDataScreen),
        Entry(MR.strings.pref_category_security, MR.strings.pref_security_summary, SettingsSecurityScreen),
        Entry(SYMR.strings.pref_category_eh, SYMR.strings.pref_ehentai_summary, SettingsEhScreen),
        Entry(SYMR.strings.pref_category_mangadex, SYMR.strings.pref_mangadex_summary, SettingsMangadexScreen),
        Entry(MR.strings.pref_category_advanced, MR.strings.pref_advanced_summary, SettingsAdvancedScreen),
        Entry(MR.strings.pref_category_about, null, AboutScreen()),
    )

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        KodiListPage(
            title = stringResource(KMR.strings.komik_settings),
            items = entries.map { e ->
                KodiListItem(
                    title = stringResource(e.title),
                    subtitle = e.subtitle?.let { stringResource(it) },
                    onClick = { navigator.push(e.screen) },
                )
            },
        )
    }
}

/** "System Settings": VR calibration, account sync and app updates. */
object SystemSettingsScreen : Screen() {
    @Suppress("unused")
    private fun readResolve(): Any = SystemSettingsScreen

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val okText = stringResource(KMR.strings.wallpaper_set_ok)
        val failText = stringResource(KMR.strings.wallpaper_set_failed)
        val pickWallpaper = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                Toast.makeText(
                    context,
                    if (KodiWallpaper.set(context, uri)) okText else failText,
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
        KodiListPage(
            title = stringResource(KMR.strings.system_settings),
            items = listOf(
                KodiListItem(stringResource(KMR.strings.vr_settings)) { navigator.push(SettingsVrScreen) },
                KodiListItem(
                    stringResource(KMR.strings.system_account_sync),
                    stringResource(KMR.strings.system_account_sync_summary),
                ) { navigator.push(AccountSyncScreen) },
                KodiListItem(
                    stringResource(KMR.strings.controller_mapping),
                    stringResource(KMR.strings.controller_mapping_summary),
                ) { navigator.push(ControllerMappingScreen) },
                KodiListItem(
                    stringResource(KMR.strings.wallpaper_setting),
                    stringResource(KMR.strings.wallpaper_setting_summary),
                ) { pickWallpaper.launch("image/*") },
                KodiListItem(
                    stringResource(KMR.strings.wallpaper_reset),
                    stringResource(KMR.strings.wallpaper_reset_summary),
                ) { KodiWallpaper.clear(context) },
                KodiListItem(
                    stringResource(KMR.strings.system_update),
                    stringResource(KMR.strings.system_update_summary),
                ) { navigator.push(UpdateScreen) },
            ),
        )
    }
}

object AccountSyncScreen : Screen() {
    @Suppress("unused")
    private fun readResolve(): Any = AccountSyncScreen

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val syncPrefs = remember { Injekt.get<SyncPreferences>() }
        val auth = remember { SupabaseAuth(syncPrefs) }
        val url by syncPrefs.supabaseUrl().collectAsState()
        val anon by syncPrefs.supabaseAnonKey().collectAsState()
        val email by syncPrefs.supabaseEmail().collectAsState()
        val refresh by syncPrefs.supabaseRefreshToken().collectAsState()
        val signedIn = refresh.isNotBlank()

        var dialog by remember { mutableStateOf<String?>(null) }

        val okText = stringResource(KMR.strings.supabase_signed_in_ok)
        val confirmText = stringResource(KMR.strings.supabase_confirm_email)
        val setFirst = stringResource(KMR.strings.supabase_set_first)
        val syncStarted = stringResource(KMR.strings.supabase_sync_started)
        val errorFmt = stringResource(KMR.strings.supabase_error, "%s")

        KodiListPage(
            title = stringResource(KMR.strings.system_account_sync),
            items = buildList {
                add(
                    KodiListItem(
                        if (signedIn) {
                            stringResource(KMR.strings.supabase_status_signed_in, email)
                        } else {
                            stringResource(KMR.strings.supabase_status_signed_out)
                        },
                    ) {},
                )
                add(
                    KodiListItem(
                        stringResource(KMR.strings.supabase_url),
                        url.ifBlank { "https://xxxx.supabase.co" },
                    ) { dialog = "url" },
                )
                add(
                    KodiListItem(
                        stringResource(KMR.strings.supabase_anon_key),
                        if (anon.isBlank()) "-" else "••••" + anon.takeLast(4),
                    ) { dialog = "anon" },
                )
                if (!signedIn) {
                    add(
                        KodiListItem(stringResource(KMR.strings.supabase_sign_in)) {
                            if (!auth.isConfigured) context.toast(setFirst) else dialog = "signin"
                        },
                    )
                    add(
                        KodiListItem(stringResource(KMR.strings.supabase_sign_up)) {
                            if (!auth.isConfigured) context.toast(setFirst) else dialog = "signup"
                        },
                    )
                } else {
                    add(KodiListItem(stringResource(KMR.strings.supabase_sync_now)) {
                        syncPrefs.syncService().set(SyncManager.SyncService.SUPABASE.value)
                        SyncDataJob.startNow(context, manual = true)
                        context.toast(syncStarted)
                    })
                    add(KodiListItem(stringResource(KMR.strings.supabase_sign_out)) { auth.signOut() })
                }
            },
        )

        when (dialog) {
            "url" -> KodiInputDialog(
                stringResource(KMR.strings.supabase_url),
                url,
                onDismiss = { dialog = null },
            ) {
                syncPrefs.supabaseUrl().set(it)
                dialog = null
            }
            "anon" -> KodiInputDialog(
                stringResource(KMR.strings.supabase_anon_key),
                anon,
                onDismiss = { dialog = null },
            ) {
                syncPrefs.supabaseAnonKey().set(it)
                dialog = null
            }
            "signin", "signup" -> {
                val isSignUp = dialog == "signup"
                KodiCredentialsDialog(
                    stringResource(if (isSignUp) KMR.strings.supabase_sign_up else KMR.strings.supabase_sign_in),
                    email,
                    onDismiss = { dialog = null },
                ) { e, p ->
                    dialog = null
                    scope.launch {
                        val msg = try {
                            if (isSignUp) {
                                if (auth.signUp(e, p)) {
                                    syncPrefs.syncService().set(SyncManager.SyncService.SUPABASE.value)
                                    okText
                                } else {
                                    confirmText
                                }
                            } else {
                                auth.signIn(e, p)
                                syncPrefs.syncService().set(SyncManager.SyncService.SUPABASE.value)
                                okText
                            }
                        } catch (ex: Exception) {
                            errorFmt.replace("%s", ex.message.orEmpty())
                        }
                        context.toast(msg)
                    }
                }
            }
        }
    }
}

object UpdateScreen : Screen() {
    @Suppress("unused")
    private fun readResolve(): Any = UpdateScreen

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val repoPref = remember { Injekt.get<UiPreferences>().updateRepo() }
        val repo by repoPref.collectAsState()
        var status by remember { mutableStateOf<String?>(null) }
        var release by remember { mutableStateOf<ElenaUpdater.Release?>(null) }
        var showRepoDialog by remember { mutableStateOf(false) }

        val checking = stringResource(KMR.strings.system_update_checking)
        val latest = stringResource(KMR.strings.system_update_latest)
        val downloadingLabel = stringResource(KMR.strings.system_update_started)
        val installingLabel = stringResource(KMR.strings.system_update_installing)
        val permissionLabel = stringResource(KMR.strings.system_update_permission)
        var downloadPct by remember { mutableStateOf<Int?>(null) }
        val failedFmt = stringResource(KMR.strings.system_update_failed, "%s")
        val availableFmt = stringResource(KMR.strings.system_update_available, "%s")

        KodiListPage(
            title = stringResource(KMR.strings.system_update),
            items = buildList {
                add(
                    KodiListItem(
                        stringResource(
                            KMR.strings.system_update_current,
                            "${BuildConfig.VERSION_NAME} (${BuildConfig.COMMIT_SHA})",
                        ),
                    ) {},
                )
                add(KodiListItem(stringResource(KMR.strings.system_update_server), repo) { showRepoDialog = true })
                add(
                    KodiListItem(stringResource(KMR.strings.system_update_check), status) {
                        status = checking
                        release = null
                        scope.launch {
                            status = try {
                                val r = ElenaUpdater.check(repo)
                                if (r.isNewer) {
                                    release = r
                                    availableFmt.replace("%s", r.commit)
                                } else {
                                    latest
                                }
                            } catch (e: Exception) {
                                failedFmt.replace("%s", e.message.orEmpty())
                            }
                        }
                    },
                )
                release?.let { r ->
                    add(
                        KodiListItem(
                            title = downloadPct?.let { "$downloadingLabel $it%" }
                                ?: availableFmt.replace("%s", r.commit),
                        ) {
                            if (downloadPct == null) {
                                downloadPct = 0
                                scope.launch {
                                    try {
                                        val ok = ElenaUpdater.downloadAndInstall(context, r) { downloadPct = it }
                                        status = if (ok) installingLabel else permissionLabel
                                    } catch (e: Exception) {
                                        status = failedFmt.replace("%s", e.message.orEmpty())
                                    } finally {
                                        downloadPct = null
                                    }
                                }
                            }
                        },
                    )
                }
            },
        )

        if (showRepoDialog) {
            KodiInputDialog(
                stringResource(KMR.strings.system_update_server),
                repo,
                onDismiss = { showRepoDialog = false },
            ) {
                repoPref.set(it.ifBlank { ElenaUpdater.DEFAULT_REPO })
                showRepoDialog = false
            }
        }
    }
}
object ControllerMappingScreen : Screen() {
    @Suppress("unused")
    private fun readResolve(): Any = ControllerMappingScreen

    @Composable
    override fun Content() {
        var capturing by remember { mutableStateOf<ControllerMapping.Action?>(null) }
        // Re-read the labels after a key was assigned.
        var version by remember { mutableStateOf(0) }

        fun title(a: ControllerMapping.Action): StringResource = when (a) {
            ControllerMapping.Action.UP -> KMR.strings.controller_dpad_up
            ControllerMapping.Action.DOWN -> KMR.strings.controller_dpad_down
            ControllerMapping.Action.LEFT -> KMR.strings.controller_dpad_left
            ControllerMapping.Action.RIGHT -> KMR.strings.controller_dpad_right
            ControllerMapping.Action.CONFIRM -> KMR.strings.controller_confirm
            ControllerMapping.Action.BACK -> KMR.strings.controller_back
            ControllerMapping.Action.NEXT_PAGE -> KMR.strings.controller_next_page
            ControllerMapping.Action.PREV_PAGE -> KMR.strings.controller_prev_page
            ControllerMapping.Action.MAGNIFIER -> KMR.strings.controller_magnifier
        }

        val labelTitles = ControllerMapping.Action.entries.associateWith { stringResource(title(it)) }

        KodiListPage(
            title = stringResource(KMR.strings.controller_mapping),
            items = buildList {
                ControllerMapping.Action.entries.forEach { action ->
                    add(
                        KodiListItem(
                            labelTitles.getValue(action),
                            // reading `version` makes the list refresh after a change
                            ControllerMapping.label(ControllerMapping.keyFor(action)).also { version },
                        ) { capturing = action },
                    )
                }
                add(
                    KodiListItem(stringResource(KMR.strings.controller_reset)) {
                        ControllerMapping.reset()
                        version++
                    },
                )
            },
        )

        capturing?.let { action ->
            val focus = remember { FocusRequester() }
            DisposableEffect(Unit) {
                ControllerMapping.capturing = true
                onDispose { ControllerMapping.capturing = false }
            }
            LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xE6000000))
                    .focusRequester(focus)
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyUp) {
                            ControllerMapping.setKey(action, event.nativeKeyEvent.keyCode)
                            version++
                            capturing = null
                        }
                        true
                    }
                    .pointerInput(Unit) { detectTapGestures { capturing = null } },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(KMR.strings.controller_press_button, labelTitles.getValue(action)),
                    color = Color.White,
                    fontSize = 18.sp,
                )
            }
        }
    }
}
// KMK <--
