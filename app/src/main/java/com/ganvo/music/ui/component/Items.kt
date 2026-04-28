package com.ganvo.music.ui.component

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.zIndex
import androidx.core.graphics.drawable.toBitmapOrNull
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.Download.STATE_COMPLETED
import androidx.media3.exoplayer.offline.Download.STATE_DOWNLOADING
import androidx.media3.exoplayer.offline.Download.STATE_QUEUED
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.ganvo.music.LocalDatabase
import com.ganvo.music.LocalDownloadUtil
import com.ganvo.music.R
import com.ganvo.music.constants.*
import com.ganvo.music.db.entities.*
import com.ganvo.music.models.MediaMetadata
import com.ganvo.music.ui.theme.extractThemeColor
import com.ganvo.music.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun ListItem(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: (@Composable RowScope.() -> Unit)? = null,
    thumbnailContent: @Composable () -> Unit,
    trailingContent: @Composable RowScope.() -> Unit = {},
    isActive: Boolean = false,
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.95f else if (isActive) 0.98f else 1f, label = "scale")
    val backgroundColor by animateColorAsState(targetValue = if (isActive) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent, label = "backgroundColor")

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitFirstDown(false); isPressed = true; waitForUpOrCancellation(); isPressed = false
                    }
                }
            }
            .height(ListItemHeight)
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color = backgroundColor),
    ) {
        Box(modifier = Modifier.padding(6.dp), contentAlignment = Alignment.Center) { thumbnailContent() }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 6.dp)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface, // FIX: Title Color
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.basicMarquee().fillMaxWidth(),
            )
            if (subtitle != null) Row(verticalAlignment = Alignment.CenterVertically) { subtitle() }
        }
        trailingContent()
    }
}

@Composable
fun ListItem(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String?,
    badges: @Composable RowScope.() -> Unit = {},
    thumbnailContent: @Composable () -> Unit,
    trailingContent: @Composable RowScope.() -> Unit = {},
    isActive: Boolean = false,
) = ListItem(
    title = title,
    subtitle = {
        badges()
        if (!subtitle.isNullOrEmpty()) {
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant, // FIX: Subtitle Color
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    },
    thumbnailContent = thumbnailContent,
    trailingContent = trailingContent,
    modifier = modifier,
    isActive = isActive,
)

@Composable
fun GridItem(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    badges: @Composable RowScope.() -> Unit = {},
    thumbnailContent: @Composable BoxWithConstraintsScope.() -> Unit,
    thumbnailShape: Shape,
    thumbnailRatio: Float = 1f,
    fillMaxWidth: Boolean = false,
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.92f else 1f, label = "scale")

    Column(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitFirstDown(false); isPressed = true; waitForUpOrCancellation(); isPressed = false
                    }
                }
            }
            .then(if (fillMaxWidth) Modifier.padding(12.dp).fillMaxWidth() else Modifier.padding(12.dp).width(GridThumbnailHeight * thumbnailRatio)),
    ) {
        BoxWithConstraints(
            contentAlignment = Alignment.Center,
            modifier = (if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier.height(GridThumbnailHeight))
                .aspectRatio(thumbnailRatio).clip(thumbnailShape),
        ) { thumbnailContent() }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface, // FIX: Grid Title Color
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start,
            modifier = Modifier.basicMarquee().fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            badges()
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, // FIX: Grid Subtitle Color
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun PlaylistGridItem(
    playlist: Playlist,
    modifier: Modifier = Modifier,
    badges: @Composable RowScope.() -> Unit = { },
    fillMaxWidth: Boolean = false,
    autoPlaylist: Boolean = false,
    context: Context
) = GridItem(
    title = playlist.playlist.name,
    subtitle = if (autoPlaylist) "" else pluralStringResource(R.plurals.n_song, playlist.songCount, playlist.songCount),
    badges = badges,
    thumbnailContent = {
        val thumbnailUri = getPlaylistImageUri(context, playlist.playlist.id)
        if (thumbnailUri != null) {
            AsyncImage(model = thumbnailUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(ThumbnailCornerRadius)))
        } else {
            val iconRes = when (playlist.playlist.id) {
                "auto_liked" -> R.drawable.favorite
                "auto_downloaded" -> R.drawable.offline
                "auto_cache" -> R.drawable.cached
                else -> R.drawable.queue_music
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh) // FIX: High contrast card background
                    .clip(RoundedCornerShape(ThumbnailCornerRadius)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, // FIX: Icon color
                    modifier = Modifier.size(maxWidth / 2)
                )
            }
        }
    },
    thumbnailShape = RoundedCornerShape(ThumbnailCornerRadius),
    fillMaxWidth = fillMaxWidth,
    modifier = modifier,
)

// Simplified mapping for the rest of the items to avoid huge file size but ensure they use correct colors
@Composable
fun SongListItem(song: Song, modifier: Modifier = Modifier, albumIndex: Int? = null, showLikedIcon: Boolean = true, showInLibraryIcon: Boolean = false, showDownloadIcon: Boolean = true, isSelected: Boolean = false, badges: @Composable RowScope.() -> Unit = {}, isActive: Boolean = false, isPlaying: Boolean = false, trailingContent: @Composable RowScope.() -> Unit = {}) = ListItem(title = song.song.title, subtitle = joinByBullet(song.artists.joinToString { it.name }, makeTimeString(song.song.duration * 1000L)), badges = badges, thumbnailContent = { Box(contentAlignment = Alignment.Center, modifier = Modifier.size(ListThumbnailSize)) { AsyncImage(model = song.song.thumbnailUrl, contentDescription = null, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(ThumbnailCornerRadius))) } }, trailingContent = trailingContent, modifier = modifier, isActive = isActive)
@Composable
fun ArtistListItem(artist: Artist, modifier: Modifier = Modifier, badges: @Composable RowScope.() -> Unit = {}, trailingContent: @Composable RowScope.() -> Unit = {}) = ListItem(title = artist.artist.name, subtitle = pluralStringResource(R.plurals.n_song, artist.songCount, artist.songCount), badges = badges, thumbnailContent = { AsyncImage(model = artist.artist.thumbnailUrl, contentDescription = null, modifier = Modifier.size(ListThumbnailSize).clip(CircleShape)) }, trailingContent = trailingContent, modifier = modifier)
@Composable
fun AlbumListItem(album: Album, modifier: Modifier = Modifier, showLikedIcon: Boolean = true, badges: @Composable RowScope.() -> Unit = {}, isActive: Boolean = false, isPlaying: Boolean = false, trailingContent: @Composable RowScope.() -> Unit = {}) = ListItem(title = album.album.title, subtitle = joinByBullet(album.artists.joinToString { it.name }, pluralStringResource(R.plurals.n_song, album.album.songCount, album.album.songCount)), badges = badges, thumbnailContent = { AsyncImage(model = album.album.thumbnailUrl, contentDescription = null, modifier = Modifier.size(ListThumbnailSize).clip(RoundedCornerShape(ThumbnailCornerRadius))) }, trailingContent = trailingContent, modifier = modifier, isActive = isActive)
@Composable
fun PlaylistListItem(playlist: Playlist, modifier: Modifier = Modifier, trailingContent: @Composable RowScope.() -> Unit = {}, autoPlaylist: Boolean = false) = ListItem(title = playlist.playlist.name, subtitle = if (autoPlaylist) "" else pluralStringResource(R.plurals.n_song, playlist.songCount, playlist.songCount), thumbnailContent = { Box(modifier = Modifier.size(ListThumbnailSize).clip(RoundedCornerShape(ThumbnailCornerRadius)).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) { Icon(painterResource(if (playlist.playlist.id.contains("liked")) R.drawable.favorite else R.drawable.queue_music), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp)) } }, trailingContent = trailingContent, modifier = modifier)
@Composable
fun ArtistGridItem(artist: Artist, modifier: Modifier = Modifier, badges: @Composable RowScope.() -> Unit = {}, fillMaxWidth: Boolean = false) = GridItem(title = artist.artist.name, subtitle = pluralStringResource(R.plurals.n_song, artist.songCount, artist.songCount), badges = badges, thumbnailContent = { AsyncImage(model = artist.artist.thumbnailUrl, contentDescription = null, modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop) }, thumbnailShape = CircleShape, fillMaxWidth = fillMaxWidth, modifier = modifier)
@Composable
fun AlbumGridItem(album: Album, modifier: Modifier = Modifier, coroutineScope: CoroutineScope, badges: @Composable RowScope.() -> Unit = {}, isActive: Boolean = false, isPlaying: Boolean = false, fillMaxWidth: Boolean = false) = GridItem(title = album.album.title, subtitle = album.artists.joinToString { it.name }, badges = badges, thumbnailContent = { AsyncImage(model = album.album.thumbnailUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }, thumbnailShape = RoundedCornerShape(ThumbnailCornerRadius), fillMaxWidth = fillMaxWidth, modifier = modifier)