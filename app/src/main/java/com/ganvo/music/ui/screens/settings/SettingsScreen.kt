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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.core.net.toUri
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
import com.ganvo.music.ui.component.IconButton
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(72.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        onClick = { uriHandler.openUri("https://github.com/Ganvo/Ganvo/releases/latest") }
    ) {
        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(44.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(170.dp),
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
        Card(modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(28.dp)) {
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
        modifier = Modifier.fillMaxWidth().aspectRatio(1.15f),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        onClick = onClick
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(56.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(painter = painterResource(id = icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    latestVersion: Long,
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val uriHandler = LocalUriHandler.current
    var showChangelogSheet by remember { mutableStateOf(false) }

    Column(Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)).verticalScroll(rememberScrollState())) {
        Spacer(Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)))
        val context = LocalContext.current
        val avatarManager = remember { AvatarPreferenceManager(context) }
        val currentSelection by avatarManager.getAvatarSelection.collectAsState(initial = AvatarSelection.Default)
        val accountName by rememberPreference(AccountNameKey, "")
        val innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
        val isLoggedIn = remember(innerTubeCookie) { "SAPISID" in parseCookieString(innerTubeCookie) }

        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
            // Only clickable if logged in
            onClick = { if (isLoggedIn) navController.navigate("settings/account") }
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(72.dp).clip(CircleShape).background(brush = Brush.radialGradient(colors = listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)))).border(width = 2.dp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), shape = CircleShape), contentAlignment = Alignment.Center) {
                    if (isLoggedIn) {
                        val avatarModel = when(currentSelection) {
                            is AvatarSelection.Custom -> (currentSelection as AvatarSelection.Custom).uri.toUri()
                            is AvatarSelection.DiceBear -> (currentSelection as AvatarSelection.DiceBear).url
                            else -> null
                        }
                        if (avatarModel != null) {
                            AsyncImage(model = ImageRequest.Builder(context).data(avatarModel).crossfade(true).build(), contentDescription = null, modifier = Modifier.size(68.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                        } else {
                            Text(text = accountName.replace("@", "").trim().take(2).uppercase(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Icon(painter = painterResource(R.drawable.ganvo_monochrome), contentDescription = null, modifier = Modifier.size(40.dp).padding(4.dp), tint = MaterialTheme.colorScheme.primary)
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

        UpdateCard(); VersionCard(uriHandler); Spacer(Modifier.height(24.dp))
    }

    if (showChangelogSheet) {
        ModalBottomSheet(onDismissRequest = { showChangelogSheet = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).verticalScroll(rememberScrollState())) {
                ChangelogScreen(); Spacer(Modifier.height(32.dp))
            }
        }
    }

    TopAppBar(
        title = { Text(stringResource(R.string.settings)) },
        modifier = Modifier.clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)),
        navigationIcon = { IconButton(onClick = navController::navigateUp, onLongClick = navController::backToMain) { Icon(painterResource(R.drawable.arrow_back), null) } },
        scrollBehavior = scrollBehavior
    )
}