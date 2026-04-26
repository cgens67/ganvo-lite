package com.ganvo.music.ui.component

import android.annotation.SuppressLint
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ganvo.music.LocalPlayerConnection
import com.ganvo.music.R
import com.ganvo.music.constants.LyricsClickKey
import com.ganvo.music.constants.ShowLyricsKey
import com.ganvo.music.db.entities.LyricsEntity
import com.ganvo.music.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.ganvo.music.extensions.togglePlayPause
import com.ganvo.music.lyrics.LyricsEntry
import com.ganvo.music.lyrics.LyricsEntry.Companion.HEAD_LYRICS_ENTRY
import com.ganvo.music.lyrics.LyricsUtils.findCurrentLineIndex
import com.ganvo.music.lyrics.LyricsUtils.parseLyrics
import com.ganvo.music.ui.component.shimmer.ShimmerHost
import com.ganvo.music.ui.component.shimmer.TextPlaceholder
import com.ganvo.music.utils.TransliterationUtils
import com.ganvo.music.utils.makeTimeString
import com.ganvo.music.utils.rememberPreference
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@RequiresApi(Build.VERSION_CODES.M)
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun Lyrics(
    sliderPositionProvider: () -> Long?,
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val density = LocalDensity.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val currentSongId = mediaMetadata?.id

    var currentLyricsEntity by remember(currentSongId) { mutableStateOf<LyricsEntity?>(null) }
    var isLoadingLyrics by remember(currentSongId) { mutableStateOf(false) }
    var lines by remember { mutableStateOf<List<LyricsEntry>>(emptyList()) }

    val playbackState by playerConnection.playbackState.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    val playerVolume by playerConnection.service.playerVolume.collectAsState()

    // Load Lyrics and Transliterate
    LaunchedEffect(currentSongId) {
        currentSongId?.let { songId ->
            isLoadingLyrics = true
            withContext(Dispatchers.IO) {
                try {
                    val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, com.ganvo.music.di.LyricsHelperEntryPoint::class.java)
                    val fetched = entryPoint.lyricsHelper().getLyrics(mediaMetadata!!)
                    
                    val parsed = if (fetched.startsWith("[")) {
                        listOf(HEAD_LYRICS_ENTRY) + parseLyrics(fetched)
                    } else {
                        fetched.lines().mapIndexed { i, l -> LyricsEntry(i * 1000L, l) }
                    }

                    // Preliminary set to show lyrics quickly
                    withContext(Dispatchers.Main) { lines = parsed }

                    // Fetch Romanization for each line
                    val romanizedLines = parsed.map { entry ->
                        if (entry.text.isBlank()) entry 
                        else entry.copy(romanizedText = TransliterationUtils.transliterate(entry.text))
                    }
                    
                    withContext(Dispatchers.Main) { 
                        lines = romanizedLines
                        isLoadingLyrics = false
                    }
                } catch (e: Exception) {
                    isLoadingLyrics = false
                }
            }
        }
    }

    // Update position
    LaunchedEffect(isPlaying, playbackState) {
        while (isActive) {
            position = playerConnection.player.currentPosition
            duration = playerConnection.player.duration
            delay(500)
        }
    }

    val currentLineIndex = remember(lines, position) {
        findCurrentLineIndex(lines, position)
    }

    val lazyListState = rememberLazyListState()
    LaunchedEffect(currentLineIndex) {
        if (currentLineIndex != -1 && lines.isNotEmpty()) {
            lazyListState.animateScrollToItem(currentLineIndex, -200)
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        // Background Blur
        AsyncImage(
            model = mediaMetadata?.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(50.dp).alpha(0.4f)
        )

        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { onNavigateBack?.invoke() }) {
                    Icon(painterResource(R.drawable.expand_more), null, tint = Color.White)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Now Playing", color = Color.White.copy(0.7f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(mediaMetadata?.title ?: "Unknown", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                IconButton(onClick = { /* Menu */ }) {
                    Icon(painterResource(R.drawable.more_horiz), null, tint = Color.White)
                }
            }

            // Lyrics List
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (isLoadingLyrics) {
                    ShimmerHost(modifier = Modifier.align(Alignment.Center)) {
                        repeat(5) { TextPlaceholder(modifier = Modifier.padding(20.dp)) }
                    }
                } else {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 100.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        itemsIndexed(lines) { index, item ->
                            val isActiveLine = index == currentLineIndex
                            val color by animateColorAsState(if (isActiveLine) Color.White else Color.White.copy(0.3f))
                            val scale by animateFloatAsState(if (isActiveLine) 1.1f else 1.0f)

                            Column(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 30.dp, vertical = 12.dp)
                                    .clickable { playerConnection.player.seekTo(item.time) },
                                horizontalAlignment = Alignment.Start
                            ) {
                                Text(
                                    text = item.text,
                                    color = color,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 32.sp,
                                    modifier = Modifier.graphicsLayer(scaleX = scale, scaleY = scale)
                                )
                                if (!item.romanizedText.isNullOrBlank()) {
                                    Text(
                                        text = item.romanizedText,
                                        color = color.copy(alpha = if (isActiveLine) 0.6f else 0.2f),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Normal,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Player Controls
            Column(modifier = Modifier.fillMaxWidth().padding(30.dp)) {
                // Progress Slider
                Slider(
                    value = position.toFloat(),
                    onValueChange = { playerConnection.player.seekTo(it.toLong()) },
                    valueRange = 0f..(duration.toFloat().coerceAtLeast(1f)),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(0.2f)
                    )
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(makeTimeString(position), color = Color.White.copy(0.6f), fontSize = 12.sp)
                    Text(makeTimeString(duration), color = Color.White.copy(0.6f), fontSize = 12.sp)
                }

                Spacer(Modifier.height(20.dp))

                // Control Buttons
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { playerConnection.player.toggleRepeatMode() }) {
                        Icon(painterResource(R.drawable.repeat), null, tint = Color.White.copy(0.6f))
                    }
                    IconButton(onClick = { playerConnection.player.seekToPrevious() }) {
                        Icon(painterResource(R.drawable.skip_previous), null, modifier = Modifier.size(40.dp), tint = Color.White)
                    }
                    Surface(
                        shape = CircleShape,
                        color = Color.White,
                        modifier = Modifier.size(70.dp).clickable { playerConnection.player.togglePlayPause() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                    IconButton(onClick = { playerConnection.player.seekToNext() }) {
                        Icon(painterResource(R.drawable.skip_next), null, modifier = Modifier.size(40.dp), tint = Color.White)
                    }
                    IconButton(onClick = { /* Shuffle */ }) {
                        Icon(painterResource(R.drawable.shuffle), null, tint = Color.White.copy(0.6f))
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Volume Slider
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.volume_mute), null, tint = Color.White.copy(0.6f), modifier = Modifier.size(16.dp))
                    Slider(
                        value = playerVolume,
                        onValueChange = { playerConnection.service.playerVolume.value = it },
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                        colors = SliderDefaults.colors(thumbColor = Color.Transparent, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(0.2f))
                    )
                    Icon(painterResource(R.drawable.volume_up), null, tint = Color.White.copy(0.6f), modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}