@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
package com.ganvo.music.ui.player

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.drawable.BitmapDrawable
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.DialogProperties
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.navigation.NavController
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ganvo.music.LocalPlayerConnection
import com.ganvo.music.R
import com.ganvo.music.constants.DarkModeKey
import com.ganvo.music.constants.ListItemHeight
import com.ganvo.music.constants.PlayerBackgroundStyle
import com.ganvo.music.constants.PlayerBackgroundStyleKey
import com.ganvo.music.constants.PureBlackKey
import com.ganvo.music.constants.QueueEditLockKey
import com.ganvo.music.extensions.metadata
import com.ganvo.music.extensions.move
import com.ganvo.music.extensions.togglePlayPause
import com.ganvo.music.extensions.toggleRepeatMode
import com.ganvo.music.models.MediaMetadata
import com.ganvo.music.ui.component.BottomSheet
import com.ganvo.music.ui.component.BottomSheetState
import com.ganvo.music.ui.component.LocalMenuState
import com.ganvo.music.ui.component.MediaMetadataListItem
import com.ganvo.music.ui.menu.PlayerMenu
import com.ganvo.music.ui.menu.SelectionMediaMetadataMenu
import com.ganvo.music.ui.screens.settings.DarkMode
import com.ganvo.music.ui.theme.extractGradientColors
import com.ganvo.music.utils.makeTimeString
import com.ganvo.music.utils.rememberEnumPreference
import com.ganvo.music.utils.rememberPreference
import kotlinx.coroutines.*
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorder
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable

data class WindowWrapper(
    val window: Timeline.Window,
    val key: String
)

@SuppressLint("UnrememberedMutableState", "StringFormatInvalid")
@Composable
fun Queue(
    state: BottomSheetState,
    playerBottomSheetState: BottomSheetState,
    navController: NavController,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    onBackgroundColor: Color = MaterialTheme.colorScheme.onSurface,
    textBackgroundColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val clipboardManager = LocalClipboardManager.current
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return

    val isPlaying by playerConnection.isPlaying.collectAsState()
    val repeatMode by playerConnection.repeatMode.collectAsState()
    val currentWindowIndex by playerConnection.currentWindowIndex.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val currentFormat by playerConnection.currentFormat.collectAsState(initial = null)
    val queueWindows by playerConnection.queueWindows.collectAsState()
    val automix by playerConnection.service.automixItems.collectAsState()
    val currentSong by playerConnection.currentSong.collectAsState(initial = null)

    val selectedSongs = remember { mutableStateListOf<MediaMetadata>() }
    val selectedItems = remember { mutableStateListOf<Timeline.Window>() }
    var selection by remember { mutableStateOf(false) }
    var showDetailsDialog by rememberSaveable { mutableStateOf(false) }
    var locked by rememberPreference(QueueEditLockKey, true)
    val (enableHapticFeedback) = rememberPreference(booleanPreferencesKey("enable_haptic_feedback"), true)
    val snackbarHostState = remember { SnackbarHostState() }
    var dismissJob: Job? by remember { mutableStateOf(null) }
    val coroutineScope = rememberCoroutineScope()

    // Player Background preferences
    val playerBackground by rememberEnumPreference(
        key = PlayerBackgroundStyleKey,
        defaultValue = PlayerBackgroundStyle.DEFAULT
    )
    val disableBlur by rememberPreference(booleanPreferencesKey("disable_blur"), false)
    val pureBlack by rememberPreference(PureBlackKey, false)
    val isSystemInDarkTheme = isSystemInDarkTheme()
    val darkTheme by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
    val useDarkTheme = remember(darkTheme, isSystemInDarkTheme) {
        if (darkTheme == DarkMode.AUTO) isSystemInDarkTheme else darkTheme == DarkMode.ON
    }

    val isCustomBackground = playerBackground != PlayerBackgroundStyle.DEFAULT

    var gradientColors by remember { mutableStateOf<List<Color>>(emptyList()) }

    LaunchedEffect(mediaMetadata, playerBackground) {
        if (playerBackground == PlayerBackgroundStyle.GRADIENT) {
            withContext(Dispatchers.IO) {
                val result = runCatching {
                    ImageLoader(context).execute(
                        ImageRequest.Builder(context)
                            .data(mediaMetadata?.thumbnailUrl)
                            .allowHardware(false)
                            .build()
                    ).drawable as? BitmapDrawable
                }.getOrNull()

                result?.bitmap?.let { bitmap ->
                    val extracted = bitmap.extractGradientColors()
                    withContext(Dispatchers.Main) {
                        gradientColors = extracted
                    }
                }
            }
        } else {
            gradientColors = emptyList()
        }
    }

    val sheetBgColor = when {
        isCustomBackground -> Color.Transparent
        useDarkTheme && pureBlack -> Color.Black
        else -> backgroundColor
    }

    val effectiveOnBgColor = when {
        isCustomBackground -> Color.White
        sheetBgColor.luminance() < 0.5f -> Color.White
        else -> MaterialTheme.colorScheme.onSurface
    }

    fun clearSelection() {
        selection = false
        selectedSongs.clear()
        selectedItems.clear()
    }

    if (selection) BackHandler { clearSelection() }

    val onRemoveMultipleWithUndo: (List<Timeline.Window>) -> Unit = { windows ->
        if (windows.isNotEmpty()) {
            val sorted = windows.sortedBy { it.firstPeriodIndex }
            var i = 0
            sorted.forEach { playerConnection.player.removeMediaItem(it.firstPeriodIndex - i++) }
            dismissJob?.cancel()
            dismissJob = coroutineScope.launch {
                val msg = if (windows.size == 1) {
                    context.getString(R.string.removed_song_from_playlist, sorted.first().mediaItem.metadata?.title ?: "")
                } else {
                    "${windows.size} songs removed"
                }
                val res = snackbarHostState.showSnackbar(
                    msg,
                    context.getString(R.string.undo),
                    duration = SnackbarDuration.Short
                )
                if (res == SnackbarResult.ActionPerformed) {
                    sorted.forEach { w ->
                        playerConnection.player.addMediaItem(w.mediaItem)
                        playerConnection.player.moveMediaItem(playerConnection.player.mediaItemCount - 1, w.firstPeriodIndex)
                    }
                }
            }
        }
    }

    if (showDetailsDialog) {
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
                Column(modifier = Modifier.sizeIn(minWidth = 280.dp, maxWidth = 560.dp).verticalScroll(rememberScrollState())) {
                    listOf(
                        stringResource(R.string.song_title) to mediaMetadata?.title,
                        stringResource(R.string.song_artists) to mediaMetadata?.artists?.joinToString { it.name },
                        stringResource(R.string.media_id) to mediaMetadata?.id,
                        stringResource(R.string.mime_type) to currentFormat?.mimeType,
                        stringResource(R.string.codecs) to currentFormat?.codecs,
                        stringResource(R.string.bitrate) to currentFormat?.bitrate?.let { "${it / 1000} Kbps" },
                        stringResource(R.string.sample_rate) to currentFormat?.sampleRate?.let { "$it Hz" },
                        stringResource(R.string.volume) to "${(playerConnection.player.volume * 100).toInt()}%"
                    ).forEach { (label, text) ->
                        val displayText = text ?: stringResource(R.string.unknown)
                        Text(label, style = MaterialTheme.typography.labelMedium)
                        Text(
                            displayText,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                clipboardManager.setText(AnnotatedString(displayText))
                                Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        )
    }

    BottomSheet(
        state = state,
        brushBackgroundColor = Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent)),
        modifier = modifier,
        collapsedContent = {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { state.expandSoft() }) {
                    Icon(
                        painter = painterResource(R.drawable.expand_less),
                        tint = effectiveOnBgColor,
                        contentDescription = null
                    )
                }
            }
        }
    ) {
        // Handle Background here natively for custom backgrounds
        Box(modifier = Modifier.fillMaxSize().background(sheetBgColor)) {
            if (isCustomBackground) {
                if (playerBackground == PlayerBackgroundStyle.BLUR) {
                    AsyncImage(
                        model = mediaMetadata?.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().blur(if (disableBlur) 0.dp else 80.dp)
                    )
                } else if (playerBackground == PlayerBackgroundStyle.GRADIENT) {
                    Box(modifier = Modifier.fillMaxSize().background(
                        if (gradientColors.size >= 2) Brush.verticalGradient(gradientColors)
                        else Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surface))
                    ))
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                )
            }
        }

        val mutableQueueWindows = remember { mutableStateListOf<WindowWrapper>() }
        val queueLength = remember(queueWindows) { queueWindows.sumOf { it.mediaItem.metadata?.duration?.toLong() ?: 0L } }
        val lazyListState = rememberLazyListState()
        val headerItems = 1

        val reorderableState = rememberReorderableLazyListState(
            listState = lazyListState,
            canDragOver = { draggedOver, _ ->
                val targetIdx = draggedOver.index - headerItems
                targetIdx in mutableQueueWindows.indices
            },
            onMove = { from, to ->
                val fromIdx = from.index - headerItems
                val toIdx = to.index - headerItems
                if (fromIdx in mutableQueueWindows.indices && toIdx in mutableQueueWindows.indices) {
                    val item = mutableQueueWindows.removeAt(fromIdx)
                    mutableQueueWindows.add(toIdx, item)
                }
            },
            onDragEnd = { fromIndex, toIndex ->
                val safeFrom = (fromIndex - headerItems).coerceIn(0, queueWindows.lastIndex)
                val safeTo = (toIndex - headerItems).coerceIn(0, queueWindows.lastIndex)
                if (safeFrom != safeTo) {
                    if (!playerConnection.player.shuffleModeEnabled) {
                        playerConnection.player.moveMediaItem(safeFrom, safeTo)
                    } else {
                        playerConnection.player.setShuffleOrder(
                            DefaultShuffleOrder(
                                queueWindows.map { it.firstPeriodIndex }.toMutableList().apply {
                                    add(safeTo, removeAt(safeFrom))
                                }.toIntArray(),
                                System.currentTimeMillis()
                            )
                        )
                    }
                }
            }
        )

        LaunchedEffect(queueWindows) {
            if (reorderableState.draggingItemKey == null) {
                val newWindows = mutableListOf<WindowWrapper>()
                val usedKeys = mutableSetOf<String>()
                queueWindows.forEach { window ->
                    var key = window.uid?.toString() ?: "uid_null"
                    var count = 0
                    while (usedKeys.contains(key)) {
                        count++
                        key = "${window.uid}_$count"
                    }
                    usedKeys.add(key)
                    newWindows.add(WindowWrapper(window, key))
                }
                mutableQueueWindows.clear()
                mutableQueueWindows.addAll(newWindows)
            }
        }

        LaunchedEffect(state.isCollapsed, currentWindowIndex) {
            if (!state.isCollapsed && currentWindowIndex != -1) {
                if (currentWindowIndex in mutableQueueWindows.indices) {
                    try { lazyListState.scrollToItem(currentWindowIndex + headerItems) } catch (e: Exception) {}
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                CurrentSongHeader(
                    mediaMetadata = mediaMetadata,
                    liked = currentSong?.song?.liked == true,
                    repeatMode = repeatMode,
                    shuffleModeEnabled = playerConnection.player.shuffleModeEnabled,
                    locked = locked,
                    songCount = queueWindows.size,
                    queueDuration = queueLength,
                    backgroundColor = Color.Transparent,
                    onBackgroundColor = effectiveOnBgColor,
                    onToggleLike = { playerConnection.service.toggleLike() },
                    onMenuClick = {
                        mediaMetadata?.let { meta ->
                            menuState.show {
                                PlayerMenu(
                                    mediaMetadata = meta,
                                    navController = navController,
                                    playerBottomSheetState = playerBottomSheetState,
                                    isQueueTrigger = true,
                                    onShowDetailsDialog = { showDetailsDialog = true },
                                    onDismiss = { menuState.dismiss() }
                                )
                            }
                        }
                    },
                    onClearQueueClick = {
                        val w = if (currentWindowIndex in queueWindows.indices) {
                            queueWindows.filterIndexed { i, _ -> i != currentWindowIndex }
                        } else emptyList()
                        onRemoveMultipleWithUndo(w)
                        clearSelection()
                    },
                    onRepeatClick = { playerConnection.player.toggleRepeatMode() },
                    onShuffleClick = { playerConnection.player.shuffleModeEnabled = !playerConnection.player.shuffleModeEnabled },
                    onLockClick = { locked = !locked }
                )

                CompositionLocalProvider(LocalContentColor provides effectiveOnBgColor) {
                    LazyColumn(
                        state = lazyListState,
                        contentPadding = WindowInsets.systemBars.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
                            .add(WindowInsets(bottom = ListItemHeight + if (selection) 88.dp else 8.dp))
                            .asPaddingValues(),
                        modifier = Modifier
                            .weight(1f)
                            .nestedScroll(state.preUpPostDownNestedScrollConnection)
                            .reorderable(reorderableState)
                    ) {
                        item { Spacer(modifier = Modifier.animateContentSize().height(if (selection) 48.dp else 0.dp)) }

                        itemsIndexed(items = mutableQueueWindows, key = { _, wrapper -> wrapper.key }) { index, wrapper ->
                            val metadata = wrapper.window.mediaItem.metadata
                            if (metadata != null) {
                                ReorderableItem(reorderableState = reorderableState, key = wrapper.key) { isDragging ->
                                    val currentItem by rememberUpdatedState(wrapper.window)
                                    val isActive = index == currentWindowIndex
                                    val dismissBoxState = rememberSwipeToDismissBoxState(positionalThreshold = { it }, confirmValueChange = {
                                        if (it == SwipeToDismissBoxValue.StartToEnd || it == SwipeToDismissBoxValue.EndToStart) {
                                            onRemoveMultipleWithUndo(listOf(currentItem))
                                        }
                                        true
                                    })

                                    val content: @Composable () -> Unit = {
                                        Row(
                                            horizontalArrangement = Arrangement.Center,
                                            modifier = Modifier.graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                                        ) {
                                            MediaMetadataListItem(
                                                mediaMetadata = metadata,
                                                isSelected = selection && metadata in selectedSongs,
                                                isActive = isActive,
                                                isPlaying = isPlaying && isActive,
                                                trailingContent = {
                                                    IconButton(onClick = {
                                                        menuState.show {
                                                            SelectionMediaMetadataMenu(
                                                                songSelection = selectedSongs,
                                                                onDismiss = { menuState.dismiss() },
                                                                clearAction = { clearSelection() },
                                                                currentItems = selectedItems
                                                            )
                                                        }
                                                    }) {
                                                        Icon(painterResource(R.drawable.more_vert), null, tint = effectiveOnBgColor)
                                                    }
                                                    if (!locked) {
                                                        IconButton(
                                                            onClick = {},
                                                            modifier = Modifier
                                                                .detectReorder(reorderableState)
                                                                .graphicsLayer { alpha = 0.99f }
                                                        ) {
                                                            Icon(painterResource(R.drawable.drag_handle), null, tint = effectiveOnBgColor)
                                                        }
                                                    }
                                                },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(if (isDragging) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                                                    .combinedClickable(
                                                        onClick = {
                                                            if (selection) {
                                                                if (metadata in selectedSongs) {
                                                                    selectedSongs.remove(metadata)
                                                                    selectedItems.remove(currentItem)
                                                                    if (selectedSongs.isEmpty()) selection = false
                                                                } else {
                                                                    selectedSongs.add(metadata)
                                                                    selectedItems.add(currentItem)
                                                                }
                                                            } else {
                                                                if (index == currentWindowIndex) {
                                                                    playerConnection.player.togglePlayPause()
                                                                } else {
                                                                    playerConnection.player.seekToDefaultPosition(wrapper.window.firstPeriodIndex)
                                                                    playerConnection.player.playWhenReady = true
                                                                }
                                                            }
                                                        },
                                                        onLongClick = {
                                                            if (enableHapticFeedback) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            selection = true
                                                            selectedSongs.clear()
                                                            selectedItems.clear()
                                                            selectedSongs.add(metadata)
                                                            selectedItems.add(currentItem)
                                                        }
                                                    )
                                            )
                                        }
                                    }

                                    if (locked) content() else SwipeToDismissBox(state = dismissBoxState, backgroundContent = {}) { content() }
                                }
                            }
                        }

                        if (automix.isNotEmpty()) {
                            item {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                    color = effectiveOnBgColor.copy(alpha = 0.12f)
                                )
                                ItemWithGlowingIcon()
                            }

                            itemsIndexed(items = automix, key = { index, it -> "${it.mediaId}_$index" }) { index, item ->
                                val metadata = item.metadata
                                if (metadata != null) {
                                    Row(horizontalArrangement = Arrangement.Center) {
                                        MediaMetadataListItem(
                                            mediaMetadata = metadata,
                                            trailingContent = {
                                                IconButton(onClick = { playerConnection.service.playNextAutomix(item, index) }) {
                                                    Icon(painterResource(R.drawable.playlist_play), null, tint = effectiveOnBgColor)
                                                }
                                                IconButton(onClick = { playerConnection.service.addToQueueAutomix(item, index) }) {
                                                    Icon(painterResource(R.drawable.queue_music), null, tint = effectiveOnBgColor)
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .combinedClickable(
                                                    onClick = {},
                                                    onLongClick = {
                                                        menuState.show {
                                                            PlayerMenu(
                                                                mediaMetadata = metadata,
                                                                navController = navController,
                                                                playerBottomSheetState = playerBottomSheetState,
                                                                isQueueTrigger = true,
                                                                onShowDetailsDialog = { showDetailsDialog = true },
                                                                onDismiss = { menuState.dismiss() }
                                                            )
                                                        }
                                                    }
                                                )
                                                .animateItem()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .padding(bottom = (if (selection) ListItemHeight * 2 + 16.dp else ListItemHeight) + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding())
                        .align(Alignment.BottomCenter)
                )

                AnimatedVisibility(
                    visible = selection,
                    enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = ListItemHeight + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding())
                ) {
                    val validWindows = mutableQueueWindows.filter { it.window.mediaItem.metadata != null }
                    val allSelected = selectedSongs.size == validWindows.size && validWindows.isNotEmpty()

                    QueueSelectionFloatingToolbar(
                        allSelected = allSelected,
                        pureBlack = isCustomBackground || sheetBgColor == Color.Black,
                        onClose = ::clearSelection,
                        onToggleSelectAll = {
                            if (allSelected) {
                                clearSelection()
                            } else {
                                selectedSongs.clear()
                                selectedItems.clear()
                                validWindows.forEach { w ->
                                    selectedSongs.add(w.window.mediaItem.metadata!!)
                                    selectedItems.add(w.window)
                                }
                            }
                        },
                        onMenuAction = {
                            menuState.show {
                                SelectionMediaMetadataMenu(
                                    songSelection = selectedSongs,
                                    onDismiss = { menuState.dismiss() },
                                    clearAction = { clearSelection() },
                                    currentItems = selectedItems
                                )
                            }
                        },
                        onDelete = {
                            onRemoveMultipleWithUndo(selectedItems.toList())
                            clearSelection()
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueSelectionFloatingToolbar(
    allSelected: Boolean,
    pureBlack: Boolean,
    onClose: () -> Unit,
    onToggleSelectAll: () -> Unit,
    onMenuAction: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.widthIn(max = 420.dp),
        shape = RoundedCornerShape(percent = 50),
        color = if (pureBlack) Color.Black else cs.surfaceContainerHigh,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = onClose,
                shape = CircleShape,
                color = if (pureBlack) Color.White.copy(0.12f) else cs.surfaceContainerHighest,
                contentColor = if (pureBlack) Color.White else cs.onSurface,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.close), null, modifier = Modifier.size(22.dp))
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                QueueSelectionToolbarAction(
                    icon = if (allSelected) R.drawable.deselect else R.drawable.select_all,
                    desc = null,
                    onClick = onToggleSelectAll,
                    tint = if (pureBlack) Color.White else cs.onSurface
                )
                QueueSelectionToolbarAction(
                    icon = R.drawable.more_vert,
                    desc = null,
                    onClick = onMenuAction,
                    tint = cs.primary
                )
                QueueSelectionToolbarAction(
                    icon = R.drawable.delete,
                    desc = stringResource(R.string.delete),
                    onClick = onDelete,
                    tint = cs.error
                )
            }
        }
    }
}

@Composable
private fun QueueSelectionToolbarAction(
    icon: Int,
    desc: String?,
    onClick: () -> Unit,
    tint: Color
) = IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
    Icon(painterResource(icon), desc, modifier = Modifier.size(22.dp), tint = tint)
}

@Composable
fun ToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape,
    containerColor: Color,
    contentColor: Color,
    checkedContainerColor: Color,
    checkedContentColor: Color,
    content: @Composable () -> Unit
) {
    Surface(
        checked = checked,
        onCheckedChange = onCheckedChange,
        shape = shape,
        color = if (checked) checkedContainerColor else containerColor,
        contentColor = if (checked) checkedContentColor else contentColor,
        modifier = modifier
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@Composable
fun CurrentSongHeader(
    mediaMetadata: MediaMetadata?,
    liked: Boolean,
    repeatMode: Int,
    shuffleModeEnabled: Boolean,
    locked: Boolean,
    songCount: Int,
    queueDuration: Long,
    backgroundColor: Color,
    onBackgroundColor: Color,
    onToggleLike: () -> Unit,
    onMenuClick: () -> Unit,
    onClearQueueClick: () -> Unit,
    onRepeatClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onLockClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(horizontal = 16.dp)
            .padding(top = 20.dp, bottom = 8.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(2.5.dp))
                    .background(onBackgroundColor.copy(0.4f))
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AsyncImage(
                model = mediaMetadata?.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(onBackgroundColor.copy(0.06f))
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    mediaMetadata?.title ?: "",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = onBackgroundColor
                )
                Text(
                    mediaMetadata?.artists?.joinToString(", ") { it.name } ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = onBackgroundColor.copy(0.6f)
                )
            }
            androidx.compose.material3.IconButton(
                onClick = onToggleLike,
                modifier = Modifier.size(44.dp),
                colors = IconButtonDefaults.iconButtonColors(contentColor = if (liked) MaterialTheme.colorScheme.error else onBackgroundColor)
            ) {
                Icon(
                    painterResource(if (liked) R.drawable.favorite else R.drawable.favorite_border),
                    null,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(onBackgroundColor.copy(0.06f))
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.IconButton(
                    onClick = onLockClick,
                    modifier = Modifier.size(40.dp),
                    colors = IconButtonDefaults.iconButtonColors(contentColor = onBackgroundColor.copy(0.7f))
                ) {
                    Icon(painterResource(if (locked) R.drawable.lock else R.drawable.lock_open), null, modifier = Modifier.size(20.dp))
                }
                androidx.compose.material3.IconButton(
                    onClick = onMenuClick,
                    modifier = Modifier.size(40.dp),
                    colors = IconButtonDefaults.iconButtonColors(contentColor = onBackgroundColor.copy(0.7f))
                ) {
                    Icon(painterResource(R.drawable.more_vert), null, modifier = Modifier.size(20.dp))
                }
                androidx.compose.material3.IconButton(
                    onClick = onClearQueueClick,
                    modifier = Modifier.size(40.dp),
                    colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(painterResource(R.drawable.delete), null, modifier = Modifier.size(20.dp))
                }
            }
            Text(
                "${songCount} songs  •  " + makeTimeString(queueDuration * 1000L),
                style = MaterialTheme.typography.labelMedium,
                color = onBackgroundColor.copy(0.55f),
                modifier = Modifier.padding(end = 14.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val ucContainer = onBackgroundColor.copy(0.12f)
            val ucContent = onBackgroundColor
            val ccContainer = onBackgroundColor.copy(0.22f)
            val ccContent = onBackgroundColor

            ToggleButton(
                checked = shuffleModeEnabled,
                onCheckedChange = { onShuffleClick() },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp, topEnd = 4.dp, bottomEnd = 4.dp),
                containerColor = ucContainer,
                contentColor = ucContent,
                checkedContainerColor = ccContainer,
                checkedContentColor = ccContent
            ) {
                Icon(painterResource(R.drawable.shuffle), null, modifier = Modifier.size(22.dp))
            }
            ToggleButton(
                checked = repeatMode != Player.REPEAT_MODE_OFF,
                onCheckedChange = { onRepeatClick() },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 24.dp, bottomEnd = 24.dp),
                containerColor = ucContainer,
                contentColor = ucContent,
                checkedContainerColor = ccContainer,
                checkedContentColor = ccContent
            ) {
                Icon(
                    painterResource(
                        when (repeatMode) {
                            Player.REPEAT_MODE_ONE -> R.drawable.repeat_one
                            else -> R.drawable.repeat
                        }
                    ),
                    null,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text(
            "Continue playing",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = onBackgroundColor
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            "Autoplaying similar content",
            style = MaterialTheme.typography.bodySmall,
            color = onBackgroundColor.copy(0.5f)
        )
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = onBackgroundColor.copy(0.08f), thickness = 1.dp)
    }
}

@SuppressLint("SuspiciousIndentation")
@Composable
fun ItemWithGlowingIcon() {
    val infiniteTransition = rememberInfiniteTransition(label = "")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = ""
    )

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = ""
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp, 8.dp),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.4f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Box {
                Icon(
                    painter = painterResource(R.drawable.ia_icon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(30.dp)
                        .scale(scale)
                        .alpha(alpha)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = stringResource(R.string.similar_content),
                fontSize = 16.sp,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.SansSerif,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
