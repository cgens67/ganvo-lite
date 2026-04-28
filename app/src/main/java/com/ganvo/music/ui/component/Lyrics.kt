package com.ganvo.music.ui.component

import android.annotation.SuppressLint
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.ganvo.music.extensions.toggleRepeatMode
import com.ganvo.music.lyrics.LyricsEntry
import com.ganvo.music.lyrics.LyricsEntry.Companion.HEAD_LYRICS_ENTRY
import com.ganvo.music.lyrics.LyricsUtils.findCurrentLineIndex
import com.ganvo.music.lyrics.LyricsUtils.parseLyrics
import com.ganvo.music.ui.component.shimmer.ShimmerHost
import com.ganvo.music.ui.component.shimmer.TextPlaceholder
import com.ganvo.music.ui.menu.SongMenu
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

    val playbackState by playerConnection.playbackState.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    val currentAudioVolume by playerConnection.service.playerVolume.collectAsState()

    BackHandler(enabled = onNavigateBack != null) {
        onNavigateBack?.invoke()
    }

    LaunchedEffect(currentSongId) {
        currentSongId?.let { songId ->
            isLoadingLyrics = true
            isAutoScrollEnabled = true
            withContext(Dispatchers.IO) {
                try {
                    val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, com.ganvo.music.di.LyricsHelperEntryPoint::class.java)
                    val fetched = entryPoint.lyricsHelper().getLyrics(mediaMetadata!!)
                    
                    val parsed = if (fetched.startsWith("[")) {
                        listOf(HEAD_LYRICS_ENTRY) + parseLyrics(fetched)
                    } else {
                        fetched.lines().mapIndexed { i, l -> LyricsEntry(i * 1000L, l) }
                    }

                    withContext(Dispatchers.Main) { lines = parsed }

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

    LaunchedEffect(isPlaying, playbackState) {
        while (isActive) {
            position = playerConnection.player.currentPosition
            duration = playerConnection.player.duration
            delay(500)
        }
    }

    val currentLineIndex = remember(lines, position) { findCurrentLineIndex(lines, position) }
    val lazyListState = rememberLazyListState()
    val isDragged by lazyListState.interactionSource.collectIsDraggedAsState()

    LaunchedEffect(isDragged) { if (isDragged) isAutoScrollEnabled = false }

    LaunchedEffect(currentLineIndex, isAutoScrollEnabled) {
        if (isAutoScrollEnabled && currentLineIndex != -1 && lines.isNotEmpty()) {
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
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { onNavigateBack?.invoke() }) {
                    Icon(painterResource(R.drawable.expand_more), null, tint = Color.White)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Now Playing", color = Color.White.copy(0.7f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(mediaMetadata?.title ?: "Unknown", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                IconButton(onClick = {
                    currentSong?.let { song ->
                        menuState.show { SongMenu(originalSong = song, navController = androidx.navigation.compose.rememberNavController(), onDismiss = menuState::dismiss) }
                    }
                }) {
                    Icon(painterResource(R.drawable.more_horiz), null, tint = Color.White)
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (isLoadingLyrics) {
                    ShimmerHost(modifier = Modifier.align(Alignment.Center)) { repeat(5) { TextPlaceholder(modifier = Modifier.padding(24.dp)) } }
                } else {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 120.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        itemsIndexed(lines) { index, item ->
                            val isActiveLine = index == currentLineIndex
                            
                            val color by animateColorAsState(if (isActiveLine) Color.White else Color.White.copy(0.35f), label = "color")
                            val scale by animateFloatAsState(if (isActiveLine) 1.08f else 1.0f, label = "scale")
                            val blurRadius by animateDpAsState(if (isActiveLine) 0.dp else 6.dp, label = "blur")

                            Column(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 14.dp)
                                    .graphicsLayer { scaleX = scale; scaleY = scale }.blur(blurRadius)
                                    .clickable { playerConnection.player.seekTo(item.time); isAutoScrollEnabled = true },
                                horizontalAlignment = Alignment.Start
                            ) {
                                Text(text = item.text, color = color, fontSize = 26.sp, fontWeight = FontWeight.Black, lineHeight = 34.sp)
                                if (!item.romanizedText.isNullOrBlank()) {
                                    Text(text = item.romanizedText, color = color.copy(alpha = if (isActiveLine) 0.7f else 0.2f), fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 6.dp))
                                }
                            }
                        }
                    }
                }

                Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp)) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = !isAutoScrollEnabled,
                        enter = fadeIn() + slideInVertically { it },
                        exit = fadeOut() + slideOutVertically { it }
                    ) {
                        Button(onClick = { isAutoScrollEnabled = true }, colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(0.2f)), shape = RoundedCornerShape(12.dp)) {
                            Icon(painterResource(R.drawable.sync), null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Resume Autoscroll", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 30.dp, vertical = 20.dp)) {
                Slider(value = position.toFloat(), onValueChange = { playerConnection.player.seekTo(it.toLong()) }, valueRange = 0f..(duration.toFloat().coerceAtLeast(1f)), colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(0.2f)))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(makeTimeString(position), color = Color.White.copy(0.6f), fontSize = 12.sp)
                    Text(makeTimeString(duration), color = Color.White.copy(0.6f), fontSize = 12.sp)
                }

                Spacer(Modifier.height(20.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { playerConnection.player.toggleRepeatMode() }) { Icon(painterResource(R.drawable.repeat), null, tint = Color.White.copy(0.6f)) }
                    IconButton(onClick = { playerConnection.player.seekToPrevious() }) { Icon(painterResource(R.drawable.skip_previous), null, modifier = Modifier.size(40.dp), tint = Color.White) }
                    Surface(shape = CircleShape, color = Color.White, modifier = Modifier.size(70.dp).clickable { playerConnection.player.togglePlayPause() }) {
                        Box(contentAlignment = Alignment.Center) { Icon(painterResource(if (isPlaying) R.drawable.pause else R.drawable.play), null, tint = Color.Black, modifier = Modifier.size(36.dp)) }
                    }
                    IconButton(onClick = { playerConnection.player.seekToNext() }) { Icon(painterResource(R.drawable.skip_next), null, modifier = Modifier.size(40.dp), tint = Color.White) }
                    IconButton(onClick = { /* Shuffle logic */ }) { Icon(painterResource(R.drawable.shuffle), null, tint = Color.White.copy(0.6f)) }
                }

                Spacer(Modifier.height(24.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.volume_off), null, tint = Color.White.copy(0.6f), modifier = Modifier.size(18.dp))
                    Slider(
                        value = currentAudioVolume,
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