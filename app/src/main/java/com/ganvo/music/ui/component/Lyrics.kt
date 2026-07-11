package com.ganvo.music.ui.component

import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.ganvo.music.LocalPlayerConnection
import com.ganvo.music.R
import com.ganvo.music.constants.*
import com.ganvo.music.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.ganvo.music.extensions.togglePlayPause
import com.ganvo.music.extensions.toggleRepeatMode
import com.ganvo.music.lyrics.LyricsEntry
import com.ganvo.music.lyrics.LyricsEntry.Companion.HEAD_LYRICS_ENTRY
import com.ganvo.music.lyrics.LyricsUtils.findCurrentLineIndex
import com.ganvo.music.lyrics.LyricsUtils.parseLyrics
import com.ganvo.music.ui.component.shimmer.ShimmerHost
import com.ganvo.music.ui.component.shimmer.TextPlaceholder
import com.ganvo.music.ui.menu.SongMenu
import com.ganvo.music.ui.screens.settings.LyricsPosition
import com.ganvo.music.ui.theme.SfProDisplayFontFamily
import com.ganvo.music.utils.TransliterationUtils
import com.ganvo.music.utils.makeTimeString
import com.ganvo.music.utils.rememberEnumPreference
import com.ganvo.music.utils.rememberPreference
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@RequiresApi(Build.VERSION_CODES.S)
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun Lyrics(
    sliderPositionProvider: () -> Long?,
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val currentSongId = mediaMetadata?.id
    val currentSong by playerConnection.currentSong.collectAsState(initial = null)

    var isLoadingLyrics by remember(currentSongId) { mutableStateOf(false) }
    var lines by remember { mutableStateOf<List<LyricsEntry>>(emptyList()) }
    var providerSource by remember { mutableStateOf("") }
    var isAutoScrollEnabled by remember { mutableStateOf(true) }

    val playbackState by playerConnection.playbackState.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()

    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    val currentVolumeLevel by playerConnection.service.playerVolume.collectAsState()

    val lyricsPosition by rememberEnumPreference(LyricsTextPositionKey, LyricsPosition.CENTER)
    val lyricsClick by rememberPreference(LyricsClickKey, true)
    val experimentalLyrics by rememberPreference(ExperimentalLyricsKey, false)
    val glowingLyrics by rememberPreference(GlowingLyricsKey, false)
    val wordByWord by rememberEnumPreference(WordByWordStyleKey, WordByWordStyle.FADE)
    val lyricsTextSize by rememberPreference(LyricsTextSizeKey, 24f)
    val lyricsLineSpacing by rememberPreference(LyricsLineSpacingKey, 1.2f)
    val respectAgent by rememberPreference(RespectAgentPositioningKey, true)

    // Selection & Sharing states
    val selectedLines = remember { mutableStateListOf<LyricsEntry>() }
    var isSelectionModeActive by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    var shareText by remember { mutableStateOf("") }

    val baseAlignment = when (lyricsPosition) {
        LyricsPosition.LEFT -> Alignment.Start
        LyricsPosition.CENTER -> Alignment.CenterHorizontally
        LyricsPosition.RIGHT -> Alignment.End
    }

    // GESTIÓN DE RETROCESO DE ALTA PRIORIDAD
    // Mediante la directiva `key` forzamos el registro del BackHandler en el dispatcher nativo
    // cada vez que cambia el estado, tomando prioridad absoluta sobre la hoja inferior del reproductor.
    key(isSelectionModeActive, onNavigateBack) {
        androidx.activity.compose.BackHandler(enabled = isSelectionModeActive || onNavigateBack != null) {
            if (isSelectionModeActive) {
                isSelectionModeActive = false
                selectedLines.clear()
            } else {
                onNavigateBack?.invoke()
            }
        }
    }

    fun fetchLyrics() {
        currentSongId?.let {
            isLoadingLyrics = true
            isAutoScrollEnabled = true
            scope.launch(Dispatchers.IO) {
                try {
                    val entryPoint = EntryPointAccessors.fromApplication(
                        context.applicationContext,
                        com.ganvo.music.di.LyricsHelperEntryPoint::class.java
                    )
                    val fetched = entryPoint.lyricsHelper().getLyrics(mediaMetadata!!)

                    val rawLyrics = if (fetched.startsWith("PROVIDER:")) {
                        val fetchedLines = fetched.lines()
                        providerSource = fetchedLines[0].substringAfter("PROVIDER:")
                        fetchedLines.drop(1).joinToString("\n")
                    } else {
                        providerSource = "Local Cache"
                        fetched
                    }

                    val parsed = if (rawLyrics == LYRICS_NOT_FOUND || rawLyrics.trim().isEmpty() || rawLyrics == "null") {
                        listOf(LyricsEntry(0L, LYRICS_NOT_FOUND))
                    } else if (rawLyrics.startsWith("[")) {
                        listOf(HEAD_LYRICS_ENTRY) + parseLyrics(rawLyrics)
                    } else {
                        rawLyrics.lines().mapIndexed { i, l -> LyricsEntry(i * 1000L, l) }
                    }

                    val romanizedLines = parsed.map { entry ->
                        if (entry.text.isBlank() || entry.text == LYRICS_NOT_FOUND) entry
                        else entry.copy(romanizedText = TransliterationUtils.transliterate(entry.text))
                    }

                    withContext(Dispatchers.Main) {
                        lines = romanizedLines
                        isLoadingLyrics = false
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        lines = listOf(LyricsEntry(0L, LYRICS_NOT_FOUND))
                        isLoadingLyrics = false
                    }
                }
            }
        }
    }

    LaunchedEffect(currentSongId) {
        fetchLyrics()
        isSelectionModeActive = false
        selectedLines.clear()
    }

    LaunchedEffect(isPlaying, playbackState) {
        while (isActive) {
            position = playerConnection.player.currentPosition
            duration = playerConnection.player.duration
            delay(32)
        }
    }

    val currentLineIndex = remember(lines, position) { findCurrentLineIndex(lines, position) }
    val lazyListState = rememberLazyListState()
    val isDragged by lazyListState.interactionSource.collectIsDraggedAsState()

    LaunchedEffect(isDragged) { if (isDragged) isAutoScrollEnabled = false }

    LaunchedEffect(currentLineIndex, isAutoScrollEnabled) {
        if (isAutoScrollEnabled && currentLineIndex != -1 && lines.isNotEmpty() && lines[0].text != LYRICS_NOT_FOUND) {
            lazyListState.animateScrollToItem(currentLineIndex, -250)
        }
    }

    val listItems = remember(lines) {
        val items = mutableListOf<LyricsListItem>()
        lines.forEachIndexed { index, entry ->
            items.add(LyricsListItem.Line(index, entry))
            val nextEntry = lines.getOrNull(index + 1)
            if (nextEntry != null) {
                val gap = nextEntry.time - entry.time
                if (gap > 6000L) {
                    items.add(
                        LyricsListItem.Indicator(
                            afterLineIndex = index,
                            gapMs = gap,
                            gapStartMs = entry.time + 1000L,
                            gapEndMs = nextEntry.time - 1000L,
                            nextAgent = nextEntry.agent
                        )
                    )
                }
            }
        }
        items
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        AsyncImage(
            model = mediaMetadata?.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(60.dp).alpha(0.45f)
        )

        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { onNavigateBack?.invoke() }) {
                    Icon(painterResource(R.drawable.expand_more), null, tint = Color.White)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Now Playing", color = Color.White.copy(0.7f), fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = SfProDisplayFontFamily)
                    Text(mediaMetadata?.title ?: "Unknown", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, fontFamily = SfProDisplayFontFamily)
                }
                IconButton(onClick = {
                    currentSong?.let { song ->
                        menuState.show { SongMenu(originalSong = song, navController = androidx.navigation.compose.rememberNavController(), onDismiss = menuState::dismiss) }
                    }
                }) {
                    Icon(painterResource(R.drawable.more_horiz), null, tint = Color.White)
                }
            }

            // Lyrics Content
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (isLoadingLyrics) {
                    ShimmerHost(modifier = Modifier.align(Alignment.Center)) {
                        repeat(5) { TextPlaceholder(modifier = Modifier.padding(24.dp)) }
                    }
                } else if (lines.isEmpty() || (lines.size == 1 && lines[0].text == LYRICS_NOT_FOUND)) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Oh sorry, didn't find lyrics \uD83D\uDE14",
                            color = Color.White.copy(0.7f),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = SfProDisplayFontFamily
                        )
                        Spacer(Modifier.height(24.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Button(
                                onClick = { fetchLyrics() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(0.2f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(painterResource(R.drawable.sync), null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Refetch", color = Color.White, fontWeight = FontWeight.Bold, fontFamily = SfProDisplayFontFamily)
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 120.dp, bottom = 120.dp),
                        horizontalAlignment = baseAlignment
                    ) {
                        itemsIndexed(listItems) { _, listItem ->
                            when (listItem) {
                                is LyricsListItem.Line -> {
                                    val index = listItem.index
                                    val item = listItem.entry
                                    val isActiveLine = index == currentLineIndex

                                    LyricsLine(
                                        index = index,
                                        item = item,
                                        isSynced = lines.getOrNull(1)?.time != 0L,
                                        isActiveLine = isActiveLine,
                                        bgVisible = true,
                                        isSelected = item in selectedLines,
                                        isSelectionModeActive = isSelectionModeActive,
                                        currentPositionState = position,
                                        lyricsOffset = 0L,
                                        playerConnection = playerConnection,
                                        lyricsTextSize = lyricsTextSize,
                                        lyricsLineSpacing = lyricsLineSpacing,
                                        expressiveAccent = Color.White,
                                        lyricsTextPosition = lyricsPosition,
                                        respectAgentPositioning = respectAgent,
                                        isAutoScrollEnabled = isAutoScrollEnabled,
                                        displayedCurrentLineIndex = currentLineIndex,
                                        romanizeAsMain = false,
                                        enabledLanguages = listOf("en"),
                                        romanizeLyrics = true,
                                        onSizeChanged = {},
                                        onClick = {
                                            if (isSelectionModeActive) {
                                                if (item in selectedLines) selectedLines.remove(item)
                                                else selectedLines.add(item)
                                            } else {
                                                playerConnection.player.seekTo(item.time)
                                                isAutoScrollEnabled = true
                                            }
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            if (!isSelectionModeActive) {
                                                isSelectionModeActive = true
                                                selectedLines.clear()
                                            }
                                            if (item in selectedLines) selectedLines.remove(item)
                                            else selectedLines.add(item)
                                        }
                                    )
                                }
                                is LyricsListItem.Indicator -> {
                                    val isGapActive = position in listItem.gapStartMs..listItem.gapEndMs
                                    IntervalIndicator(
                                        gapStartMs = listItem.gapStartMs,
                                        gapEndMs = listItem.gapEndMs,
                                        currentPositionMs = position,
                                        visible = isGapActive,
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        if (providerSource.isNotBlank() && lines.isNotEmpty() && lines[0].text != LYRICS_NOT_FOUND) {
                            item {
                                Text(
                                    text = "Lyrics provided by $providerSource",
                                    color = Color.White.copy(alpha = 0.4f),
                                    fontSize = 12.sp,
                                    fontFamily = SfProDisplayFontFamily,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(top = 40.dp, bottom = 24.dp)
                                )
                            }
                        }
                    }
                }

                // Selection & Autoscroll HUD Overlay
                LyricsActionOverlay(
                    isAutoScrollEnabled = isAutoScrollEnabled,
                    isSynced = lines.isNotEmpty() && lines[0].text != LYRICS_NOT_FOUND,
                    isSelectionModeActive = isSelectionModeActive,
                    anySelected = selectedLines.isNotEmpty(),
                    onSyncClick = { isAutoScrollEnabled = true },
                    onCancelSelection = {
                        isSelectionModeActive = false
                        selectedLines.clear()
                    },
                    onShareSelection = {
                        shareText = selectedLines.joinToString("\n") { it.cleanText }
                        showShareDialog = true
                    },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }

            // Controls
            if (!isSelectionModeActive) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 30.dp, vertical = 20.dp)) {
                    Slider(
                        value = position.toFloat(),
                        onValueChange = { playerConnection.player.seekTo(it.toLong()) },
                        valueRange = 0f..(duration.toFloat().coerceAtLeast(1f)),
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(0.2f))
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(makeTimeString(position), color = Color.White.copy(0.6f), fontSize = 12.sp, fontFamily = SfProDisplayFontFamily)
                        Text(makeTimeString(duration), color = Color.White.copy(0.6f), fontSize = 12.sp, fontFamily = SfProDisplayFontFamily)
                    }

                    Spacer(Modifier.height(20.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { playerConnection.player.toggleRepeatMode() }) {
                            Icon(painterResource(when (playerConnection.player.repeatMode) {
                                Player.REPEAT_MODE_ONE -> R.drawable.repeat_one
                                else -> R.drawable.repeat
                            }), null, tint = if (playerConnection.player.repeatMode == Player.REPEAT_MODE_OFF) Color.White.copy(0.5f) else Color.White)
                        }
                        IconButton(onClick = { playerConnection.player.seekToPrevious() }) {
                            Icon(painterResource(R.drawable.skip_previous), null, modifier = Modifier.size(40.dp), tint = Color.White)
                        }
                        Surface(shape = CircleShape, color = Color.White, modifier = Modifier.size(70.dp).clickable { playerConnection.player.togglePlayPause() }) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(painterResource(if (isPlaying) R.drawable.pause else R.drawable.play), null, tint = Color.Black, modifier = Modifier.size(36.dp))
                            }
                        }
                        IconButton(onClick = { playerConnection.player.seekToNext() }) {
                            Icon(painterResource(R.drawable.skip_next), null, modifier = Modifier.size(40.dp), tint = Color.White)
                        }
                        IconButton(onClick = { playerConnection.service.toggleLike() }) {
                            Icon(painterResource(if (currentSong?.song?.liked == true) R.drawable.favorite else R.drawable.favorite_border), null, tint = if (currentSong?.song?.liked == true) MaterialTheme.colorScheme.error else Color.White.copy(0.6f))
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.volume_off), null, tint = Color.White.copy(0.6f), modifier = Modifier.size(18.dp))
                        Slider(
                            value = currentVolumeLevel,
                            onValueChange = { playerConnection.service.playerVolume.value = it },
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                            colors = SliderDefaults.colors(thumbColor = Color.Transparent, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(0.2f))
                        )
                        Icon(painterResource(R.drawable.volume_up), null, tint = Color.White.copy(0.6f), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }

    // Share Dialog
    if (showShareDialog) {
        ShareLyricsDialog(
            lyricsText = shareText,
            songTitle = mediaMetadata?.title ?: "Unknown",
            artists = mediaMetadata?.artists?.joinToString { it.name } ?: "Unknown",
            mediaMetadata = mediaMetadata,
            onDismiss = { showShareDialog = false }
        )
    }
}
