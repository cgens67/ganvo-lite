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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import coil.compose.AsyncImage
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
import com.ganvo.music.db.entities.Album
import com.ganvo.music.db.entities.Artist
import com.ganvo.music.db.entities.LocalItem
import com.ganvo.music.db.entities.Playlist
import com.ganvo.music.db.entities.Song
import com.ganvo.music.extensions.togglePlayPause
import com.ganvo.music.models.toMediaMetadata
import com.ganvo.music.playback.queues.LocalAlbumRadio
import com.ganvo.music.playback.queues.YouTubeAlbumRadio
import com.ganvo.music.playback.queues.YouTubeQueue
import com.ganvo.music.ui.component.AlbumGridItem
import com.ganvo.music.ui.component.ArtistGridItem
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
import com.ganvo.music.utils.rememberPreference
import com.ganvo.music.viewmodels.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
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
    val keepListening by viewModel.keepListening.collectAsState()
    val similarRecommendations by viewModel.similarRecommendations.collectAsState()
    val homePage by viewModel.homePage.collectAsState()

    val allLocalItems by viewModel.allLocalItems.collectAsState()
    val allYtItems by viewModel.allYtItems.collectAsState()

    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val pullRefreshState = rememberPullToRefreshState()

    val accountName by rememberPreference(AccountNameKey, "")
    val innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    val isLoggedIn = remember(innerTubeCookie) { "SAPISID" in parseCookieString(innerTubeCookie) }

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
                .width(140.dp)
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
            // App Header (Greeting + Avatar)
            item {
                Spacer(modifier = Modifier.height(24.dp))
                
                val currentHour = LocalDateTime.now().hour
                val greeting = when (currentHour) {
                    in 5..11 -> "Good Morning"
                    in 12..17 -> "Good Afternoon"
                    else -> "Good Evening"
                }
                val dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d"))
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = dateStr.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 2.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = greeting,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    if (isLoggedIn && accountName.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .clickable { navController.navigate("account") },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = accountName.take(1).uppercase(),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { navController.navigate("login") },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.person),
                                contentDescription = "Login",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Quick Actions Grid (2x2)
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp, bottom = 16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        QuickActionCard(
                            modifier = Modifier.weight(1f),
                            title = stringResource(R.string.history),
                            icon = R.drawable.history,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            onColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) { navController.navigate("history") }
                        
                        QuickActionCard(
                            modifier = Modifier.weight(1f),
                            title = stringResource(R.string.stats),
                            icon = R.drawable.trending_up,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            onColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ) { navController.navigate("stats") }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        QuickActionCard(
                            modifier = Modifier.weight(1f),
                            title = stringResource(R.string.liked),
                            icon = R.drawable.favorite,
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            onColor = MaterialTheme.colorScheme.onTertiaryContainer
                        ) { navController.navigate("auto_playlist/liked") }
                        
                        QuickActionCard(
                            modifier = Modifier.weight(1f),
                            title = stringResource(R.string.offline),
                            icon = R.drawable.offline,
                            color = MaterialTheme.colorScheme.errorContainer,
                            onColor = MaterialTheme.colorScheme.onErrorContainer
                        ) { navController.navigate("auto_playlist/downloaded") }
                    }
                }
            }

            // Quick Picks (Hero Carousel)
            quickPicks?.takeIf { it.isNotEmpty() }?.let { picks ->
                item { 
                    Text(
                        text = stringResource(R.string.quick_picks), 
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                }
                item {
                    val listState = rememberLazyListState()
                    val snappingLayout = rememberSnapFlingBehavior(snapLayoutInfoProvider = rememberSnapLayoutInfoProvider(listState))
                    LazyRow(
                        state = listState,
                        flingBehavior = snappingLayout,
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth().height(160.dp)
                    ) {
                        itemsIndexed(items = picks, key = { _, it -> it.id }) { _, originalSong ->
                            val song by database.song(originalSong.id).collectAsState(initial = originalSong)
                            song?.let { loadedSong ->
                                HeroSongCard(
                                    song = loadedSong,
                                    isPlaying = isPlaying && mediaMetadata?.id == loadedSong.id,
                                    onClick = { 
                                        if (loadedSong.id == mediaMetadata?.id) playerConnection.player.togglePlayPause() 
                                        else playerConnection.playQueue(YouTubeQueue.radio(loadedSong.toMediaMetadata())) 
                                    },
                                    onLongClick = { 
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        menuState.show { 
                                            SongMenu(originalSong = loadedSong, navController = navController, onDismiss = menuState::dismiss) 
                                        } 
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            // Keep Listening (Grid)
            keepListening?.takeIf { it.isNotEmpty() }?.let { items ->
                item { 
                    Text(
                        text = stringResource(R.string.keep_listening), 
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    ) 
                }
                item {
                    val rows = if (items.size > 6) 2 else 1
                    LazyHorizontalGrid(
                        rows = GridCells.Fixed(rows),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        modifier = Modifier.fillMaxWidth().height((GridThumbnailHeight + 60.dp) * rows)
                    ) {
                        items(items) { item ->
                            when (item) {
                                is Song -> SongGridItem(song = item, isActive = item.id == mediaMetadata?.id, isPlaying = isPlaying, modifier = Modifier.width(140.dp).combinedClickable(onClick = { if (item.id == mediaMetadata?.id) playerConnection.player.togglePlayPause() else playerConnection.playQueue(YouTubeQueue.radio(item.toMediaMetadata())) }, onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menuState.show { SongMenu(originalSong = item, navController = navController, onDismiss = menuState::dismiss) } }))
                                is Album -> AlbumGridItem(album = item, isActive = item.id == mediaMetadata?.album?.id, isPlaying = isPlaying, coroutineScope = scope, modifier = Modifier.width(140.dp).combinedClickable(onClick = { navController.navigate("album/${item.id}") }, onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menuState.show { AlbumMenu(originalAlbum = item, navController = navController, onDismiss = menuState::dismiss) } }))
                                is Artist -> ArtistGridItem(artist = item, modifier = Modifier.width(140.dp).combinedClickable(onClick = { navController.navigate("artist/${item.id}") }, onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menuState.show { ArtistMenu(originalArtist = item, coroutineScope = scope, onDismiss = menuState::dismiss) } }))
                                else -> {}
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // YouTube Home Page Sections
            homePage?.sections?.forEach { section ->
                item { NavigationTitle(title = section.title, label = section.label) }
                item { 
                    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp)) { 
                        items(section.items) { ytGridItem(it) } 
                    } 
                }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickActionCard(
    modifier: Modifier = Modifier,
    title: String,
    icon: Int,
    color: Color,
    onColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.height(64.dp),
        colors = CardDefaults.cardColors(containerColor = color),
        shape = RoundedCornerShape(16.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = icon),
                contentDescription = null,
                tint = onColor,
                modifier = Modifier.size(26.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = onColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HeroSongCard(
    song: Song,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(300.dp)
            .height(140.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Blurred background
            AsyncImage(
                model = song.song.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(40.dp).alpha(0.5f)
            )
            
            // Gradient overlay for readability
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
                        )
                    )
                )
            )
            
            Row(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = song.song.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(108.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .shadow(8.dp, RoundedCornerShape(12.dp))
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = song.song.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 22.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = song.artists.joinToString { it.name },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    // Play Indicator
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
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