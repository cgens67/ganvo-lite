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
import com.ganvo.music.constants.*
import com.ganvo.music.db.entities.LyricsEntity
import com.ganvo.music.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
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

    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val currentSongId = mediaMetadata?.id
    val currentSong by playerConnection.currentSong.collectAsState(initial = null)

    var isLoadingLyrics by remember(currentSongId) { mutableStateOf(false) }
    var lines by remember { mutableStateOf<List<LyricsEntry>>(emptyList()) }
    var isAutoScrollEnabled by remember { mutableStateOf(true) }
    var showLyricsMenu by remember { mutableStateOf(false) }

    val playbackState by playerConnection.playbackState.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }

    val lyricsPosition by rememberEnumPreference(LyricsTextPositionKey, LyricsPosition.CENTER)
    val wordByWord by rememberEnumPreference(WordByWordStyleKey, WordByWordStyle.FADE)
    val lyricsTextSize by rememberPreference(LyricsTextSizeKey, 24f)
    val lyricsLineSpacing by rememberPreference(LyricsLineSpacingKey, 1.2f)
    val glowingLyrics by rememberPreference(GlowingLyricsKey, false)
    val experimentalLyrics by rememberPreference(ExperimentalLyricsKey, false)

    val baseAlignment = when (lyricsPosition) {
        LyricsPosition.LEFT -> Alignment.Start
        LyricsPosition.CENTER -> Alignment.CenterHorizontally
        LyricsPosition.RIGHT -> Alignment.End
    }

    fun fetchLyrics() {
        currentSongId?.let {
            isLoadingLyrics = true
            isAutoScrollEnabled = true
            scope.launch(Dispatchers.IO) {
                try {
                    val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, com.ganvo.music.di.LyricsHelperEntryPoint::class.java)
                    val fetched = entryPoint.lyricsHelper().getLyrics(mediaMetadata!!)
                    val parsed = if (fetched == LYRICS_NOT_FOUND || fetched == "null") {
                        listOf(LyricsEntry(0L, LYRICS_NOT_FOUND))
                    } else {
                        parseLyrics(fetched).let { if (it.isNotEmpty()) listOf(HEAD_LYRICS_ENTRY) + it else it }
                    }
                    withContext(Dispatchers.Main) { 
                        lines = parsed
                        isLoadingLyrics = false
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { isLoadingLyrics = false }
                }
            }
        }
    }

    LaunchedEffect(currentSongId) { fetchLyrics() }

    LaunchedEffect(isPlaying, playbackState) {
        while (isActive) {
            position = playerConnection.player.currentPosition
            duration = playerConnection.player.duration
            delay(16) // 60fps refresh for word sync
        }
    }

    val currentLineIndex = remember(lines, position) { findCurrentLineIndex(lines, position) }
    val lazyListState = rememberLazyListState()
    val isDragged by lazyListState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(isDragged) { if (isDragged) isAutoScrollEnabled = false }

    LaunchedEffect(currentLineIndex, isAutoScrollEnabled) {
        if (isAutoScrollEnabled && currentLineIndex != -1 && lines.size > 1) {
            lazyListState.animateScrollToItem(currentLineIndex, -250)
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        AsyncImage(
            model = mediaMetadata?.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(60.dp).alpha(0.45f)
        )

        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            // Header (Omitted for brevity, keep your existing row)
            
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (isLoadingLyrics) {
                    ShimmerHost(modifier = Modifier.align(Alignment.Center)) { repeat(5) { TextPlaceholder(modifier = Modifier.padding(24.dp)) } }
                } else if (lines.isEmpty() || lines[0].text == LYRICS_NOT_FOUND) {
                    // Empty state (Keep your existing Refetch/Search UI)
                } else {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 200.dp),
                        horizontalAlignment = baseAlignment
                    ) {
                        itemsIndexed(lines) { index, item ->
                            val isActiveLine = index == currentLineIndex
                            val color by animateColorAsState(if (isActiveLine) Color.White else Color.White.copy(0.35f))
                            
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 32.dp, vertical = 14.dp)
                                    .clickable { playerConnection.player.seekTo(item.time); isAutoScrollEnabled = true },
                                horizontalAlignment = baseAlignment
                            ) {
                                val textStyle = TextStyle(
                                    fontFamily = SfProDisplayFontFamily,
                                    fontSize = lyricsTextSize.sp,
                                    fontWeight = if (isActiveLine) FontWeight.Black else FontWeight.Bold,
                                    lineHeight = (lyricsTextSize * lyricsLineSpacing).sp,
                                    shadow = if (isActiveLine && glowingLyrics) Shadow(Color.White.copy(0.5f), blurRadius = 16f) else null
                                )

                                if (isActiveLine && item.words.isNotEmpty() && wordByWord != WordByWordStyle.NONE) {
                                    Text(
                                        text = buildAnnotatedString {
                                            item.words.forEach { word ->
                                                val wordProgress = ((position - word.startTime).toFloat() / (word.endTime - word.startTime).toFloat()).coerceIn(0f, 1f)
                                                val wordColor = androidx.compose.ui.graphics.lerp(Color.White.copy(0.35f), Color.White, wordProgress)
                                                withStyle(SpanStyle(color = wordColor)) { append(word.text) }
                                            }
                                        },
                                        style = textStyle
                                    )
                                } else {
                                    Text(text = item.text, style = textStyle, color = color)
                                }
                            }
                        }
                    }
                }
            }
            // Bottom Controls (Keep your existing slider and playback buttons)
        }
    }
}