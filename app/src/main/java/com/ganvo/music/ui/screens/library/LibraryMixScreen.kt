package com.ganvo.music.ui.screens.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.ganvo.music.LocalPlayerAwareWindowInsets
import com.ganvo.music.LocalPlayerConnection
import com.ganvo.music.R
import com.ganvo.music.constants.*
import com.ganvo.music.db.entities.*
import com.ganvo.music.extensions.*
import com.ganvo.music.models.toMediaMetadata
import com.ganvo.music.ui.component.*
import com.ganvo.music.ui.menu.*
import com.ganvo.music.utils.*
import com.ganvo.music.viewmodels.LibraryMixViewModel
import java.text.Collator
import java.time.LocalDateTime
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryMixScreen(
    navController: NavController,
    filterContent: @Composable () -> Unit,
    viewModel: LibraryMixViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    var viewType by rememberEnumPreference(AlbumViewTypeKey, LibraryViewType.GRID)
    val (sortType, onSortTypeChange) = rememberEnumPreference(MixSortTypeKey, MixSortType.CREATE_DATE)
    val (sortDescending, onSortDescendingChange) = rememberPreference(MixSortDescendingKey, true)
    val gridItemSize by rememberEnumPreference(GridItemsSizeKey, GridItemSize.BIG)

    val topSize by viewModel.topValue.collectAsState(initial = "50")
    
    val albums by viewModel.albums.collectAsState()
    val artists by viewModel.artists.collectAsState()
    val playlists by viewModel.playlists.collectAsState()

    val allItems = remember(albums, artists, playlists, sortType, sortDescending) {
        val list = (albums + artists + playlists)
        val collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.PRIMARY }
        
        when (sortType) {
            MixSortType.CREATE_DATE -> list.sortedBy { item ->
                when (item) {
                    is Album -> item.album.bookmarkedAt
                    is Artist -> item.artist.bookmarkedAt
                    is Playlist -> item.playlist.createdAt
                    else -> LocalDateTime.now()
                }
            }
            MixSortType.NAME -> list.sortedWith(compareBy(collator) { item ->
                when (item) {
                    is Album -> item.album.title
                    is Artist -> item.artist.name
                    is Playlist -> item.playlist.name
                    else -> ""
                }
            })
            MixSortType.LAST_UPDATED -> list.sortedBy { item ->
                when (item) {
                    is Album -> item.album.lastUpdateTime
                    is Artist -> item.artist.lastUpdateTime
                    is Playlist -> item.playlist.lastUpdateTime
                    else -> LocalDateTime.now()
                }
            }
        }.reversed(sortDescending)
    }

    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    val lazyGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop = backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsState()

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            if (viewType == LibraryViewType.LIST) lazyListState.animateScrollToItem(0)
            else lazyGridState.animateScrollToItem(0)
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    val headerContent = @Composable {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp)) {
            SortHeader(
                sortType = sortType, sortDescending = sortDescending,
                onSortTypeChange = onSortTypeChange, onSortDescendingChange = onSortDescendingChange,
                sortTypeText = { type ->
                    when (type) {
                        MixSortType.CREATE_DATE -> R.string.sort_by_create_date
                        MixSortType.LAST_UPDATED -> R.string.sort_by_last_updated
                        MixSortType.NAME -> R.string.sort_by_name
                    }
                },
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { viewType = viewType.toggle() }) {
                Icon(painterResource(if (viewType == LibraryViewType.LIST) R.drawable.list else R.drawable.grid_view), null)
            }
        }
    }

    val autoPlaylistsContent = @Composable {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                AutoPlaylistCard(
                    title = stringResource(R.string.liked),
                    icon = R.drawable.favorite,
                    color = MaterialTheme.colorScheme.errorContainer,
                    onColor = MaterialTheme.colorScheme.onErrorContainer,
                    onClick = { navController.navigate("auto_playlist/liked") }
                )
            }
            item {
                AutoPlaylistCard(
                    title = stringResource(R.string.offline),
                    icon = R.drawable.offline,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    onColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = { navController.navigate("auto_playlist/downloaded") }
                )
            }
            item {
                AutoPlaylistCard(
                    title = "${stringResource(R.string.my_top)} $topSize",
                    icon = R.drawable.trending_up,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    onColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = { navController.navigate("top_playlist/$topSize") }
                )
            }
            item {
                AutoPlaylistCard(
                    title = stringResource(R.string.cached_playlist),
                    icon = R.drawable.cached,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    onColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    onClick = { navController.navigate("cache_playlist/cached") }
                )
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (viewType) {
            LibraryViewType.LIST ->
                LazyColumn(state = lazyListState, contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()) {
                    item { filterContent() }
                    item { autoPlaylistsContent() }
                    item { headerContent() }
                    
                    items(items = allItems, key = { it.id }) { item ->
                        when (item) {
                            is Playlist -> PlaylistListItem(playlist = item, trailingContent = { IconButton(onClick = { menuState.show { PlaylistMenu(playlist = item, coroutineScope = coroutineScope, onDismiss = menuState::dismiss) } }) { Icon(painterResource(R.drawable.more_vert), null) } }, modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { navController.navigate("local_playlist/${item.id}") }, onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menuState.show { PlaylistMenu(playlist = item, coroutineScope = coroutineScope, onDismiss = menuState::dismiss) } }).animateItem())
                            is Artist -> ArtistListItem(artist = item, trailingContent = { IconButton(onClick = { menuState.show { ArtistMenu(originalArtist = item, coroutineScope = coroutineScope, onDismiss = menuState::dismiss) } }) { Icon(painterResource(R.drawable.more_vert), null) } }, modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { navController.navigate("artist/${item.id}") }, onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menuState.show { ArtistMenu(originalArtist = item, coroutineScope = coroutineScope, onDismiss = menuState::dismiss) } }).animateItem())
                            is Album -> AlbumListItem(album = item, isActive = item.id == mediaMetadata?.album?.id, isPlaying = isPlaying, trailingContent = { IconButton(onClick = { menuState.show { AlbumMenu(originalAlbum = item, navController = navController, onDismiss = menuState::dismiss) } }) { Icon(painterResource(R.drawable.more_vert), null) } }, modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { navController.navigate("album/${item.id}") }, onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menuState.show { AlbumMenu(originalAlbum = item, navController = navController, onDismiss = menuState::dismiss) } }).animateItem())
                            is Song -> SongListItem(song = item, isActive = item.id == mediaMetadata?.id, isPlaying = isPlaying, trailingContent = { IconButton(onClick = { menuState.show { SongMenu(originalSong = item, navController = navController, onDismiss = menuState::dismiss) } }) { Icon(painterResource(R.drawable.more_vert), null) } }, modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { if (item.id == mediaMetadata?.id) playerConnection.player.togglePlayPause() else playerConnection.playQueue(com.ganvo.music.playback.queues.YouTubeQueue.radio(item.toMediaMetadata())) }, onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menuState.show { SongMenu(originalSong = item, navController = navController, onDismiss = menuState::dismiss) } }).animateItem())
                            else -> {}
                        }
                    }
                }
            LibraryViewType.GRID ->
                LazyVerticalGrid(state = lazyGridState, columns = GridCells.Adaptive(minSize = GridThumbnailHeight + if (gridItemSize == GridItemSize.BIG) 24.dp else (-24).dp), contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { filterContent() }
                    item(span = { GridItemSpan(maxLineSpan) }) { autoPlaylistsContent() }
                    item(span = { GridItemSpan(maxLineSpan) }) { headerContent() }
                    
                    items(items = allItems, key = { it.id }) { item ->
                        when (item) {
                            is Playlist -> PlaylistGridItem(playlist = item, fillMaxWidth = true, modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { navController.navigate("local_playlist/${item.id}") }, onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menuState.show { PlaylistMenu(playlist = item, coroutineScope = coroutineScope, onDismiss = menuState::dismiss) } }).animateItem(), context = LocalContext.current)
                            is Artist -> ArtistGridItem(artist = item, fillMaxWidth = true, modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { navController.navigate("artist/${item.id}") }, onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menuState.show { ArtistMenu(originalArtist = item, coroutineScope = coroutineScope, onDismiss = menuState::dismiss) } }).animateItem())
                            is Album -> AlbumGridItem(album = item, isActive = item.id == mediaMetadata?.album?.id, isPlaying = isPlaying, coroutineScope = coroutineScope, fillMaxWidth = true, modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { navController.navigate("album/${item.id}") }, onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menuState.show { AlbumMenu(originalAlbum = item, navController = navController, onDismiss = menuState::dismiss) } }).animateItem())
                            is Song -> SongGridItem(song = item, isActive = item.id == mediaMetadata?.id, isPlaying = isPlaying, fillMaxWidth = true, modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { if (item.id == mediaMetadata?.id) playerConnection.player.togglePlayPause() else playerConnection.playQueue(com.ganvo.music.playback.queues.YouTubeQueue.radio(item.toMediaMetadata())) }, onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menuState.show { SongMenu(originalSong = item, navController = navController, onDismiss = menuState::dismiss) } }).animateItem())
                            else -> {}
                        }
                    }
                }
        }
    }
}

@Composable
fun AutoPlaylistCard(
    title: String,
    icon: Int,
    color: androidx.compose.ui.graphics.Color,
    onColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(140.dp)
            .height(80.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(id = icon),
                contentDescription = null,
                tint = onColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = onColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}