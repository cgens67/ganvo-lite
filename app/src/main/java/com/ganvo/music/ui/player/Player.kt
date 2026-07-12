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
import androidx.compose.material3.CircularWavyProgressIndicator
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
            MiniPlayer(position = position, duration = duration)
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
                    onClick = { state.collapseSoft() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.expand_more),
                        contentDescription = "Collapse",
                        tint = textBackgroundColor,
                        modifier = Modifier.size(32.dp)
                    )
                }

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
                modifier = Modifier.weight(1f).padding(horizontal = 32.dp)
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
    currentSongLiked: Boolean,
    onSliderValueChange: (Long) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val shuffleModeEnabled by playerConnection.shuffleModeEnabled.collectAsState()

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = PlayerHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            PlayerTitleSection(mediaMetadata, textBackgroundColor, navController, state)
        }
        Spacer(Modifier.width(12.dp))

        // V4 specific top actions
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                onClick = { /* Implement Share Intent if needed */ },
                shape = RoundedCornerShape(14.dp),
                color = textBackgroundColor.copy(alpha = 0.12f),
                modifier = Modifier.height(44.dp).width(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(painterResource(R.drawable.share), null, tint = textBackgroundColor, modifier = Modifier.size(22.dp))
                }
            }

            Surface(
                onClick = { playerConnection.toggleLike() },
                shape = RoundedCornerShape(14.dp),
                color = if (currentSongLiked) MaterialTheme.colorScheme.error.copy(alpha = 0.25f) else textBackgroundColor.copy(alpha = 0.12f),
                modifier = Modifier.height(44.dp).width(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        painter = painterResource(if (currentSongLiked) R.drawable.favorite else R.drawable.favorite_border),
                        contentDescription = null,
                        tint = if (currentSongLiked) MaterialTheme.colorScheme.error else textBackgroundColor,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(12.dp))

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

    Spacer(Modifier.height(12.dp))

    // V4 Transport Controls
    val playPauseCorner by animateDpAsState(
        targetValue = if (isPlaying) 28.dp else 44.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "cinematicPlayPauseCorner",
    )

    BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(horizontal = PlayerHorizontalPadding)) {
        val maxW = maxWidth
        val playButtonHeight = maxW / 6f
        val playButtonWidth = playButtonHeight * 1.6f
        val sideButtonHeight = playButtonHeight * 0.8f
        val sideButtonWidth = sideButtonHeight * 1.3f

        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            FilledTonalIconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    playerConnection.seekToPrevious()
                },
                enabled = canSkipPrevious,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = textBackgroundColor.copy(alpha = 0.15f),
                    contentColor = textBackgroundColor,
                ),
                modifier = Modifier.size(width = sideButtonWidth, height = sideButtonHeight).clip(RoundedCornerShape(32.dp)),
            ) {
                Icon(painterResource(R.drawable.skip_previous), null, modifier = Modifier.size(32.dp))
            }

            Spacer(modifier = Modifier.width(16.dp))

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
                    contentColor = icBackgroundColor,
                ),
                modifier = Modifier.size(width = playButtonWidth, height = playButtonHeight).clip(RoundedCornerShape(playPauseCorner)),
            ) {
                if (isLoading) {
                    CircularWavyProgressIndicator(modifier = Modifier.size(42.dp), color = icBackgroundColor)
                } else {
                    Icon(
                        painter = painterResource(when {
                            playbackState == STATE_ENDED -> R.drawable.replay
                            isPlaying -> R.drawable.pause
                            else -> R.drawable.play
                        }),
                        contentDescription = null,
                        modifier = Modifier.size(42.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            FilledTonalIconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    playerConnection.seekToNext()
                },
                enabled = canSkipNext,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = textBackgroundColor.copy(alpha = 0.15f),
                    contentColor = textBackgroundColor,
                ),
                modifier = Modifier.size(width = sideButtonWidth, height = sideButtonHeight).clip(RoundedCornerShape(32.dp)),
            ) {
                Icon(painterResource(R.drawable.skip_next), null, modifier = Modifier.size(32.dp))
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

    return remember(mediaMetadata, navController, state, artistLine) {
        PlayerTitleActions(
            onTitleClick = {
                mediaMetadata.album?.let { album ->
                    state.collapseSoft()
                    navController.navigate("album/${album.id}")
                }
            },
            onArtistClick = { artistId ->
                if (artistId.isNotBlank()) {
                    state.collapseSoft()
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
                pushStringAnnotation(tag = "artist_${artist.id.orEmpty()}", annotation = artist.id.orEmpty())
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
                    annotatedString.getStringAnnotations(position, position).firstOrNull()?.let { onArtistClick(it.item) }
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
