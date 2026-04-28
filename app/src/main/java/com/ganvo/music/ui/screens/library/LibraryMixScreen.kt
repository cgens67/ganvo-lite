package com.ganvo.music.ui.screens.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
    
    val likedPlaylist = remember { Playlist(PlaylistEntity(id = "auto_liked", name = "Me gusta"), 0, emptyList()) }
    val downloadPlaylist = remember { Playlist(PlaylistEntity(id = "auto_downloaded", name = "Descargado"), 0, emptyList()) }
    val topPlaylist = remember { Playlist(PlaylistEntity(id = "auto_top", name = "Mi Top"), 0, emptyList()) }
    val cachePlaylist = remember { Playlist(PlaylistEntity(id = "auto_cache", name = "En caché"), 0, emptyList()) }

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
    val lazyGridState = rememberLazyGridState()
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

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (viewType) {
            LibraryViewType.LIST ->
                LazyColumn(state = lazyListState, contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()) {
                    item { filterContent() }
                    item { headerContent() }
                    item { PlaylistListItem(playlist = likedPlaylist, autoPlaylist = true, modifier = Modifier.fillMaxWidth().clickable { navController.navigate("auto_playlist/liked") }.animateItem()) }
                    item { PlaylistListItem(playlist = downloadPlaylist, autoPlaylist = true, modifier = Modifier.fillMaxWidth().clickable { navController.navigate("auto_playlist/downloaded") }.animateItem()) }
                    item { PlaylistListItem(playlist = topPlaylist.copy(playlist = topPlaylist.playlist.copy(name = "${stringResource(R.string.my_top)} $topSize")), autoPlaylist = true, modifier = Modifier.fillMaxWidth().clickable { navController.navigate("top_playlist/$topSize") }.animateItem()) }
                    item { PlaylistListItem(playlist = cachePlaylist, autoPlaylist = true, modifier = Modifier.fillMaxWidth().clickable { navController.navigate("cache_playlist/cached") }.animateItem()) }
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
                    item(span = { GridItemSpan(maxLineSpan) }) { headerContent() }
                    item { PlaylistGridItem(playlist = likedPlaylist, fillMaxWidth = true, autoPlaylist = true, modifier = Modifier.fillMaxWidth().clickable { navController.navigate("auto_playlist/liked") }.animateItem(), context = LocalContext.current) }
                    item { PlaylistGridItem(playlist = downloadPlaylist, fillMaxWidth = true, autoPlaylist = true, modifier = Modifier.fillMaxWidth().clickable { navController.navigate("auto_playlist/downloaded") }.animateItem(), context = LocalContext.current) }
                    item { PlaylistGridItem(playlist = topPlaylist.copy(playlist = topPlaylist.playlist.copy(name = "${stringResource(R.string.my_top)} $topSize")), fillMaxWidth = true, autoPlaylist = true, modifier = Modifier.fillMaxWidth().clickable { navController.navigate("top_playlist/$topSize") }.animateItem(), context = LocalContext.current) }
                    item { PlaylistGridItem(playlist = cachePlaylist, fillMaxWidth = true, autoPlaylist = true, modifier = Modifier.fillMaxWidth().clickable { navController.navigate("cache_playlist/cached") }.animateItem(), context = LocalContext.current) }
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