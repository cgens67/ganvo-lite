package com.ganvo.music.ui.screens.settings

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.Ganvo.innertube.utils.parseCookieString
import com.ganvo.music.BuildConfig
import com.ganvo.music.LocalPlayerAwareWindowInsets
import com.ganvo.music.R
import com.ganvo.music.constants.AccountNameKey
import com.ganvo.music.constants.InnerTubeCookieKey
import com.ganvo.music.ui.component.AvatarPreferenceManager
import com.ganvo.music.ui.component.AvatarSelection
import com.ganvo.music.ui.component.ChangelogScreen
import com.ganvo.music.ui.component.EmptyPlaceholder
import com.ganvo.music.ui.component.IconButton
import com.ganvo.music.ui.component.PreferenceEntry
import com.ganvo.music.ui.component.TopSearch
import com.ganvo.music.ui.screens.search.SuggestionItem
import com.ganvo.music.ui.utils.backToMain
import com.ganvo.music.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URL

@SuppressLint("ObsoleteSdkInt")
fun getAppVersion(context: Context): String {
    return try {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        packageInfo.versionName ?: "Unknown"
    } catch (e: Exception) {
        "Unknown"
    }
}

@Composable
fun VersionCard(uriHandler: UriHandler) {
    val context = LocalContext.current
    val appVersion = remember { getAppVersion(context) }
    Spacer(Modifier.height(16.dp))
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(72.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        onClick = { uriHandler.openUri("https://github.com/Ganvo/Ganvo/releases/latest") }
    ) {
        Row(modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier
                .size(44.dp)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    CircleShape
                ), contentAlignment = Alignment.Center) {
                Icon(painter = painterResource(R.drawable.info), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = stringResource(R.string.Version), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(text = appVersion, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
fun UpdateCard(latestVersion: String = "") {
    val context = LocalContext.current
    var showUpdateCard by remember { mutableStateOf(false) }
    var currentLatestVersion by remember { mutableStateOf(latestVersion) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val newVersion = checkForUpdates()
        if (newVersion != null && isNewerVersion(newVersion, BuildConfig.VERSION_NAME)) {
            showUpdateCard = true
            currentLatestVersion = newVersion
        }
    }
    if (showDownloadDialog) { UpdateDownloadDialog(latestVersion = currentLatestVersion, onDismiss = { showDownloadDialog = false }) }
    if (showUpdateCard) {
        Spacer(Modifier.height(25.dp))
        ElevatedCard(
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(170.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            shape = RoundedCornerShape(32.dp),
            onClick = { showDownloadDialog = true }
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.Center) {
                Spacer(Modifier.height(3.dp))
                Text(text = "${stringResource(R.string.NewVersion)}: $currentLatestVersion", style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.height(8.dp))
                Text(text = "${stringResource(R.string.warn)} ", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp, fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                Text(text = stringResource(R.string.tap_to_update), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp), color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun UpdateDownloadDialog(latestVersion: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var downloadProgress by remember { mutableStateOf(0f) }
    var downloadStatus by remember { mutableStateOf(DownloadStatus.NOT_STARTED) }
    var downloadedApkUri by remember { mutableStateOf<Uri?>(null) }
    val downloadScope = rememberCoroutineScope()
    val installPermissionLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.StartActivityForResult()) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (context.packageManager.canRequestPackageInstalls() && downloadedApkUri != null) { installApk(context, downloadedApkUri!!) }
        }
    }
    Dialog(onDismissRequest = { if (downloadStatus != DownloadStatus.DOWNLOADING) { onDismiss() } }) {
        Card(modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp), shape = RoundedCornerShape(28.dp)) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = stringResource(id = R.string.update_version, latestVersion), style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(16.dp))
                when (downloadStatus) {
                    DownloadStatus.NOT_STARTED -> {
                        Text(stringResource(R.string.download_question))
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                            Button(onClick = {
                                downloadStatus = DownloadStatus.DOWNLOADING
                                downloadScope.launch {
                                    downloadedApkUri = downloadApk(context, latestVersion) { progress ->
                                        downloadProgress = progress
                                        if (progress >= 1f) { downloadStatus = DownloadStatus.COMPLETED }
                                    }
                                }
                            }) { Text(stringResource(R.string.download)) }
                        }
                    }
                    DownloadStatus.DOWNLOADING -> {
                        Text(stringResource(R.string.downloading_update))
                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(progress = { downloadProgress }, modifier = Modifier.fillMaxWidth())
                        Text(text = "${(downloadProgress * 100).toInt()}%", modifier = Modifier.padding(top = 8.dp))
                    }
                    DownloadStatus.COMPLETED -> {
                        Text(stringResource(R.string.download_completed))
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
                            Button(onClick = {
                                if (downloadedApkUri != null) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
                                        installPermissionLauncher.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).setData("package:${context.packageName}".toUri()))
                                    } else { installApk(context, downloadedApkUri!!) }
                                }
                            }) { Text(stringResource(R.string.install)) }
                        }
                    }
                    DownloadStatus.ERROR -> {
                        Text(stringResource(R.string.download_update_error))
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onDismiss) { Text(stringResource(R.string.close)) }
                    }
                }
            }
        }
    }
}

enum class DownloadStatus { NOT_STARTED, DOWNLOADING, COMPLETED, ERROR }

suspend fun downloadApk(context: Context, version: String, onProgressUpdate: (Float) -> Unit): Uri? = withContext(Dispatchers.IO) {
    try {
        val apkUrl = "https://github.com/Ganvo/Ganvo/releases/download/$version/app-release.apk"
        val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val apkFile = File(downloadDir, "app-release-$version.apk")
        if (apkFile.exists()) { apkFile.delete() }
        val request = DownloadManager.Request(apkUrl.toUri()).setTitle(context.getString(R.string.downloading_app_update, version)).setDestinationUri(Uri.fromFile(apkFile))
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)
        var isDownloading = true
        while (isDownloading) {
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor = downloadManager.query(query)
            if (cursor.moveToFirst()) {
                val status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
                val bytesDownloaded = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val bytesTotal = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                if (status == DownloadManager.STATUS_SUCCESSFUL) { isDownloading = false; onProgressUpdate(1f) }
                else if (status == DownloadManager.STATUS_FAILED) { isDownloading = false; return@withContext null }
                else if (bytesTotal > 0) { onProgressUpdate(bytesDownloaded.toFloat() / bytesTotal.toFloat()) }
            }
            cursor.close(); delay(100)
        }
        return@withContext FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
    } catch (e: Exception) { return@withContext null }
}

fun installApk(context: Context, apkUri: Uri) {
    val installIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(apkUri, "application/vnd.android.package-archive")
        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
    }
    context.startActivity(installIntent)
}

suspend fun checkForUpdates(): String? = withContext(Dispatchers.IO) {
    try {
        val json = URL("https://api.github.com/repos/Ganvo/Ganvo/releases/latest").readText()
        JSONObject(json).getString("tag_name")
    } catch (e: Exception) { null }
}

fun isNewerVersion(remoteVersion: String, currentVersion: String): Boolean {
    val remote = remoteVersion.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
    val current = currentVersion.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(remote.size, current.size)) {
        val r = remote.getOrNull(i) ?: 0
        val c = current.getOrNull(i) ?: 0
        if (r > c) return true
        if (r < c) return false
    }
    return false
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsGridCard(icon: Int, title: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.15f),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        onClick = onClick
    ) {
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier
                .size(56.dp)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    CircleShape
                ), contentAlignment = Alignment.Center) {
                Icon(painter = painterResource(id = icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

data class SettingItemResolved(
    val title: String,
    val iconRes: Int,
    val route: String,
    val keywords: List<String> = emptyList()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    latestVersion: Long,
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val uriHandler = LocalUriHandler.current
    var showChangelogSheet by remember { mutableStateOf(false) }
    
    // Search states
    var isSearching by rememberSaveable { mutableStateOf(false) }
    var active by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isSearching) {
        active = isSearching
    }

    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val avatarManager = remember { AvatarPreferenceManager(context) }
    val currentSelection by avatarManager.getAvatarSelection.collectAsState(initial = AvatarSelection.Default)
    val accountName by rememberPreference(AccountNameKey, "")
    val innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    val isLoggedIn = remember(innerTubeCookie) { "SAPISID" in parseCookieString(innerTubeCookie) }

    val settingsSearchHistoryPref = rememberPreference(
        key = stringPreferencesKey("settings_search_history"),
        defaultValue = ""
    )

    var searchHistory by remember {
        mutableStateOf(
            settingsSearchHistoryPref.value.split("|").filter { it.isNotBlank() }
        )
    }

    fun addSearchHistory(query: String) {
        if (query.isBlank()) return
        val newHistory = (listOf(query) + searchHistory.filter { it != query }).take(10)
        searchHistory = newHistory
        settingsSearchHistoryPref.value = newHistory.joinToString("|")
    }

    fun removeSearchHistory(query: String) {
        val newHistory = searchHistory.filter { it != query }
        searchHistory = newHistory
        settingsSearchHistoryPref.value = newHistory.joinToString("|")
    }

    val allSettings = remember {
        listOf(
            // Appearance
            SettingItemResolved(context.getString(R.string.enable_dynamic_theme), R.drawable.palette, "settings/appearance"),
            SettingItemResolved(context.getString(R.string.dark_theme), R.drawable.dark_mode, "settings/appearance"),
            SettingItemResolved(context.getString(R.string.pure_black), R.drawable.contrast, "settings/appearance"),
            SettingItemResolved(context.getString(R.string.player_background_style), R.drawable.gradient, "settings/appearance"),
            SettingItemResolved(context.getString(R.string.customize_thumbnail_corner_radius), R.drawable.line_curve, "settings/appearance"),
            SettingItemResolved(context.getString(R.string.player_slider_style), R.drawable.sliders, "settings/appearance"),
            SettingItemResolved(context.getString(R.string.enable_swipe_thumbnail), R.drawable.swipe, "settings/appearance"),
            SettingItemResolved(context.getString(R.string.player_text_alignment), R.drawable.format_align_center, "settings/appearance"),
            SettingItemResolved(context.getString(R.string.lyrics_text_position), R.drawable.lyrics, "settings/appearance"),
            SettingItemResolved(context.getString(R.string.lyrics_click_change), R.drawable.lyrics, "settings/appearance"),
            SettingItemResolved(context.getString(R.string.default_open_tab), R.drawable.nav_bar, "settings/appearance"),
            SettingItemResolved(context.getString(R.string.default_lib_chips), R.drawable.tab, "settings/appearance"),
            SettingItemResolved(context.getString(R.string.slim_navbar), R.drawable.nav_bar, "settings/appearance"),
            SettingItemResolved(context.getString(R.string.grid_cell_size), R.drawable.grid_view, "settings/appearance"),
            SettingItemResolved(context.getString(R.string.avatar_selection), R.drawable.person, "settings/appearance"),

            // Account
            SettingItemResolved(context.getString(R.string.login), R.drawable.login, "settings/account"),
            SettingItemResolved(context.getString(R.string.advanced_login), R.drawable.token, "settings/account"),
            SettingItemResolved(context.getString(R.string.use_login_for_browse), R.drawable.person, "settings/account"),
            SettingItemResolved(context.getString(R.string.ytm_sync), R.drawable.cached, "settings/account"),
            SettingItemResolved(context.getString(R.string.discord_integration), R.drawable.discord, "settings/discord"),

            // Content
            SettingItemResolved(context.getString(R.string.content_language), R.drawable.language, "settings/content"),
            SettingItemResolved(context.getString(R.string.content_country), R.drawable.location_on, "settings/content"),
            SettingItemResolved(context.getString(R.string.hide_explicit), R.drawable.explicit, "settings/content"),
            SettingItemResolved(context.getString(R.string.notification), R.drawable.notification_on, "settings/content"),
            SettingItemResolved(context.getString(R.string.app_language), R.drawable.language, "settings/content"),
            SettingItemResolved(context.getString(R.string.enable_proxy), R.drawable.wifi_proxy, "settings/content"),
            SettingItemResolved(context.getString(R.string.enable_lrclib), R.drawable.lyrics, "settings/content"),
            SettingItemResolved(context.getString(R.string.enable_kugou), R.drawable.lyrics, "settings/content"),
            SettingItemResolved(context.getString(R.string.set_first_lyrics_provider), R.drawable.lyrics, "settings/content"),
            SettingItemResolved(context.getString(R.string.top_length), R.drawable.trending_up, "settings/content"),
            SettingItemResolved(context.getString(R.string.set_quick_picks), R.drawable.home_outlined, "settings/content"),
            SettingItemResolved(context.getString(R.string.history_duration), R.drawable.history, "settings/content"),

            // Player & Audio
            SettingItemResolved(context.getString(R.string.audio_quality), R.drawable.graphic_eq, "settings/player"),
            SettingItemResolved(context.getString(R.string.skip_silence), R.drawable.fast_forward, "settings/player"),
            SettingItemResolved(context.getString(R.string.audio_normalization), R.drawable.volume_up, "settings/player"),
            SettingItemResolved(context.getString(R.string.persistent_queue), R.drawable.queue_music, "settings/player"),
            SettingItemResolved(context.getString(R.string.auto_load_more), R.drawable.playlist_add, "settings/player"),
            SettingItemResolved(context.getString(R.string.enable_similar_content), R.drawable.similar, "settings/player"),
            SettingItemResolved(context.getString(R.string.auto_skip_next_on_error), R.drawable.skip_next, "settings/player"),
            SettingItemResolved(context.getString(R.string.stop_music_on_task_clear), R.drawable.clear_all, "settings/player"),

            // Storage
            SettingItemResolved(context.getString(R.string.clear_all_downloads), R.drawable.delete, "settings/storage"),
            SettingItemResolved(context.getString(R.string.clear_song_cache), R.drawable.delete, "settings/storage"),
            SettingItemResolved(context.getString(R.string.max_cache_size), R.drawable.settings, "settings/storage"),
            SettingItemResolved(context.getString(R.string.clear_image_cache), R.drawable.delete, "settings/storage"),

            // Privacy
            SettingItemResolved(context.getString(R.string.pause_listen_history), R.drawable.history, "settings/privacy"),
            SettingItemResolved(context.getString(R.string.clear_listen_history), R.drawable.delete_history, "settings/privacy"),
            SettingItemResolved(context.getString(R.string.pause_search_history), R.drawable.search_off, "settings/privacy"),
            SettingItemResolved(context.getString(R.string.clear_search_history), R.drawable.clear_all, "settings/privacy"),
            SettingItemResolved(context.getString(R.string.disable_screenshot), R.drawable.screenshot, "settings/privacy"),

            // Backup & Restore
            SettingItemResolved(context.getString(R.string.backup), R.drawable.backup, "settings/backup_restore"),
            SettingItemResolved(context.getString(R.string.restore), R.drawable.restore, "settings/backup_restore"),
            SettingItemResolved(context.getString(R.string.visitor_data_title), R.drawable.replay, "settings/backup_restore"),

            // About & Changelog
            SettingItemResolved(context.getString(R.string.Version), R.drawable.info, "settings/about"),
            SettingItemResolved(context.getString(R.string.contributors), R.drawable.group, "settings/about"),
            SettingItemResolved(context.getString(R.string.Changelog), R.drawable.schedule, "changelog")
        )
    }

    val searchResults = remember(searchQuery.text) {
        if (searchQuery.text.isBlank()) emptyList()
        else {
            val query = searchQuery.text.trim().lowercase()
            allSettings.filter { category ->
                val title = category.title.lowercase()
                val keywordMatch = category.keywords.any { it.lowercase().contains(query) }
                title.contains(query) || keywordMatch
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.settings)) },
                    modifier = if (!isSearching && !active) Modifier.clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)) else Modifier,
                    navigationIcon = {
                        IconButton(
                            onClick = navController::navigateUp,
                            onLongClick = navController::backToMain
                        ) {
                            Icon(painterResource(R.drawable.arrow_back), null)
                        }
                    },
                    actions = {
                        IconButton(onClick = { isSearching = true }, onLongClick = {}) {
                            Icon(painterResource(R.drawable.search), null)
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                    )
                )
            }
        ) { paddingValues ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                    .verticalScroll(rememberScrollState())
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    shape = RoundedCornerShape(32.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                    // Only clickable if logged in
                    onClick = { if (isLoggedIn) navController.navigate("settings/account") }
                ) {
                    Row(modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary.copy(
                                            alpha = 0.1f
                                        ), MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                    )
                                )
                            )
                            .border(
                                width = 2.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                shape = CircleShape
                            ), contentAlignment = Alignment.Center) {
                            if (isLoggedIn) {
                                val avatarModel = when(currentSelection) {
                                    is AvatarSelection.Custom -> (currentSelection as AvatarSelection.Custom).uri.toUri()
                                    is AvatarSelection.DiceBear -> (currentSelection as AvatarSelection.DiceBear).url
                                    else -> null
                                }
                                if (avatarModel != null) {
                                    AsyncImage(model = ImageRequest.Builder(context).data(avatarModel).crossfade(true).build(), contentDescription = null, modifier = Modifier
                                        .size(68.dp)
                                        .clip(
                                            CircleShape
                                        ), contentScale = ContentScale.Crop)
                                } else {
                                    Text(text = accountName.replace("@", "").trim().take(2).uppercase(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Icon(painter = painterResource(R.drawable.ganvo_monochrome), contentDescription = null, modifier = Modifier
                                    .size(40.dp)
                                    .padding(4.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(modifier = Modifier.width(20.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = if (isLoggedIn) accountName.replace("@", "").takeIf { it.isNotBlank() } ?: "User" else "Ganvo", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.height(4.dp))
                            // Motto for guest users, click does nothing
                            Text(text = if (isLoggedIn) stringResource(R.string.account) else "Your music, your way.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                        }
                        // Only show navigation arrow if clickable (logged in)
                        if (isLoggedIn) {
                            Icon(painter = painterResource(R.drawable.arrow_forward), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Text(text = stringResource(R.string.general_settings), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 24.dp, bottom = 12.dp, top = 8.dp))

                Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(modifier = Modifier.weight(1f)) { SettingsGridCard(icon = R.drawable.palette, title = stringResource(R.string.appearance), onClick = { navController.navigate("settings/appearance") }) }
                        Box(modifier = Modifier.weight(1f)) { SettingsGridCard(icon = R.drawable.person, title = stringResource(R.string.account), onClick = { navController.navigate("settings/account") }) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(modifier = Modifier.weight(1f)) { SettingsGridCard(icon = R.drawable.language, title = stringResource(R.string.content), onClick = { navController.navigate("settings/content") }) }
                        Box(modifier = Modifier.weight(1f)) { SettingsGridCard(icon = R.drawable.play, title = stringResource(R.string.player_and_audio), onClick = { navController.navigate("settings/player") }) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(modifier = Modifier.weight(1f)) { SettingsGridCard(icon = R.drawable.storage, title = stringResource(R.string.storage), onClick = { navController.navigate("settings/storage") }) }
                        Box(modifier = Modifier.weight(1f)) { SettingsGridCard(icon = R.drawable.security, title = stringResource(R.string.privacy), onClick = { navController.navigate("settings/privacy") }) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(modifier = Modifier.weight(1f)) { SettingsGridCard(icon = R.drawable.restore, title = stringResource(R.string.backup_restore), onClick = { navController.navigate("settings/backup_restore") }) }
                        Box(modifier = Modifier.weight(1f)) { SettingsGridCard(icon = R.drawable.schedule, title = stringResource(R.string.Changelog), onClick = { showChangelogSheet = true }) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(modifier = Modifier.weight(1f)) { SettingsGridCard(icon = R.drawable.info, title = stringResource(R.string.about), onClick = { navController.navigate("settings/about") }) }
                        Spacer(Modifier.weight(1f))
                    }
                }

                UpdateCard()
                VersionCard(uriHandler)
                Spacer(Modifier.height(24.dp))
            }
        }

        AnimatedVisibility(
            visible = isSearching || active,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(300))
        ) {
            TopSearch(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onSearch = { q ->
                    addSearchHistory(q)
                    keyboardController?.hide()
                },
                active = active,
                onActiveChange = { isActive ->
                    active = isActive
                    if (!isActive) isSearching = false
                },
                placeholder = { Text(stringResource(R.string.search)) },
                leadingIcon = {
                    IconButton(
                        onClick = {
                            if (active) {
                                active = false
                                isSearching = false
                            } else {
                                navController.navigateUp()
                            }
                        },
                        onLongClick = {}
                    ) {
                        Icon(painterResource(if (active) R.drawable.arrow_back else R.drawable.arrow_back), null)
                    }
                },
                trailingIcon = {
                    if (searchQuery.text.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = TextFieldValue("") },
                            onLongClick = {}
                        ) {
                            Icon(painterResource(R.drawable.close), null)
                        }
                    }
                }
            ) {
                val queryStr = searchQuery.text.trim()
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (queryStr.isEmpty()) {
                        if (searchHistory.isNotEmpty()) {
                            item {
                                Text(
                                    text = stringResource(R.string.SearchHistory),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                            items(searchHistory) { historyItem ->
                                SuggestionItem(
                                    query = historyItem,
                                    online = false,
                                    onClick = {
                                        searchQuery = TextFieldValue(historyItem, TextRange(historyItem.length))
                                        addSearchHistory(historyItem)
                                    },
                                    onDelete = { removeSearchHistory(historyItem) },
                                    onFillTextField = {
                                        searchQuery = TextFieldValue(historyItem, TextRange(historyItem.length))
                                    }
                                )
                            }
                        }
                    } else {
                        if (searchResults.isNotEmpty()) {
                            item {
                                Text(
                                    text = stringResource(R.string.search_results),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                            items(searchResults) { item ->
                                PreferenceEntry(
                                    title = { Text(item.title) },
                                    icon = { Icon(painterResource(item.iconRes), null) },
                                    onClick = {
                                        addSearchHistory(queryStr)
                                        active = false
                                        isSearching = false
                                        searchQuery = TextFieldValue("")
                                        if (item.route == "changelog") {
                                            showChangelogSheet = true
                                        } else {
                                            navController.navigate(item.route)
                                        }
                                    }
                                )
                            }
                        } else {
                            item {
                                EmptyPlaceholder(
                                    icon = R.drawable.search,
                                    text = stringResource(R.string.no_results_found)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showChangelogSheet) {
        ModalBottomSheet(onDismissRequest = { showChangelogSheet = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())) {
                ChangelogScreen()
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}