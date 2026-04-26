package com.ganvo.music.ui.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest

// EXACT IMPORTS TO FIX AMBIGUITY BUG
import com.Ganvo.innertube.models.AlbumItem
import com.Ganvo.innertube.models.ArtistItem
import com.Ganvo.innertube.models.PlaylistItem
import com.Ganvo.innertube.models.SongItem
import com.Ganvo.innertube.models.WatchEndpoint
import com.Ganvo.innertube.models.YTItem
import com.Ganvo.innertube.utils.parseCookieString
import com.ganvo.music.LocalDatabase
import com.ganvo.music.LocalPlayerAwareWindowInsets
import com.ganvo.music.LocalPlayerConnection
import com.ganvo.music.R
import com.ganvo.music.constants.AccountNameKey
import com.ganvo.music.constants.GridThumbnailHeight
import com.ganvo.music.constants.InnerTubeCookieKey
import com.ganvo.music.constants.ListItemHeight
import com.ganvo.music.constants.ListThumbnailSize
import com.ganvo.music.constants.ThumbnailCornerRadius
import com.ganvo.music.db.entities.Album
import com.ganvo.music.db.entities.Artist
import com.ganvo.music.db.entities.LocalItem
import com.ganvo.music.db.entities.Playlist
import com.ganvo.music.db.entities.Song
import com.ganvo.music.extensions.togglePlayPause
import com.ganvo.music.models.SimilarRecommendation
import com.ganvo.music.models.toMediaMetadata
import com.ganvo.music.playback.queues.LocalAlbumRadio
import com.ganvo.music.playback.queues.YouTubeAlbumRadio
import com.ganvo.music.playback.queues.YouTubeQueue
import com.ganvo.music.ui.component.AlbumGridItem
import com.ganvo.music.ui.component.ArtistGridItem
import com.ganvo.music.ui.component.ChipsRow
import com.ganvo.music.ui.component.HideOnScrollFAB
import com.ganvo.music.ui.component.LocalMenuState
import com.ganvo.music.ui.component.NavigationTitle
import com.ganvo.music.ui.component.SongGridItem
import com.ganvo.music.ui.component.YouTubeGridItem
import com.ganvo.music.ui.menu.AlbumMenu
import com.ganvo.music.ui.menu.ArtistMenu
import com.ganvo.music.ui.menu.SongMenu
import com.ganvo.music.ui.menu.YouTubeAlbumMenu
import com.ganvo.music.ui.menu.YouTubeArtistMenu
import com.ganvo.music.ui.menu.YouTubePlaylistMenu
import com.ganvo.music.ui.menu.YouTubeSongMenu
import com.ganvo.music.ui.utils.SnapLayoutInfoProvider
import com.ganvo.music.utils.rememberPreference
import com.ganvo.music.viewmodels.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.random.Random

@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val haptic = LocalHapticFeedback.current

    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val quickPicks by viewModel.quickPicks.collectAsState()
    val forgottenFavorites by viewModel.forgottenFavorites.collectAsState()
    val keepListening by viewModel.keepListening.collectAsState()
    val similarRecommendations by viewModel.similarRecommendations.collectAsState()
    val accountPlaylists by viewModel.accountPlaylists.collectAsState()
    val homePage by viewModel.homePage.collectAsState()
    val explorePage by viewModel.explorePage.collectAsState()

    val allLocalItems by viewModel.allLocalItems.collectAsState()
    val allYtItems by viewModel.allYtItems.collectAsState()

    val isLoading: Boolean by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val pullRefreshState = rememberPullToRefreshState()

    val accountName by rememberPreference(AccountNameKey, "")
    val accountImageUrl by viewModel.accountImageUrl.collectAsState()
    val innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    val isLoggedIn = remember(innerTubeCookie) {
        "SAPISID" in parseCookieString(innerTubeCookie)
    }
    val url = if (isLoggedIn) accountImageUrl else null

    val scope = rememberCoroutineScope()
    val lazylistState = rememberLazyListState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop = backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsState()

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            lazylistState.animateScrollToItem(0)
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    val ytGridItem: @Composable (YTItem) -> Unit = { item ->
        YouTubeGridItem(
            item = item,
            isActive = item.id in listOf(mediaMetadata?.album?.id, mediaMetadata?.id),
            isPlaying = isPlaying,
            coroutineScope = scope,
            thumbnailRatio = 1f,
            modifier = Modifier
                .combinedClickable(
                    onClick = {
                        when (item) {
                            is SongItem -> playerConnection.playQueue(YouTubeQueue(item.endpoint ?: WatchEndpoint(videoId = item.id), item.toMediaMetadata()))
                            is AlbumItem -> navController.navigate("album/${item.id}")
                            is ArtistItem -> navController.navigate("artist/${item.id}")
                            is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
                        }
                    },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuState.show {
                            when (item) {
                                is SongItem -> YouTubeSongMenu(song = item, navController = navController, onDismiss = menuState::dismiss)
                                is AlbumItem -> YouTubeAlbumMenu(albumItem = item, navController = navController, onDismiss = menuState::dismiss)
                                is ArtistItem -> YouTubeArtistMenu(artist = item, onDismiss = menuState::dismiss)
                                is PlaylistItem -> YouTubePlaylistMenu(playlist = item, coroutineScope = scope, onDismiss = menuState::dismiss)
                            }
                        }
                    }
                )
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pullToRefresh(state = pullRefreshState, isRefreshing = isRefreshing, onRefresh = viewModel::refresh),
        contentAlignment = Alignment.TopStart
    ) {
        LazyColumn(
            state = lazylistState,
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
        ) {
            item {
                Row(modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)).fillMaxWidth().animateItem()) {
                    ChipsRow(
                        chips = listOfNotNull(
                            Pair("history", stringResource(R.string.history)),
                            Pair("stats", stringResource(R.string.stats)),
                            Pair("liked", stringResource(R.string.liked)),
                            Pair("downloads", stringResource(R.string.offline)),
                            if (isLoggedIn) Pair("account", stringResource(R.string.account)) else null
                        ),
                        currentValue = "",
                        onValueUpdate = { value ->
                            when (value) {
                                "history" -> navController.navigate("history")
                                "stats" -> navController.navigate("stats")
                                "liked" -> navController.navigate("auto_playlist/liked")
                                "downloads" -> navController.navigate("auto_playlist/downloaded")
                                "account" -> if (isLoggedIn) navController.navigate("account")
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                }
            }

            // --- CAROUSEL QUICK PICKS START ---
            quickPicks?.takeIf { it.isNotEmpty() }?.let { picks ->
                item { NavigationTitle(title = stringResource(R.string.quick_picks)) }
                item {
                    val listState = rememberLazyListState()
                    val snappingLayout = rememberSnapFlingBehavior(snapLayoutInfoProvider = rememberSnapLayoutInfoProvider(listState))
                    LazyRow(
                        state = listState,
                        flingBehavior = snappingLayout,
                        contentPadding = PaddingValues(horizontal = 60.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth().height(400.dp)
                    ) {
                        itemsIndexed(items = picks, key = { _, it -> it.id }) { index, originalSong ->
                            val song by database.song(originalSong.id).collectAsState(initial = originalSong)
                            val scale by remember { derivedStateOf {
                                val layoutInfo = listState.layoutInfo
                                val itemInfo = layoutInfo.visibleItemsInfo.find { it.index == index }
                                if (itemInfo != null) {
                                    val center = (layoutInfo.viewportEndOffset + layoutInfo.viewportStartOffset) / 2
                                    val childCenter = itemInfo.offset + itemInfo.size / 2
                                    val maxDistance = layoutInfo.viewportEndOffset.toFloat() / 2
                                    1f - (Math.abs(center - childCenter).toFloat() / maxDistance).coerceIn(0f, 1f) * 0.25f
                                } else 0.75f
                            }}
                            Box(modifier = Modifier.width(240.dp).fillMaxHeight().graphicsLayer { scaleX = scale; scaleY = scale; alpha = lerp(0.5f, 1f, (scale - 0.75f) / 0.25f) }
                                .shadow(elevation = if (scale > 0.9f) 20.dp else 0.dp, shape = RoundedCornerShape(32.dp), clip = false)
                                .clip(RoundedCornerShape(32.dp))
                                .combinedClickable(
                                    onClick = { if (song!!.id == mediaMetadata?.id) playerConnection.player.togglePlayPause() else playerConnection.playQueue(YouTubeQueue.radio(song!!.toMediaMetadata())) },
                                    onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menuState.show { SongMenu(originalSong = song!!, navController = navController, onDismiss = menuState::dismiss) } }
                                )
                            ) {
                                AsyncImage(model = song?.song?.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)), startY = 400f)))
                                Column(modifier = Modifier.align(Alignment.BottomStart).padding(24.dp)) {
                                    Text(text = song?.song?.title ?: "", style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp), color = Color.White, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(text = song?.artists?.joinToString { it.name } ?: "", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
            // --- CAROUSEL QUICK PICKS END ---

            keepListening?.takeIf { it.isNotEmpty() }?.let { items ->
                item { NavigationTitle(title = stringResource(R.string.keep_listening)) }
                item {
                    val rows = if (items.size > 6) 2 else 1
                    LazyHorizontalGrid(
                        rows = GridCells.Fixed(rows),
                        modifier = Modifier.fillMaxWidth().height((GridThumbnailHeight + 60.dp) * rows)
                    ) {
                        items(items) { item ->
                            when (item) {
                                is Song -> SongGridItem(song = item, isActive = item.id == mediaMetadata?.id, isPlaying = isPlaying, modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { if (item.id == mediaMetadata?.id) playerConnection.player.togglePlayPause() else playerConnection.playQueue(YouTubeQueue.radio(item.toMediaMetadata())) }, onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menuState.show { SongMenu(originalSong = item, navController = navController, onDismiss = menuState::dismiss) } }))
                                is Album -> AlbumGridItem(album = item, isActive = item.id == mediaMetadata?.album?.id, isPlaying = isPlaying, coroutineScope = scope, modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { navController.navigate("album/${item.id}") }, onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menuState.show { AlbumMenu(originalAlbum = item, navController = navController, onDismiss = menuState::dismiss) } }))
                                is Artist -> ArtistGridItem(artist = item, modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { navController.navigate("artist/${item.id}") }, onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menuState.show { ArtistMenu(originalArtist = item, coroutineScope = scope, onDismiss = menuState::dismiss) } }))
                                else -> {}
                            }
                        }
                    }
                }
            }

            accountPlaylists?.takeIf { it.isNotEmpty() }?.let { playlists ->
                item { NavigationTitle(title = accountName, label = stringResource(R.string.your_ytb_playlists), onClick = { navController.navigate("account") }) }
                item { LazyRow { items(playlists) { ytGridItem(it) } } }
            }

            similarRecommendations?.forEach { rec ->
                item { NavigationTitle(title = rec.title.title, label = stringResource(R.string.similar_to), onClick = { /* Navigate logic */ }) }
                item { LazyRow { items(rec.items) { ytGridItem(it) } } }
            }

            homePage?.sections?.forEach { section ->
                item { NavigationTitle(title = section.title, label = section.label) }
                item { LazyRow { items(section.items) { ytGridItem(it) } } }
            }

            explorePage?.newReleaseAlbums?.let { albums ->
                item { NavigationTitle(title = stringResource(R.string.new_release_albums), onClick = { navController.navigate("new_release") }) }
                item { LazyRow { items(albums) { ytGridItem(it) } } }
            }
        }

        HideOnScrollFAB(
            visible = allLocalItems.isNotEmpty() || allYtItems.isNotEmpty(),
            lazyListState = lazylistState,
            icon = R.drawable.shuffle,
            onClick = {
                val local = if (allLocalItems.isNotEmpty() && allYtItems.isNotEmpty()) Random.nextFloat() < 0.5 else allLocalItems.isNotEmpty()
                scope.launch {
                    if (local) {
                        when (val lucky = allLocalItems.random()) {
                            is Song -> playerConnection.playQueue(YouTubeQueue.radio(lucky.toMediaMetadata()))
                            is Album -> database.albumWithSongs(lucky.id).first()?.let { playerConnection.playQueue(LocalAlbumRadio(it)) }
                            else -> {}
                        }
                    } else {
                        when (val lucky = allYtItems.random()) {
                            is SongItem -> playerConnection.playQueue(YouTubeQueue.radio(lucky.toMediaMetadata()))
                            is AlbumItem -> playerConnection.playQueue(YouTubeAlbumRadio(lucky.playlistId))
                            else -> {}
                        }
                    }
                }
            }
        )

        Indicator(isRefreshing = isRefreshing, state = pullRefreshState, modifier = Modifier.align(Alignment.TopCenter).padding(LocalPlayerAwareWindowInsets.current.asPaddingValues()))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rememberSnapLayoutInfoProvider(lazyListState: androidx.compose.foundation.lazy.LazyListState) = remember(lazyListState) {
    androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider(lazyListState)
}

fun lerp(start: Float, stop: Float, fraction: Float): Float {
    return (1 - fraction) * start + fraction * stop
}