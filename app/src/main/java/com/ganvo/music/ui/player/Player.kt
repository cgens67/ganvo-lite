package com.ganvo.music.ui.player

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Player.STATE_BUFFERING
import androidx.media3.common.Player.STATE_ENDED
import androidx.media3.common.Player.STATE_READY
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.ganvo.music.LocalPlayerConnection
import com.ganvo.music.R
import com.ganvo.music.constants.PlayerBackgroundStyle
import com.ganvo.music.constants.PlayerBackgroundStyleKey
import com.ganvo.music.constants.PlayerHorizontalPadding
import com.ganvo.music.constants.SliderStyle
import com.ganvo.music.constants.SliderStyleKey
import com.ganvo.music.extensions.togglePlayPause
import com.ganvo.music.extensions.toggleRepeatMode
import com.ganvo.music.models.MediaMetadata
import com.ganvo.music.playback.PlayerConnection
import com.ganvo.music.ui.component.BottomSheet
import com.ganvo.music.ui.component.BottomSheetState
import com.ganvo.music.ui.component.LocalBottomSheetPageState
import com.ganvo.music.ui.component.LocalMenuState
import com.ganvo.music.ui.component.PlayerSliderTrack
import com.ganvo.music.ui.component.rememberBottomSheetState
import com.ganvo.music.ui.menu.PlayerMenu
import com.ganvo.music.utils.makeTimeString
import com.ganvo.music.utils.rememberEnumPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.saket.squiggles.SquigglySlider
import kotlin.math.abs

private const val SeekbarSettleToleranceMs = 1_500L

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
    val bottomSheetPageState = LocalBottomSheetPageState.current
    val playerConnection = LocalPlayerConnection.current ?: return

    val sliderStyle by rememberEnumPreference(SliderStyleKey, SliderStyle.DEFAULT)
    val playerBackground by rememberEnumPreference(PlayerBackgroundStyleKey, PlayerBackgroundStyle.DEFAULT)

    val textBackgroundColor = when (playerBackground) {
        PlayerBackgroundStyle.DEFAULT -> MaterialTheme.colorScheme.onBackground
        else -> Color.White
    }
    val icBackgroundColor = when (playerBackground) {
        PlayerBackgroundStyle.DEFAULT -> MaterialTheme.colorScheme.surface
        else -> Color.Black
    }

    val playbackState by playerConnection.playbackState.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val currentSong by playerConnection.currentSong.collectAsState(initial = null)
    val currentSongLiked = currentSong?.song?.liked == true
    val repeatMode by playerConnection.repeatMode.collectAsState()
    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsState()
    val canSkipNext by playerConnection.canSkipNext.collectAsState()

    var position by rememberSaveable(mediaMetadata?.id) { mutableLongStateOf(playerConnection.player.currentPosition) }
    var duration by rememberSaveable(mediaMetadata?.id) { mutableLongStateOf(playerConnection.player.duration) }
    var sliderPosition by remember(mediaMetadata?.id) { mutableStateOf<Long?>(null) }
    var isUserSeeking by remember(mediaMetadata?.id) { mutableStateOf(false) }

    val isLoading = playbackState == STATE_BUFFERING || sliderPosition != null
    var showDetailsDialog by rememberSaveable { mutableStateOf(false) }

    val queueSheetState = rememberBottomSheetState(
        dismissedBound = 64.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
        expandedBound = state.expandedBound,
        collapsedBound = 64.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
        initialAnchor = 0,
    )

    val coroutineScope = rememberCoroutineScope()
    val smoothAnim = spring<Dp>(dampingRatio = 0.85f, stiffness = 150f)

    LaunchedEffect(mediaMetadata?.id, playbackState) {
        if (playbackState == STATE_READY) {
            while (isActive) {
                delay(100L)
                val isTransitioning = playerConnection.player.currentMediaItem?.mediaId != mediaMetadata?.id
                val currentPlayerPosition = playerConnection.player.currentPosition
                val currentPlayerDuration = playerConnection.player.duration

                if (!isTransitioning) {
                    position = currentPlayerPosition
                    duration = currentPlayerDuration
                    if (!isUserSeeking) {
                        sliderPosition?.let { targetPosition ->
                            val clampedTargetPosition = when {
                                currentPlayerDuration > 0L && currentPlayerDuration != C.TIME_UNSET -> {
                                    targetPosition.coerceIn(0L, currentPlayerDuration)
                                }
                                else -> targetPosition.coerceAtLeast(0L)
                            }
                            if (abs(currentPlayerPosition - clampedTargetPosition) <= SeekbarSettleToleranceMs) {
                                sliderPosition = null
                            }
                        }
                    }
                }
            }
        } else {
            mediaMetadata?.let {
                val metaDuration = it.duration.toLong() * 1000
                duration = if (metaDuration > 0) metaDuration else 0L
            }
            val currentPlayerPosition = playerConnection.player.currentPosition
            if (sliderPosition == null && currentPlayerPosition > 0L) {
                position = currentPlayerPosition
            }
        }
    }

    val onSliderValueChange: (Long) -> Unit = {
        isUserSeeking = true
        sliderPosition = it
    }

    val onSliderValueChangeFinished: () -> Unit = {
        sliderPosition?.let {
            val isTransitioning = playerConnection.player.currentMediaItem?.mediaId != mediaMetadata?.id
            if (isTransitioning) {
                playerConnection.player.seekToNext()
                playerConnection.player.seekTo(it)
            } else {
                playerConnection.player.seekTo(it)
            }
            position = it
        }
        isUserSeeking = false
    }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(state.isExpanded) {
        if (state.isExpanded) focusRequester.requestFocus()
    }

    if (showDetailsDialog && mediaMetadata != null) {
        AlertDialog(
            properties = DialogProperties(usePlatformDefaultWidth = false),
            onDismissRequest = { showDetailsDialog = false },
            icon = { Icon(painterResource(R.drawable.info), null) },
            confirmButton = {
                TextButton(onClick = { showDetailsDialog = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            text = {
                Column {
                    Text("Title: ${mediaMetadata!!.title}", style = MaterialTheme.typography.bodyMedium)
                    Text("Artist: ${mediaMetadata!!.artists.joinToString { it.name }}", style = MaterialTheme.typography.bodyMedium)
                    Text("ID: ${mediaMetadata!!.id}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        )
    }

    BottomSheet(
        state = state,
        modifier = modifier
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown || state.isCollapsed) return@onKeyEvent false
                when (keyEvent.key) {
                    Key.Spacebar -> { playerConnection.player.togglePlayPause(); true }
                    Key.N -> { if (keyEvent.isShiftPressed) { playerConnection.seekToNext(); true } else false }
                    Key.P -> { if (keyEvent.isShiftPressed) { playerConnection.seekToPrevious(); true } else false }
                    Key.L -> { playerConnection.toggleLike(); true }
                    else -> false
                }
            },
        brushBackgroundColor = Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent)),
        onDismiss = {
            playerConnection.player.stop()
            playerConnection.player.clearMediaItems()
        },
        collapsedContent = {
            MiniPlayer(
                position = position, 
                duration = duration
                // Removed the clickable modifier from MiniPlayer to allow BottomSheet's 
                // native gesture detection to handle vertical dragging properly.
            )
        },
    ) {
        // Player Background rendering
        Box(modifier = Modifier.fillMaxSize()) {
            when (playerBackground) {
                PlayerBackgroundStyle.BLUR -> {
                    AsyncImage(
                        model = mediaMetadata?.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().blur(80.dp)
                    )
                    Box(modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.65f), Color.Black.copy(alpha = 0.95f)))
                    ))
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

        // V4 Design Content
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top))
                .padding(bottom = queueSheetState.collapsedBound)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.IconButton(
                    onClick = { coroutineScope.launch { state.collapse(smoothAnim) } },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.expand_more),
                        contentDescription = "Collapse",
                        tint = textBackgroundColor,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Text(
                    text = "Now Playing",
                    style = MaterialTheme.typography.labelLarge,
                    color = textBackgroundColor.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Bold
                )

                androidx.compose.material3.IconButton(
                    onClick = {
                        mediaMetadata?.let { meta ->
                            menuState.show {
                                PlayerMenu(
                                    mediaMetadata = meta,
                                    navController = navController,
                                    playerBottomSheetState = state,
                                    onShowDetailsDialog = { showDetailsDialog = true },
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
                        tint = textBackgroundColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Thumbnail
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 32.dp, vertical = 16.dp)
            ) {
                Thumbnail(
                    sliderPositionProvider = { sliderPosition },
                    onOpenFullscreenLyrics = onOpenFullscreenLyrics,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(32.dp))
                )
            }

            // Controls
            mediaMetadata?.let { meta ->
                PlayerControlsContent(
                    mediaMetadata = meta,
                    sliderStyle = sliderStyle,
                    playbackState = playbackState,
                    isPlaying = isPlaying,
                    isLoading = isLoading,
                    repeatMode = repeatMode,
                    canSkipPrevious = canSkipPrevious,
                    canSkipNext = canSkipNext,
                    textBackgroundColor = textBackgroundColor,
                    icBackgroundColor = icBackgroundColor,
                    sliderPosition = sliderPosition,
                    position = position,
                    duration = duration,
                    playerConnection = playerConnection,
                    navController = navController,
                    state = state,
                    queueSheetState = queueSheetState,
                    currentSongLiked = currentSongLiked,
                    onSliderValueChange = onSliderValueChange,
                    onSliderValueChangeFinished = onSliderValueChangeFinished,
                )
            }

            Spacer(Modifier.height(48.dp))
        }

        Queue(
            state = queueSheetState,
            playerBottomSheetState = state,
            navController = navController,
        )
    }
}

@Composable
private fun PlayerControlsContent(
    mediaMetadata: MediaMetadata,
    sliderStyle: SliderStyle,
    playbackState: Int,
    isPlaying: Boolean,
    isLoading: Boolean,
    repeatMode: Int,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    textBackgroundColor: Color,
    icBackgroundColor: Color,
    sliderPosition: Long?,
    position: Long,
    duration: Long,
    playerConnection: PlayerConnection,
    navController: NavController,
    state: BottomSheetState,
    queueSheetState: BottomSheetState,
    currentSongLiked: Boolean,
    onSliderValueChange: (Long) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val shuffleModeEnabled by playerConnection.shuffleModeEnabled.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val smoothAnim = spring<Dp>(dampingRatio = 0.85f, stiffness = 150f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                PlayerTitleSection(mediaMetadata, textBackgroundColor, navController, state)
            }
            Spacer(Modifier.width(16.dp))

            // Like Button
            androidx.compose.material3.IconButton(
                onClick = { playerConnection.toggleLike() },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    painterResource(if (currentSongLiked) R.drawable.favorite else R.drawable.favorite_border),
                    null,
                    tint = if (currentSongLiked) MaterialTheme.colorScheme.error else textBackgroundColor,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Queue Button
            androidx.compose.material3.IconButton(
                onClick = {
                    coroutineScope.launch { queueSheetState.expandSoft() }
                },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    painterResource(R.drawable.queue_music),
                    null,
                    tint = textBackgroundColor,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        PlayerSlider(
            sliderStyle = sliderStyle,
            sliderPosition = sliderPosition,
            position = position,
            duration = duration,
            isPlaying = isPlaying,
            textButtonColor = textBackgroundColor,
            onValueChange = onSliderValueChange,
            onValueChangeFinished = onSliderValueChangeFinished,
        )

        Spacer(Modifier.height(4.dp))

        PlayerTimeLabel(
            sliderPosition = sliderPosition,
            position = position,
            duration = duration,
            textBackgroundColor = textBackgroundColor
        )

        Spacer(Modifier.height(16.dp))

        // Transport Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.IconButton(onClick = { playerConnection.toggleShuffle() }) {
                Icon(
                    painterResource(if (shuffleModeEnabled) R.drawable.shuffle_on else R.drawable.shuffle),
                    null,
                    tint = if (shuffleModeEnabled) MaterialTheme.colorScheme.primary else textBackgroundColor.copy(alpha = 0.7f)
                )
            }

            FilledTonalIconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    playerConnection.seekToPrevious()
                },
                enabled = canSkipPrevious,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = textBackgroundColor.copy(alpha = 0.1f),
                    contentColor = textBackgroundColor
                ),
                modifier = Modifier.size(56.dp).clip(CircleShape)
            ) {
                Icon(painterResource(R.drawable.skip_previous), null, modifier = Modifier.size(32.dp))
            }

            FilledIconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (playbackState == STATE_ENDED) {
                        playerConnection.player.seekTo(0, 0)
                        playerConnection.player.playWhenReady = true
                    } else {
                        playerConnection.player.togglePlayPause()
                    }
                },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = textBackgroundColor,
                    contentColor = icBackgroundColor
                ),
                modifier = Modifier.size(72.dp).clip(CircleShape)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(42.dp), color = icBackgroundColor)
                } else {
                    Icon(
                        painterResource(when {
                            playbackState == STATE_ENDED -> R.drawable.replay
                            isPlaying -> R.drawable.pause
                            else -> R.drawable.play
                        }),
                        null,
                        modifier = Modifier.size(42.dp)
                    )
                }
            }

            FilledTonalIconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    playerConnection.seekToNext()
                },
                enabled = canSkipNext,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = textBackgroundColor.copy(alpha = 0.1f),
                    contentColor = textBackgroundColor
                ),
                modifier = Modifier.size(56.dp).clip(CircleShape)
            ) {
                Icon(painterResource(R.drawable.skip_next), null, modifier = Modifier.size(32.dp))
            }

            androidx.compose.material3.IconButton(onClick = { playerConnection.toggleRepeatMode() }) {
                val repeatIcon = when (repeatMode) {
                    Player.REPEAT_MODE_ONE -> R.drawable.repeat_one_on
                    Player.REPEAT_MODE_ALL -> R.drawable.repeat_on
                    else -> R.drawable.repeat
                }
                val tint = if (repeatMode == Player.REPEAT_MODE_OFF) textBackgroundColor.copy(alpha = 0.7f) else MaterialTheme.colorScheme.primary
                Icon(painterResource(repeatIcon), null, tint = tint)
            }
        }
    }
}

@Immutable
class PlayerTitleActions(
    val onTitleClick: () -> Unit,
    val onArtistClick: (artistId: String) -> Unit,
    val onCopyTitle: () -> Unit,
    val onCopyArtists: () -> Unit,
)

@Composable
fun rememberPlayerTitleActions(
    mediaMetadata: MediaMetadata,
    navController: NavController,
    state: BottomSheetState,
): PlayerTitleActions {
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val artistLine = remember(mediaMetadata.artists) { mediaMetadata.artists.joinToString(", ") { it.name } }
    val coroutineScope = rememberCoroutineScope()
    val smoothAnim = spring<Dp>(dampingRatio = 0.85f, stiffness = 150f)

    return remember(mediaMetadata, navController, state, artistLine) {
        PlayerTitleActions(
            onTitleClick = {
                mediaMetadata.album?.let { album ->
                    coroutineScope.launch { state.collapse(smoothAnim) }
                    navController.navigate("album/${album.id}")
                }
            },
            onArtistClick = { artistId ->
                if (artistId.isNotBlank()) {
                    coroutineScope.launch { state.collapse(smoothAnim) }
                    navController.navigate("artist/$artistId")
                }
            },
            onCopyTitle = {
                clipboardManager.setPrimaryClip(ClipData.newPlainText("Copied Title", mediaMetadata.title))
                Toast.makeText(context, "Copied Title", Toast.LENGTH_SHORT).show()
            },
            onCopyArtists = {
                clipboardManager.setPrimaryClip(ClipData.newPlainText("Copied Artist", artistLine))
                Toast.makeText(context, "Copied Artist", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerTitleSection(
    mediaMetadata: MediaMetadata,
    textBackgroundColor: Color,
    navController: NavController,
    state: BottomSheetState,
) {
    val actions = rememberPlayerTitleActions(mediaMetadata = mediaMetadata, navController = navController, state = state)
    AnimatedContent(targetState = mediaMetadata.title, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "") { title ->
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = textBackgroundColor,
            modifier = Modifier.basicMarquee().combinedClickable(
                enabled = true,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = actions.onTitleClick,
                onLongClick = actions.onCopyTitle,
            ),
        )
    }
    Spacer(Modifier.height(6.dp))
    ClickableArtists(
        artists = mediaMetadata.artists,
        onArtistClick = actions.onArtistClick,
        style = MaterialTheme.typography.titleMedium.copy(color = textBackgroundColor, fontSize = 16.sp),
        onLongClick = actions.onCopyArtists,
        modifier = Modifier.fillMaxWidth().basicMarquee().padding(end = 12.dp),
    )
}

@Composable
fun ClickableArtists(
    artists: List<MediaMetadata.Artist>,
    onArtistClick: (artistId: String) -> Unit,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val annotatedString = remember(artists) {
        buildAnnotatedString {
            artists.forEachIndexed { index, artist ->
                pushStringAnnotation(tag = "artist", annotation = artist.id.orEmpty())
                append(artist.name)
                pop()
                if (index != artists.lastIndex) append(", ")
            }
        }
    }
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = annotatedString,
        style = style,
        color = color,
        textAlign = textAlign,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { layoutResult = it },
        modifier = modifier.pointerInput(annotatedString) {
            detectTapGestures(
                onTap = { offset ->
                    val layout = layoutResult ?: return@detectTapGestures
                    val position = layout.getOffsetForPosition(offset)
                    annotatedString.getStringAnnotations("artist", position, position).firstOrNull()?.let { onArtistClick(it.item) }
                },
                onLongPress = onLongClick?.let { handler -> { handler() } },
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSlider(
    sliderStyle: SliderStyle,
    sliderPosition: Long?,
    position: Long,
    duration: Long,
    isPlaying: Boolean,
    textButtonColor: Color,
    onValueChange: (Long) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    val safeDuration = if (duration <= 0L) 0f else duration.toFloat()
    val safeValue = (sliderPosition ?: position).toFloat().coerceIn(0f, maxOf(0f, safeDuration))
    val valueRange = 0f..maxOf(1f, safeDuration)

    when (sliderStyle) {
        SliderStyle.SLIM -> {
            Slider(
                value = safeValue,
                valueRange = valueRange,
                onValueChange = { onValueChange(it.toLong()) },
                onValueChangeFinished = onValueChangeFinished,
                colors = SliderDefaults.colors(
                    activeTrackColor = textButtonColor,
                    inactiveTrackColor = textButtonColor.copy(alpha = 0.2f),
                ),
                thumb = { Spacer(modifier = Modifier.size(0.dp)) },
                track = { sliderState ->
                    PlayerSliderTrack(sliderState = sliderState, colors = SliderDefaults.colors(activeTrackColor = textButtonColor, inactiveTrackColor = textButtonColor.copy(alpha = 0.2f)))
                },
                modifier = Modifier.padding(horizontal = PlayerHorizontalPadding)
            )
        }
        SliderStyle.SQUIGGLY -> {
            SquigglySlider(
                value = safeValue,
                valueRange = valueRange,
                onValueChange = { onValueChange(it.toLong()) },
                onValueChangeFinished = onValueChangeFinished,
                colors = SliderDefaults.colors(activeTrackColor = textButtonColor, inactiveTrackColor = textButtonColor.copy(alpha = 0.2f), thumbColor = textButtonColor),
                modifier = Modifier.padding(horizontal = PlayerHorizontalPadding),
                squigglesSpec = SquigglySlider.SquigglesSpec(amplitude = if (isPlaying) 2.dp else 0.dp, strokeWidth = 6.dp)
            )
        }
        else -> {
            Slider(
                value = safeValue,
                valueRange = valueRange,
                onValueChange = { onValueChange(it.toLong()) },
                onValueChangeFinished = onValueChangeFinished,
                colors = SliderDefaults.colors(activeTrackColor = textButtonColor, inactiveTrackColor = textButtonColor.copy(alpha = 0.2f), thumbColor = textButtonColor),
                modifier = Modifier.padding(horizontal = PlayerHorizontalPadding)
            )
        }
    }
}

@Composable
fun PlayerTimeLabel(
    sliderPosition: Long?,
    position: Long,
    duration: Long,
    textBackgroundColor: Color,
) {
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = PlayerHorizontalPadding + 4.dp)) {
        Text(
            text = makeTimeString(sliderPosition ?: position),
            style = MaterialTheme.typography.labelMedium,
            color = textBackgroundColor.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        Text(
            text = if (duration != C.TIME_UNSET) makeTimeString(duration) else "",
            style = MaterialTheme.typography.labelMedium,
            color = textBackgroundColor.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}
