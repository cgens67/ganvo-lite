package com.ganvo.music.ui.component

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Player.STATE_ENDED
import coil.compose.AsyncImage
import com.ganvo.music.LocalDatabase
import com.ganvo.music.LocalPlayerConnection
import com.ganvo.music.R
import com.ganvo.music.constants.LyricsClickKey
import com.ganvo.music.db.entities.LyricsEntity
import com.ganvo.music.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.ganvo.music.extensions.togglePlayPause
import com.ganvo.music.extensions.toggleRepeatMode
import com.ganvo.music.lyrics.LyricsEntry
import com.ganvo.music.lyrics.LyricsEntry.Companion.HEAD_LYRICS_ENTRY
import com.ganvo.music.lyrics.LyricsUtils.findCurrentLineIndex
import com.ganvo.music.lyrics.LyricsUtils.parseLyrics
import com.ganvo.music.ui.component.shimmer.ShimmerHost
import com.ganvo.music.ui.component.shimmer.TextPlaceholder
import com.ganvo.music.ui.menu.LyricsMenu
import com.ganvo.music.utils.TransliterationUtils
import com.ganvo.music.utils.makeTimeString
import com.ganvo.music.utils.rememberPreference
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds

@RequiresApi(Build.VERSION_CODES.M)
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@SuppressLint("UnusedBoxWithConstraintsScope", "StringFormatInvalid")
@Composable
fun Lyrics(
    sliderPositionProvider: () -> Long?,
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val density = LocalDensity.current
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()
    val database = LocalDatabase.current

    val isFullscreen = onNavigateBack != null
    val changeLyrics by rememberPreference(LyricsClickKey, true)
    val landscapeOffset = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val currentSongId = mediaMetadata?.id

    var lyricsCache by remember { mutableStateOf<Map<String, LyricsEntity>>(emptyMap()) }
    var currentLyricsEntity by remember(currentSongId) { mutableStateOf<LyricsEntity?>(lyricsCache[currentSongId]) }
    var isLoadingLyrics by remember(currentSongId) { mutableStateOf(false) }

    val rawLyrics = remember(currentLyricsEntity) { currentLyricsEntity?.lyrics?.trim() }

    val playbackState by playerConnection.playbackState.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val repeatMode by playerConnection.repeatMode.collectAsState()
    val shuffleMode by playerConnection.shuffleModeEnabled.collectAsState()
    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsState()
    val canSkipNext by playerConnection.canSkipNext.collectAsState()
    val playerVolume = playerConnection.service.playerVolume.collectAsState()

    var position by rememberSaveable(playbackState) { mutableLongStateOf(playerConnection.player.currentPosition) }
    var duration by rememberSaveable(playbackState) { mutableLongStateOf(playerConnection.player.duration) }
    var sliderPosition by remember { mutableStateOf<Long?>(null) }
    var isMuted by remember { mutableStateOf(playerVolume.value == 0f) }
    var previousVolume by remember { mutableFloatStateOf(if (playerVolume.value > 0f) playerVolume.value else 0.5f) }

    var lines by remember { mutableStateOf<List<LyricsEntry>>(emptyList()) }

    // Fetch Lyrics
    LaunchedEffect(currentSongId) {
        currentSongId?.let { songId ->
            if (lyricsCache.containsKey(songId)) {
                currentLyricsEntity = lyricsCache[songId]
                return@LaunchedEffect
            }
            isLoadingLyrics = true
            withContext(Dispatchers.IO) {
                try {
                    val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, com.ganvo.music.di.LyricsHelperEntryPoint::class.java)
                    val fetchedLyrics = mediaMetadata?.let { entryPoint.lyricsHelper().getLyrics(it) }
                    val entity = LyricsEntity(songId, fetchedLyrics ?: LYRICS_NOT_FOUND)
                    database.query { upsert(entity) }
                    lyricsCache = lyricsCache.toMutableMap().apply { put(songId, entity) }
                    currentLyricsEntity = entity
                } catch (e: Exception) {
                    val entity = LyricsEntity(songId, LYRICS_NOT_FOUND)
                    lyricsCache = lyricsCache.toMutableMap().apply { put(songId, entity) }
                    currentLyricsEntity = entity
                } finally {
                    isLoadingLyrics = false
                }
            }
        }
    }

    // Process Lyrics and Romanization
    LaunchedEffect(rawLyrics, currentSongId) {
        if (rawLyrics.isNullOrEmpty() || rawLyrics == LYRICS_NOT_FOUND) {
            lines = emptyList()
            return@LaunchedEffect
        }
        val parsed = if (rawLyrics.startsWith("[")) listOf(HEAD_LYRICS_ENTRY) + parseLyrics(rawLyrics)
        else rawLyrics.lines().mapIndexed { index, line -> LyricsEntry(index * 100L, line) }
        lines = parsed

        // Background Romanization
        withContext(Dispatchers.IO) {
            val plainText = parsed.joinToString("\n") { it.text }
            val romanizedFull = TransliterationUtils.transliterate(plainText)
            if (romanizedFull != null) {
                val romLines = romanizedFull.lines()
                if (romLines.size == parsed.size || romLines.size > parsed.size - 5) {
                    lines = parsed.mapIndexed { i, entry -> entry.copy(romanizedText = romLines.getOrNull(i)) }
                }
            }
        }
    }

    val isSynced = remember(rawLyrics) { !rawLyrics.isNullOrEmpty() && rawLyrics.startsWith("[") }
    var currentLineIndex by remember { mutableIntStateOf(-1) }
    var deferredCurrentLineIndex by remember(currentSongId) { mutableIntStateOf(0) }
    var previousLineIndex by remember(currentSongId) { mutableIntStateOf(0) }
    var lastPreviewTime by remember(currentSongId) { mutableLongStateOf(0L) }
    var isSeeking by remember { mutableStateOf(false) }
    var initialScrollDone by remember(currentSongId) { mutableStateOf(false) }
    var shouldScrollToFirstLine by remember(currentSongId) { mutableStateOf(true) }
    var isAppMinimized by rememberSaveable { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }

    var isSelectionModeActive by remember(currentSongId) { mutableStateOf(false) }
    val selectedIndices = remember(currentSongId) { mutableStateListOf<Int>() }
    var showMaxSelectionToast by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    var shareDialogData by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    var showColorPickerDialog by remember { mutableStateOf(false) }
    var showProgressDialog by remember { mutableStateOf(false) }

    val lazyListState = rememberLazyListState()
    val maxSelectionLimit = 5

    LaunchedEffect(playbackState) {
        if (isFullscreen && playbackState == Player.STATE_READY) {
            while (isActive) {
                delay(100)
                position = playerConnection.player.currentPosition
                duration = playerConnection.player.duration
            }
        }
    }

    LaunchedEffect(showMaxSelectionToast) {
        if (showMaxSelectionToast) {
            Toast.makeText(context, context.getString(R.string.max_selection_limit, maxSelectionLimit), Toast.LENGTH_SHORT).show()
            showMaxSelectionToast = false
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                val visibleItemsInfo = lazyListState.layoutInfo.visibleItemsInfo
                val isCurrentLineVisible = visibleItemsInfo.any { it.index == currentLineIndex }
                if (isCurrentLineVisible) initialScrollDone = false
                isAppMinimized = true
            } else if (event == Lifecycle.Event.ON_START) {
                isAppMinimized = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(rawLyrics) {
        if (rawLyrics.isNullOrEmpty() || !rawLyrics.startsWith("[")) {
            currentLineIndex = -1
            return@LaunchedEffect
        }
        while (isActive) {
            delay(50)
            val sliderPos = sliderPositionProvider()
            isSeeking = sliderPos != null
            val actualPos = sliderPos ?: playerConnection.player.currentPosition
            currentLineIndex = findCurrentLineIndex(lines, actualPos)
        }
    }

    LaunchedEffect(isSeeking, lastPreviewTime) {
        if (isSeeking) lastPreviewTime = 0L
        else if (lastPreviewTime != 0L) {
            delay(if (isFullscreen) 2.seconds else 2.seconds)
            lastPreviewTime = 0L
        }
    }

    LaunchedEffect(currentLineIndex, lastPreviewTime, initialScrollDone) {
        fun countNewLine(str: String) = str.count { it == '\n' }
        fun calculateOffset() = with(density) {
            if (landscapeOffset) 16.dp.toPx().toInt() * countNewLine(lines[currentLineIndex].text)
            else 20.dp.toPx().toInt() * countNewLine(lines[currentLineIndex].text)
        }

        if (!isSynced) return@LaunchedEffect

        if ((currentLineIndex == 0 && shouldScrollToFirstLine) || !initialScrollDone) {
            shouldScrollToFirstLine = false
            lazyListState.scrollToItem(currentLineIndex, with(density) { (if (isFullscreen) 100.dp else 36.dp).toPx().toInt() } + calculateOffset())
            if (!isAppMinimized) initialScrollDone = true
        } else if (currentLineIndex != -1) {
            deferredCurrentLineIndex = currentLineIndex
            if (isSeeking) {
                lazyListState.scrollToItem(currentLineIndex, with(density) { (if (isFullscreen) 100.dp else 36.dp).toPx().toInt() } + calculateOffset())
            } else if (lastPreviewTime == 0L || currentLineIndex != previousLineIndex) {
                val visibleItemsInfo = lazyListState.layoutInfo.visibleItemsInfo
                val isCurrentLineVisible = visibleItemsInfo.any { it.index == currentLineIndex }

                if (isCurrentLineVisible) {
                    lazyListState.animateScrollToItem(currentLineIndex, with(density) { (if (isFullscreen) 100.dp else 36.dp).toPx().toInt() } + calculateOffset())
                }
            }
        }

        if (currentLineIndex > 0) shouldScrollToFirstLine = true
        previousLineIndex = currentLineIndex
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .then(
                if (isFullscreen) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures(onTap = { if (!isSelectionModeActive) showControls = !showControls })
                    }
                } else Modifier
            )
    ) {
        if (isFullscreen) {
            BackHandler(enabled = true) { onNavigateBack?.invoke() }

            // Blurred Background
            mediaMetadata?.let { metadata ->
                AsyncImage(
                    model = metadata.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(80.dp)
                        .alpha(0.6f)
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Black.copy(alpha = 0.8f))))
                )
            }

            // Top Bar
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { -it },
                exit = fadeOut(tween(300)) + slideOutVertically(tween(300)) { -it },
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .zIndex(2f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onNavigateBack?.invoke() }) {
                        Icon(
                            painter = painterResource(R.drawable.expand_more),
                            contentDescription = stringResource(R.string.back),
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    mediaMetadata?.let { metadata ->
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Now Playing",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.7f),
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = metadata.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            mediaMetadata?.let { metadata ->
                                menuState.show {
                                    LyricsMenu(
                                        lyricsProvider = { currentLyricsEntity },
                                        mediaMetadataProvider = { metadata },
                                        onDismiss = menuState::dismiss
                                    )
                                }
                            }
                        }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.more_horiz),
                            contentDescription = stringResource(R.string.more_options),
                            tint = Color.White
                        )
                    }
                }
            }

            // Bottom Controls
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it },
                exit = fadeOut(tween(300)) + slideOutVertically(tween(300)) { it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .zIndex(2f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Progress Slider
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = makeTimeString(sliderPosition ?: position),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Slider(
                            value = (sliderPosition ?: position).toFloat(),
                            valueRange = 0f..(if (duration == C.TIME_UNSET) 0f else duration.toFloat()),
                            onValueChange = { sliderPosition = it.toLong() },
                            onValueChangeFinished = {
                                sliderPosition?.let {
                                    playerConnection.player.seekTo(it)
                                    position = it
                                }
                                sliderPosition = null
                            },
                            colors = SliderDefaults.colors(
                                activeTrackColor = Color.White,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                                thumbColor = Color.White
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        )
                        Text(
                            text = if (duration != C.TIME_UNSET) makeTimeString(duration) else "",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    // Playback Controls
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(
                            onClick = { playerConnection.player.toggleRepeatMode() },
                            modifier = Modifier.size(48.dp).alpha(if (repeatMode == Player.REPEAT_MODE_OFF) 0.5f else 1f)
                        ) {
                            Icon(
                                painter = painterResource(when (repeatMode) {
                                    Player.REPEAT_MODE_OFF, Player.REPEAT_MODE_ALL -> R.drawable.repeat
                                    Player.REPEAT_MODE_ONE -> R.drawable.repeat_one
                                    else -> R.drawable.repeat
                                }),
                                contentDescription = "Repeat",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        IconButton(
                            onClick = { playerConnection.seekToPrevious() },
                            enabled = canSkipPrevious,
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.skip_previous),
                                contentDescription = "Previous",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .clickable {
                                    if (playbackState == STATE_ENDED) {
                                        playerConnection.player.seekTo(0, 0)
                                        playerConnection.player.playWhenReady = true
                                    } else playerConnection.player.togglePlayPause()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(if (playbackState == STATE_ENDED) R.drawable.replay else if (isPlaying) R.drawable.pause else R.drawable.play),
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.Black,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        IconButton(
                            onClick = { playerConnection.seekToNext() },
                            enabled = canSkipNext,
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.skip_next),
                                contentDescription = "Next",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        IconButton(
                            onClick = { playerConnection.player.shuffleModeEnabled = !shuffleMode },
                            modifier = Modifier.size(48.dp).alpha(if (shuffleMode) 1f else 0.5f)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.shuffle),
                                contentDescription = "Shuffle",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    // Volume Controls
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    ) {
                        IconButton(
                            onClick = {
                                isMuted = !isMuted
                                if (isMuted) {
                                    previousVolume = playerVolume.value
                                    playerConnection.service.playerVolume.value = 0f
                                } else {
                                    playerConnection.service.playerVolume.value = previousVolume
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                painter = painterResource(if (isMuted || playerVolume.value == 0f) R.drawable.volume_off else R.drawable.volume_up), // Using volume_up as placeholder for low volume if needed
                                contentDescription = "Mute",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Slider(
                            value = if (isMuted) 0f else playerVolume.value,
                            onValueChange = {
                                isMuted = false
                                playerConnection.service.playerVolume.value = it
                                previousVolume = it
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                            colors = SliderDefaults.colors(
                                activeTrackColor = Color.White,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                                thumbColor = Color.White
                            )
                        )

                        Icon(
                            painter = painterResource(R.drawable.volume_up),
                            contentDescription = "Volume",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp).padding(start = 4.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isSelectionModeActive,
                enter = slideInVertically(spring()) { it } + fadeIn(),
                exit = slideOutVertically(spring()) { it } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).zIndex(3f)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.inverseSurface,
                    modifier = Modifier.padding(bottom = if (showControls) 260.dp else 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(painterResource(R.drawable.check_circle), null, tint = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.selection_mode_active, selectedIndices.size), color = MaterialTheme.colorScheme.inverseOnSurface, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        BoxWithConstraints(
            contentAlignment = if (isFullscreen) Alignment.TopStart else Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isFullscreen) {
                        Modifier.padding(
                            top = 100.dp,
                            bottom = if (showControls) 280.dp else 0.dp
                        )
                    } else {
                        Modifier.padding(bottom = 12.dp)
                    }
                )
        ) {
            val topPadding = if (isFullscreen) {
                with(LocalDensity.current) { 100.dp + WindowInsets.statusBars.asPaddingValues().calculateTopPadding() }
            } else 0.dp

            LazyColumn(
                state = lazyListState,
                contentPadding = if (isFullscreen) {
                    PaddingValues(top = topPadding, bottom = if (showControls) 280.dp else 0.dp, start = 16.dp, end = 16.dp)
                } else {
                    WindowInsets.systemBars.only(WindowInsetsSides.Top).add(WindowInsets(top = maxHeight / 2, bottom = maxHeight / 2)).asPaddingValues()
                },
                modifier = Modifier
                    .fadingEdge(vertical = if (isFullscreen) 32.dp else 64.dp)
                    .nestedScroll(remember {
                        object : NestedScrollConnection {
                            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                                if (!isSelectionModeActive) lastPreviewTime = System.currentTimeMillis()
                                return super.onPostScroll(consumed, available, source)
                            }
                            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                                if (!isSelectionModeActive) lastPreviewTime = System.currentTimeMillis()
                                return super.onPostFling(consumed, available)
                            }
                        }
                    })
            ) {
                val displayedCurrentLineIndex = if (isSeeking || isSelectionModeActive) deferredCurrentLineIndex else currentLineIndex

                if (isLoadingLyrics) {
                    item {
                        ShimmerHost {
                            repeat(if (isFullscreen) 6 else 10) {
                                Box(
                                    contentAlignment = Alignment.CenterStart,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    TextPlaceholder()
                                }
                            }
                        }
                    }
                } else {
                    itemsIndexed(items = lines, key = { index, item -> "${currentSongId}-$index-${item.time}" }) { index, item ->
                        val isSelected = selectedIndices.contains(index)
                        val isCurrentLine = index == displayedCurrentLineIndex && isSynced

                        val transition = updateTransition(isCurrentLine, label = "lyricLineTransition")
                        val scale by transition.animateFloat(
                            transitionSpec = { tween(300, easing = FastOutSlowInEasing) }, label = "scale"
                        ) { current -> if (current) 1.02f else 1f }

                        val textColorAnim by transition.animateColor(
                            transitionSpec = { tween(250) }, label = "textColor"
                        ) { current ->
                            when {
                                current && isFullscreen -> Color.White
                                current -> MaterialTheme.colorScheme.primary
                                isSelected && isFullscreen -> Color.White.copy(0.8f)
                                isFullscreen -> Color.White.copy(alpha = 0.5f)
                                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            }
                        }

                        val itemModifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .combinedClickable(
                                enabled = true,
                                onClick = {
                                    if (isSelectionModeActive) {
                                        if (isSelected) {
                                            selectedIndices.remove(index)
                                            if (selectedIndices.isEmpty()) isSelectionModeActive = false
                                        } else {
                                            if (selectedIndices.size < maxSelectionLimit) selectedIndices.add(index)
                                            else showMaxSelectionToast = true
                                        }
                                    } else if (isSynced && changeLyrics) {
                                        playerConnection.player.seekTo(item.time)
                                        scope.launch {
                                            lazyListState.animateScrollToItem(
                                                index,
                                                with(density) { (if (isFullscreen) 100.dp else 36.dp).toPx().toInt() }
                                            )
                                        }
                                        lastPreviewTime = 0L
                                    }
                                },
                                onLongClick = {
                                    if (!isSelectionModeActive) {
                                        isSelectionModeActive = true
                                        selectedIndices.add(index)
                                    } else if (!isSelected && selectedIndices.size < maxSelectionLimit) {
                                        selectedIndices.add(index)
                                    } else if (!isSelected) {
                                        showMaxSelectionToast = true
                                    }
                                }
                            )
                            .background(
                                when {
                                    isSelected && isSelectionModeActive -> Color.White.copy(alpha = 0.2f)
                                    else -> Color.Transparent
                                }
                            )
                            .padding(vertical = 12.dp, horizontal = 8.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }

                        Column(modifier = itemModifier) {
                            Text(
                                text = item.text,
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 24.sp,
                                    fontFamily = FontFamily.SansSerif
                                ),
                                color = textColorAnim,
                                textAlign = TextAlign.Left,
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (!item.romanizedText.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = item.romanizedText,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 16.sp,
                                        fontFamily = FontFamily.SansSerif
                                    ),
                                    color = if (isCurrentLine) Color.White.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.3f),
                                    textAlign = TextAlign.Left,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                if (lyrics == LYRICS_NOT_FOUND) {
                    item {
                        Box(modifier = Modifier.fillMaxSize().padding(top = 100.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(R.string.lyrics_not_found),
                                fontSize = 20.sp,
                                color = Color.White.copy(alpha = 0.5f),
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}