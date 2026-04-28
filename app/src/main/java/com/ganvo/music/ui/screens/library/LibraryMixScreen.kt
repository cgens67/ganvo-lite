package com.ganvo.music.ui.screens.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
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
import com.ganvo.music.constants.AlbumViewTypeKey
import com.ganvo.music.constants.CONTENT_TYPE_HEADER
import com.ganvo.music.constants.CONTENT_TYPE_PLAYLIST
import com.ganvo.music.constants.GridItemSize
import com.ganvo.music.constants.GridItemsSizeKey
import com.ganvo.music.constants.GridThumbnailHeight
import com.ganvo.music.constants.LibraryViewType
import com.ganvo.music.constants.MixSortDescendingKey
import com.ganvo.music.constants.MixSortType
import com.ganvo.music.constants.MixSortTypeKey
import com.ganvo.music.db.entities.Album
import com.ganvo.music.db.entities.Artist
import com.ganvo.music.db.entities.Playlist
import com.ganvo.music.db.entities.PlaylistEntity
import com.ganvo.music.extensions.reversed
import com.ganvo.music.ui.component.AlbumGridItem
import com.ganvo.music.ui.component.AlbumListItem
import com.ganvo.music.ui.component.ArtistGridItem
import com.ganvo.music.ui.component.ArtistListItem
import com.ganvo.music.ui.component.LocalMenuState
import com.ganvo.music.ui.component.PlaylistGridItem
import com.ganvo.music.ui.component.PlaylistListItem
import com.ganvo.music.ui.component.SortHeader
import com.ganvo.music.ui.menu.AlbumMenu
import com.ganvo.music.ui.menu.ArtistMenu
import com.ganvo.music.ui.menu.PlaylistMenu
import com.ganvo.music.utils.rememberEnumPreference
import com.ganvo.music.utils.rememberPreference
import com.ganvo.music.viewmodels.LibraryMixViewModel
import java.text.Collator
import java.time.LocalDateTime
import java.util.Locale
import java.util.UUID

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
    val (sortType, onSortTypeChange) = rememberEnumPreference(
        MixSortTypeKey,
        MixSortType.CREATE_DATE
    )
    val (sortDescending, onSortDescendingChange) = rememberPreference(MixSortDescendingKey, true)
    val gridItemSize by rememberEnumPreference(GridItemsSizeKey, GridItemSize.BIG)

    val topSize by viewModel.topValue.collectAsState(initial = 50)
    
    // Explicit static IDs to avoid recomposition glitches
    val likedPlaylist = remember {
        Playlist(
            playlist = PlaylistEntity(id = "auto_liked", name = "Me gusta"),
            songCount = 0,
            thumbnails = emptyList()
        )
    }

    val downloadPlaylist = remember {
        Playlist(
            playlist = PlaylistEntity(id = "auto_downloaded", name = "Descargado"),
            songCount = 0,
            thumbnails = emptyList()
        )
    }

    val topPlaylist = remember {
        Playlist(
            playlist = PlaylistEntity(id = "auto_top", name = "Mi Top"),
            songCount = 0,
            thumbnails = emptyList()
        )
    }

    val cachePlaylist = remember {
        Playlist(
            playlist = PlaylistEntity(id = "auto_cache", name = "En caché"),
            songCount = 0,
            thumbnails = emptyList()
        )
    }

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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp),
        ) {
            SortHeader(
                sortType = sortType,
                sortDescending = sortDescending,
                onSortTypeChange = onSortTypeChange,
                onSortDescendingChange = onSortDescendingChange,
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
                Icon(
                    painter = painterResource(if (viewType == LibraryViewType.LIST) R.drawable.list else R.drawable.grid_view),
                    contentDescription = null,
                )
            }
        }
    }

    // FIX: Wrapping everything in a Surface ensures background is correctly applied in Light Mode
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        when (viewType) {
            LibraryViewType.LIST ->
                LazyColumn(
                    state = lazyListState,
                    contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
                ) {
                    item(key = "filter") { filterContent() }
                    item(key = "header") { headerContent() }
                    item(key = "liked") {
                        PlaylistListItem(playlist = likedPlaylist, autoPlaylist = true, modifier = Modifier.fillMaxWidth().clickable { navController.navigate("auto_playlist/liked") }.animateItem())
                    }
                    item(key = "downloaded") {
                        PlaylistListItem(playlist = downloadPlaylist, autoPlaylist = true, modifier = Modifier.fillMaxWidth().clickable { navController.navigate("auto_playlist/downloaded") }.animateItem())
                    }
                    item(key = "top") {
                        PlaylistListItem(playlist = topPlaylist.copy(playlist = topPlaylist.playlist.copy(name = "${stringResource(R.string.my_top)} $topSize")), autoPlaylist = true, modifier = Modifier.fillMaxWidth().clickable { navController.navigate("top_playlist/$topSize") }.animateItem())
                    }
                    item(key = "cache") {
                        PlaylistListItem(playlist = cachePlaylist, autoPlaylist = true, modifier = Modifier.fillMaxWidth().clickable { navController.navigate("cache_playlist/cached") }.animateItem())
                    }
                    items(items = allItems, key = { it.id }) { item ->
                        when (item) {
                            is Playlist -> PlaylistListItem(playlist = item, trailingContent = { IconButton(onClick = { menuState.show { PlaylistMenu(playlist = item, coroutineScope = coroutineScope, onDismiss = menuState::dismiss) } }) { Icon(painterResource(R.drawable.more_vert), null) } }, modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { navController.navigate("local_playlist/${item.id}") }, onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menuState.show { PlaylistMenu(playlist = item, coroutineScope = coroutineScope, onDismiss = menuState::dismiss) } }).animateItem())
                            is Artist -> ArtistListItem(artist = item, trailingContent = { IconButton(onClick = { menuState.show { ArtistMenu(originalArtist = item, coroutineScope = coroutineScope, onDismiss = menuState::dismiss) } }) { Icon(painterResource(R.drawable.more_vert), null) } }, modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { navController.navigate("artist/${item.id}") }, onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menuState.show { ArtistMenu(originalArtist = item, coroutineScope = coroutineScope, onDismiss = menuState::dismiss) } }).animateItem())
                            is Album -> AlbumListItem(album = item, isActive = item.id == mediaMetadata?.album?.id, isPlaying = isPlaying, trailingContent = { IconButton(onClick = { menuState.show { AlbumMenu(originalAlbum = item, navController = navController, onDismiss = menuState::dismiss) } }) { Icon(painterResource(R.drawable.more_vert), null) } }, modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { navController.navigate("album/${item.id}") }, onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menuState.show { AlbumMenu(originalAlbum = item, navController = navController, onDismiss = menuState::dismiss) } }).animateItem())
                        }
                    }
                }

            LibraryViewType.GRID ->
                LazyVerticalGrid(
                    state = lazyGridState,
                    columns = GridCells.Adaptive(minSize = GridThumbnailHeight + if (gridItemSize == GridItemSize.BIG) 24.dp else (-24).dp),
                    contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
                ) {
                    item(key = "filter", span = { GridItemSpan(maxLineSpan) }) { filterContent() }
                    item(key = "header", span = { GridItemSpan(maxLineSpan) }) { headerContent() }
                    item(key = "liked") {
                        PlaylistGridItem(playlist = likedPlaylist, fillMaxWidth = true, autoPlaylist = true, modifier = Modifier.fillMaxWidth().clickable { navController.navigate("auto_playlist/liked") }.animateItem(), context = LocalContext.current)
                    }
                    item(key = "downloaded") {
                        PlaylistGridItem(playlist = downloadPlaylist, fillMaxWidth = true, autoPlaylist = true, modifier = Modifier.fillMaxWidth().clickable { navController.navigate("auto_playlist/downloaded") }.animateItem(), context = LocalContext.current)
                    }
                    item(key = "top") {
                        PlaylistGridItem(playlist = topPlaylist.copy(playlist = topPlaylist.playlist.copy(name = "${stringResource(R.string.my_top)} $topSize")), fillMaxWidth = true, autoPlaylist = true, modifier = Modifier.fillMaxWidth().clickable { navController.navigate("top_playlist/$topSize") }.animateItem(), context = LocalContext.current)
                    }
                    item(key = "cache") {
                        PlaylistGridItem(playlist = cachePlaylist, fillMaxWidth = true, autoPlaylist = true, modifier = Modifier.fillMaxWidth().clickable { navController.navigate("cache_playlist/cached") }.animateItem(), context = LocalContext.current)
                    }
                    items(items = allItems, key = { it.id }) { item ->
                        when (item) {
                            is Playlist -> PlaylistGridItem(playlist = item, fillMaxWidth = true, modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { navController.navigate("local_playlist/${item.id}") }, onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menuState.show { PlaylistMenu(playlist = item, coroutineScope = coroutineScope, onDismiss = menuState::dismiss) } }).animateItem(), context = LocalContext.current)
                            is Artist -> ArtistGridItem(artist = item, fillMaxWidth = true, modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { navController.navigate("artist/${item.id}") }, onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menuState.show { ArtistMenu(originalArtist = item, coroutineScope = coroutineScope, onDismiss = menuState::dismiss) } }).animateItem())
                            is Album -> AlbumGridItem(album = item, isActive = item.id == mediaMetadata?.album?.id, isPlaying = isPlaying, coroutineScope = coroutineScope, fillMaxWidth = true, modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { navController.navigate("album/${item.id}") }, onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menuState.show { AlbumMenu(originalAlbum = item, navController = navController, onDismiss = menuState::dismiss) } }).animateItem())
                        }
                    }
                }
        }
    }
}