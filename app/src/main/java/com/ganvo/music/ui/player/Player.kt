package com.ganvo.music.ui.player

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Player.STATE_ENDED
import androidx.media3.common.Player.STATE_READY
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.ganvo.music.LocalPlayerConnection
import com.ganvo.music.R
import com.ganvo.music.constants.PlayerBackgroundStyle
import com.ganvo.music.constants.PlayerBackgroundStyleKey
import com.ganvo.music.constants.PlayerHorizontalPadding
import com.ganvo.music.constants.PlayerTextAlignmentKey
import com.ganvo.music.constants.SliderStyle
import com.ganvo.music.constants.SliderStyleKey
import com.ganvo.music.extensions.togglePlayPause
import com.ganvo.music.extensions.toggleRepeatMode
import com.ganvo.music.models.MediaMetadata
import com.ganvo.music.ui.component.BottomSheet
import com.ganvo.music.ui.component.BottomSheetState
import com.ganvo.music.ui.component.LocalMenuState
import com.ganvo.music.ui.component.PlayerSliderTrack
import com.ganvo.music.ui.component.ResizableIconButton
import com.ganvo.music.ui.component.rememberBottomSheetState
import com.ganvo.music.ui.menu.PlayerMenu
import com.ganvo.music.ui.screens.settings.PlayerTextAlignment
import com.ganvo.music.utils.makeTimeString
import com.ganvo.music.utils.rememberEnumPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import me.saket.squiggles.SquigglySlider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheetPlayer(
    state: BottomSheetState,
    navController: NavController,
    onOpenFullscreenLyrics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return

    val playerTextAlignment by rememberEnumPreference(PlayerTextAlignmentKey, PlayerTextAlignment.CENTER)
    val sliderStyle by rememberEnumPreference(SliderStyleKey, SliderStyle.DEFAULT)
    val playerBackground by rememberEnumPreference(PlayerBackgroundStyleKey, PlayerBackgroundStyle.DEFAULT)
    
    val playbackState by playerConnection.playbackState.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val currentSong by playerConnection.currentSong.collectAsState(initial = null)
    val repeatMode by playerConnection.repeatMode.collectAsState()
    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsState()
    val canSkipNext by playerConnection.canSkipNext.collectAsState()

    var position by rememberSaveable(playbackState) { mutableLongStateOf(playerConnection.player.currentPosition) }
    var duration by rememberSaveable(playbackState) { mutableLongStateOf(playerConnection.player.duration) }
    var sliderPosition by remember { mutableStateOf<Long?>(null) }
    var showDetailsDialog by rememberSaveable { mutableStateOf(false) }

    var backgroundImageUrl by remember { mutableStateOf<String?>(null) }

    // Dynamic coloring based on background style
    val playerContentColor = if (playerBackground == PlayerBackgroundStyle.DEFAULT) {
        MaterialTheme.colorScheme.onSurface
    } else {
        Color.White
    }
    val playPauseIconTint = if (playerBackground == PlayerBackgroundStyle.DEFAULT) {
        MaterialTheme.colorScheme.surface
    } else {
        Color.Black
    }
    val titleShadow = if (playerBackground == PlayerBackgroundStyle.DEFAULT) null else Shadow(color = Color.Black.copy(alpha = 0.5f), blurRadius = 12f)
    val artistShadow = if (playerBackground == PlayerBackgroundStyle.DEFAULT) null else Shadow(color = Color.Black.copy(alpha = 0.5f), blurRadius = 8f)


    LaunchedEffect(mediaMetadata) {
        backgroundImageUrl = mediaMetadata?.thumbnailUrl
    }

    LaunchedEffect(playbackState) {
        if (playbackState == STATE_READY) {
            while (isActive) {
                delay(100)
                position = playerConnection.player.currentPosition
                duration = playerConnection.player.duration
            }
        }
    }

    val queueSheetState = rememberBottomSheetState(
        dismissedBound = 64.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
        expandedBound = state.expandedBound,
    )

    BottomSheet(
        state = state,
        modifier = modifier,
        brushBackgroundColor = Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent)),
        onDismiss = {
            playerConnection.service.clearAutomix()
            playerConnection.player.stop()
            playerConnection.player.clearMediaItems()
        },
        collapsedContent = {
            MiniPlayer(position = position, duration = duration)
        },
    ) {
        val controlsContent: @Composable ColumnScope.(MediaMetadata) -> Unit = { mediaMetadata ->
            val playPauseRoundness by animateDpAsState(targetValue = if (isPlaying) 24.dp else 36.dp, tween(90), label = "")

            Row(
                horizontalArrangement = if (playerTextAlignment == PlayerTextAlignment.CENTER) Arrangement.Center else Arrangement.Start,
                modifier = Modifier.fillMaxWidth().padding(horizontal = PlayerHorizontalPadding)
            ) {
                AnimatedContent(targetState = mediaMetadata.title, label = "") { title ->
                    Text(
                        text = title,
                        style = TextStyle(
                            fontSize = MaterialTheme.typography.headlineSmall.fontSize,
                            fontWeight = FontWeight.Black,
                            color = playerContentColor,
                            shadow = titleShadow
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee()
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Row(
                horizontalArrangement = if (playerTextAlignment == PlayerTextAlignment.CENTER) Arrangement.Center else Arrangement.Start,
                modifier = Modifier.fillMaxWidth().padding(horizontal = PlayerHorizontalPadding)
            ) {
                AnimatedContent(targetState = mediaMetadata.artists.joinToString { it.name }, label = "") { artists ->
                    Text(
                        text = artists,
                        style = TextStyle(
                            fontSize = MaterialTheme.typography.titleMedium.fontSize,
                            fontWeight = FontWeight.Medium,
                            color = playerContentColor.copy(alpha = 0.8f),
                            shadow = artistShadow
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee()
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            when (sliderStyle) {
                SliderStyle.SQUIGGLY -> {
                    SquigglySlider(
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
                            activeTrackColor = playerContentColor,
                            inactiveTrackColor = playerContentColor.copy(alpha = 0.2f),
                            thumbColor = playerContentColor
                        ),
                        modifier = Modifier.padding(horizontal = PlayerHorizontalPadding)
                    )
                }
                SliderStyle.SLIM -> {
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
                        thumb = { Spacer(modifier = Modifier.size(0.dp)) },
                        track = { sliderState ->
                            PlayerSliderTrack(
                                sliderState = sliderState,
                                colors = SliderDefaults.colors(
                                    activeTrackColor = playerContentColor,
                                    inactiveTrackColor = playerContentColor.copy(alpha = 0.2f),
                                )
                            )
                        },
                        modifier = Modifier.padding(horizontal = PlayerHorizontalPadding)
                    )
                }
                SliderStyle.DEFAULT -> {
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
                            activeTrackColor = playerContentColor,
                            inactiveTrackColor = playerContentColor.copy(alpha = 0.2f),
                            thumbColor = playerContentColor
                        ),
                        modifier = Modifier.padding(horizontal = PlayerHorizontalPadding)
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(horizontal = PlayerHorizontalPadding + 8.dp)
            ) {
                Text(makeTimeString(sliderPosition ?: position), style = MaterialTheme.typography.labelMedium, color = playerContentColor.copy(alpha = 0.8f))
                Text(if (duration != C.TIME_UNSET) makeTimeString(duration) else "", style = MaterialTheme.typography.labelMedium, color = playerContentColor.copy(alpha = 0.8f))
            }

            Spacer(Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth().padding(horizontal = PlayerHorizontalPadding)
            ) {
                ResizableIconButton(
                    icon = when (repeatMode) {
                        Player.REPEAT_MODE_OFF, Player.REPEAT_MODE_ALL -> R.drawable.repeat
                        Player.REPEAT_MODE_ONE -> R.drawable.repeat_one
                        else -> R.drawable.repeat
                    },
                    color = playerContentColor,
                    modifier = Modifier.size(32.dp).alpha(if (repeatMode == Player.REPEAT_MODE_OFF) 0.5f else 1f),
                    onClick = playerConnection.player::toggleRepeatMode
                )

                ResizableIconButton(
                    icon = R.drawable.skip_previous,
                    enabled = canSkipPrevious,
                    color = playerContentColor,
                    modifier = Modifier.size(40.dp),
                    onClick = playerConnection::seekToPrevious
                )

                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(playerContentColor)
                        .clickable {
                            if (playbackState == STATE_ENDED) {
                                playerConnection.player.seekTo(0, 0)
                                playerConnection.player.playWhenReady = true
                            } else playerConnection.player.togglePlayPause()
                        }
                ) {
                    Image(
                        painter = painterResource(if (playbackState == STATE_ENDED) R.drawable.replay else if (isPlaying) R.drawable.pause else R.drawable.play),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(playPauseIconTint),
                        modifier = Modifier.align(Alignment.Center).size(36.dp)
                    )
                }

                ResizableIconButton(
                    icon = R.drawable.skip_next,
                    enabled = canSkipNext,
                    color = playerContentColor,
                    modifier = Modifier.size(40.dp),
                    onClick = playerConnection::seekToNext
                )

                ResizableIconButton(
                    icon = if (currentSong?.song?.liked == true) R.drawable.favorite else R.drawable.favorite_border,
                    color = if (currentSong?.song?.liked == true) MaterialTheme.colorScheme.error else playerContentColor,
                    modifier = Modifier.size(32.dp),
                    onClick = playerConnection::toggleLike
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when (playerBackground) {
                PlayerBackgroundStyle.BLUR -> {
                    AsyncImage(
                        model = backgroundImageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().blur(80.dp)
                    )
                    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.65f), Color.Black.copy(alpha = 0.95f)))))
                }
                PlayerBackgroundStyle.GRADIENT -> {
                    Box(modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.background))
                    ))
                }
                PlayerBackgroundStyle.DEFAULT -> {
                    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top))
                .padding(bottom = queueSheetState.collapsedBound)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { state.collapseSoft() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.expand_more),
                        contentDescription = "Collapse",
                        tint = playerContentColor,
                        modifier = Modifier.size(32.dp)
                    )
                }

                IconButton(
                    onClick = {
                        mediaMetadata?.let { meta ->
                            menuState.show {
                                PlayerMenu(
                                    mediaMetadata = meta,
                                    navController = navController,
                                    playerBottomSheetState = state,
                                    onShowDetailsDialog = {
                                        showDetailsDialog = true
                                    },
                                    onDismiss = menuState::dismiss
                                )
                            }
                        }
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.more_vert),
                        contentDescription = "Menu",
                        tint = playerContentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.weight(1f).padding(horizontal = 32.dp)
            ) {
                Thumbnail(
                    sliderPositionProvider = { sliderPosition },
                    onOpenFullscreenLyrics = onOpenFullscreenLyrics,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(32.dp))
                )
            }

            mediaMetadata?.let { controlsContent(it) }

            Spacer(Modifier.height(48.dp))
        }

        Queue(
            state = queueSheetState,
            playerBottomSheetState = state,
            navController = navController,
        )
    }
}