package com.ganvo.music.ui.component

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.ganvo.music.LocalPlayerConnection
import com.ganvo.music.R
import com.ganvo.music.constants.LyricsClickKey
import com.ganvo.music.constants.ShowLyricsKey
import com.ganvo.music.constants.ExperimentalLyricsKey
import com.ganvo.music.constants.GlowingLyricsKey
import com.ganvo.music.constants.WordByWordStyleKey
import com.ganvo.music.constants.LyricsTextSizeKey
import com.ganvo.music.constants.LyricsLineSpacingKey
import com.ganvo.music.constants.RespectAgentPositioningKey
import com.ganvo.music.constants.LyricsTextPositionKey
import com.ganvo.music.constants.WordByWordStyle
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
import com.ganvo.music.ui.menu.SongMenu
import com.ganvo.music.ui.menu.LyricsMenu
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
import kotlin.time.Duration.Companion.milliseconds

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

    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val currentSongId = mediaMetadata?.id
    val currentSong by playerConnection.currentSong.collectAsState(initial = null)

    var isLoadingLyrics by remember(currentSongId) { mutableStateOf(false) }
    var lines by remember { mutableStateOf<List<LyricsEntry>>(emptyList()) }
    var isAutoScrollEnabled by remember { mutableStateOf(true) }
    
    // Lyrics Menu state
    var showLyricsMenu by remember { mutableStateOf(false) }

    val playbackState by playerConnection.playbackState.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    val currentVolumeLevel by playerConnection.service.playerVolume.collectAsState()

    // Smooth position binding mapped directly to fast recomposition
    val animatedPos by animateFloatAsState(targetValue = position.toFloat(), animationSpec = tween(50, easing = LinearEasing), label = "animatedPos")

    // Preferences
    val lyricsPosition by rememberEnumPreference(LyricsTextPositionKey, LyricsPosition.CENTER)
    val lyricsClick by rememberPreference(LyricsClickKey, true)
    val experimentalLyrics by rememberPreference(ExperimentalLyricsKey, false)
    val glowingLyrics by rememberPreference(GlowingLyricsKey, false)
    val wordByWord by rememberEnumPreference(WordByWordStyleKey, WordByWordStyle.FADE)
    val lyricsTextSize by rememberPreference(LyricsTextSizeKey, 24f)
    val lyricsLineSpacing by rememberPreference(LyricsLineSpacingKey, 1.2f)
    val respectAgent by rememberPreference(RespectAgentPositioningKey, true)

    val baseAlignment = when (lyricsPosition) {
        LyricsPosition.LEFT -> Alignment.Start
        LyricsPosition.CENTER -> Alignment.CenterHorizontally
        LyricsPosition.RIGHT -> Alignment.End
    }
    val baseTextAlign = when (lyricsPosition) {
        LyricsPosition.LEFT -> TextAlign.Start
        LyricsPosition.CENTER -> TextAlign.Center
        LyricsPosition.RIGHT -> TextAlign.End
    }

    BackHandler(enabled = onNavigateBack != null) {
        onNavigateBack?.invoke()
    }

    fun fetchLyrics() {
        currentSongId?.let { songId ->
            isLoadingLyrics = true
            isAutoScrollEnabled = true
            scope.launch(Dispatchers.IO) {
                try {
                    val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, com.ganvo.music.di.LyricsHelperEntryPoint::class.java)
                    val fetched = entryPoint.lyricsHelper().getLyrics(mediaMetadata!!)
                    
                    val parsed = if (fetched == LYRICS_NOT_FOUND || fetched.trim().isEmpty() || fetched == "null") {
                        listOf(LyricsEntry(0L, LYRICS_NOT_FOUND))
                    } else if (fetched.startsWith("[")) {
                        listOf(HEAD_LYRICS_ENTRY) + parseLyrics(fetched)
                    } else {
                        fetched.lines().mapIndexed { i, l -> LyricsEntry(i * 1000L, l) }
                    }

                    withContext(Dispatchers.Main) { lines = parsed }

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
    }

    LaunchedEffect(isPlaying, playbackState) {
        while (isActive) {
            position = playerConnection.player.currentPosition
            duration = playerConnection.player.duration
            delay(32) // Roughly 30 fps for liquid smooth fast recomposition on gradients
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

    if (showLyricsMenu && mediaMetadata != null) {
        LyricsMenu(
            lyricsProvider = { LyricsEntity(mediaMetadata!!.id, lines.joinToString("\n") { it.text }) },
            mediaMetadataProvider = { mediaMetadata!! },
            onDismiss = { showLyricsMenu = false }
        )
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
                    // LYRICS NOT FOUND STATE
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

                            Button(
                                onClick = { showLyricsMenu = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(painterResource(R.drawable.search), null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Search", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontFamily = SfProDisplayFontFamily)
                            }
                        }
                    }
                } else {
                    // LOADED LYRICS STATE
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 120.dp),
                        horizontalAlignment = baseAlignment
                    ) {
                        itemsIndexed(lines) { index, item ->
                            val isActiveLine = index == currentLineIndex
                            
                            val color by animateColorAsState(if (isActiveLine) Color.White else Color.White.copy(0.35f), label = "color")
                            val scale by animateFloatAsState(
                                if (isActiveLine) {
                                    if (experimentalLyrics) 1.05f else 1.0f
                                } else 1.0f, 
                                label = "scale"
                            )
                            val blurRadius by animateDpAsState(
                                if (isActiveLine || !experimentalLyrics) 0.dp else 4.dp, 
                                label = "blur"
                            )

                            // Apply agent positioning if enabled
                            val isBackgroundVocals = respectAgent && item.text.startsWith("(") && item.text.endsWith(")")
                            val specificAlignment = if (isBackgroundVocals) {
                                when(baseAlignment) {
                                    Alignment.Start -> Alignment.End
                                    Alignment.End -> Alignment.Start
                                    else -> Alignment.End
                                }
                            } else baseAlignment
                            
                            val specificTextAlign = if (isBackgroundVocals) {
                                when(baseTextAlign) {
                                    TextAlign.Start -> TextAlign.End
                                    TextAlign.End -> TextAlign.Start
                                    else -> TextAlign.End
                                }
                            } else baseTextAlign

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 32.dp, vertical = 14.dp)
                                    .graphicsLayer { 
                                        scaleX = scale
                                        scaleY = scale 
                                    }
                                    .blur(blurRadius)
                                    .clickable(enabled = lyricsClick) { 
                                        playerConnection.player.seekTo(item.time)
                                        isAutoScrollEnabled = true 
                                    },
                                horizontalAlignment = specificAlignment
                            ) {
                                val textStyle = TextStyle(
                                    fontFamily = SfProDisplayFontFamily,
                                    color = if (isActiveLine && item.words.isNotEmpty() && wordByWord != WordByWordStyle.NONE) Color.Unspecified else color,
                                    fontSize = lyricsTextSize.sp,
                                    fontWeight = if (isActiveLine) FontWeight.Black else FontWeight.Bold,
                                    lineHeight = (lyricsTextSize * lyricsLineSpacing).sp,
                                    textAlign = specificTextAlign,
                                    shadow = if (isActiveLine && glowingLyrics) Shadow(
                                        color = Color.White.copy(alpha = 0.5f),
                                        blurRadius = 16f
                                    ) else null
                                )

                                val annotatedString = buildAnnotatedString {
                                    if (isActiveLine && item.words.isNotEmpty() && wordByWord != WordByWordStyle.NONE) {
                                        item.words.forEach { word ->
                                            val wordColor = when {
                                                animatedPos >= (word.time + word.duration) -> Color.White
                                                animatedPos < word.time -> Color.White.copy(alpha = 0.35f)
                                                else -> {
                                                    val progress = ((animatedPos - word.time) / (word.duration).toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                                                    androidx.compose.ui.graphics.lerp(Color.White.copy(alpha = 0.35f), Color.White, progress)
                                                }
                                            }
                                            withStyle(SpanStyle(color = wordColor)) {
                                                append(word.text)
                                            }
                                        }
                                    } else {
                                        append(item.text)
                                    }
                                }

                                Text(
                                    text = annotatedString, 
                                    style = textStyle,
                                )
                                
                                if (!item.romanizedText.isNullOrBlank()) {
                                    Text(
                                        text = item.romanizedText, 
                                        color = color.copy(alpha = if (isActiveLine) 0.7f else 0.2f), 
                                        fontSize = (lyricsTextSize * 0.6f).sp, 
                                        fontWeight = FontWeight.Medium,
                                        fontFamily = SfProDisplayFontFamily,
                                        textAlign = specificTextAlign,
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Autoscroll Resume Button
                Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp)) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = !isAutoScrollEnabled && lines.isNotEmpty() && lines[0].text != LYRICS_NOT_FOUND,
                        enter = fadeIn() + slideInVertically { it },
                        exit = fadeOut() + slideOutVertically { it }
                    ) {
                        Button(
                            onClick = { isAutoScrollEnabled = true }, 
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(0.2f)), 
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(painterResource(R.drawable.sync), null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Resume Autoscroll", color = Color.White, fontWeight = FontWeight.Bold, fontFamily = SfProDisplayFontFamily)
                        }
                    }
                }
            }

            // Controls
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