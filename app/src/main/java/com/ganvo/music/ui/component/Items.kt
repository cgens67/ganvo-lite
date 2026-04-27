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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.ganvo.music.R
import com.ganvo.music.constants.ListItemHeight
import com.ganvo.music.constants.ListThumbnailSize
import com.ganvo.music.constants.ThumbnailCornerRadius
import com.ganvo.music.db.entities.Song
import com.ganvo.music.db.entities.Album
import com.ganvo.music.db.entities.Artist
import com.ganvo.music.db.entities.Playlist
import com.ganvo.music.models.MediaMetadata
import com.ganvo.music.utils.makeTimeString
import com.ganvo.music.utils.joinByBullet
import com.Ganvo.innertube.models.YTItem
import com.Ganvo.innertube.models.SongItem
import com.Ganvo.innertube.models.AlbumItem
import com.Ganvo.innertube.models.ArtistItem
import com.Ganvo.innertube.models.PlaylistItem
import com.ganvo.music.LocalDownloadUtil

@Composable
fun ListItem(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: (@Composable RowScope.() -> Unit)? = null,
    thumbnailContent: @Composable () -> Unit,
    trailingContent: @Composable RowScope.() -> Unit = {},
    isActive: Boolean = false,
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isActive) Color.White.copy(alpha = 0.15f) else Color.Transparent,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "backgroundColor"
    )

    // FORCE HIGH CONTRAST COLORS
    val titleColor = Color.White
    val subtitleColor = Color.White.copy(alpha = 0.6f)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(ListItemHeight)
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(color = backgroundColor),
    ) {
        Box(
            modifier = Modifier.padding(6.dp),
            contentAlignment = Alignment.Center,
        ) {
            thumbnailContent()
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 6.dp),
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.basicMarquee()
            )

            if (subtitle != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CompositionLocalProvider(LocalContentColor provides subtitleColor) {
                        subtitle()
                    }
                }
            }
        }
        trailingContent()
    }
}

@Composable
fun YouTubeListItem(
    item: YTItem,
    modifier: Modifier = Modifier,
    albumIndex: Int? = null,
    isSelected: Boolean = false,
    badges: @Composable RowScope.() -> Unit = {},
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    trailingContent: @Composable RowScope.() -> Unit = {},
) = ListItem(
    title = item.title,
    subtitle = {
        badges()
        val text = when (item) {
            is SongItem -> joinByBullet(item.artists.joinToString { it.name }, item.duration?.let { makeTimeString(it * 1000L) })
            is AlbumItem -> joinByBullet(item.artists?.joinToString { it.name }, item.year?.toString())
            is PlaylistItem -> joinByBullet(item.author?.name, item.songCountText)
            else -> ""
        }
        Text(text = text ?: "", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    },
    thumbnailContent = {
        Box(modifier = Modifier.size(ListThumbnailSize)) {
            AsyncImage(
                model = item.thumbnail,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(ThumbnailCornerRadius))
            )
            if (isActive) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))) {
                    Icon(
                        painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.align(Alignment.Center).size(20.dp)
                    )
                }
            }
        }
    },
    trailingContent = trailingContent,
    modifier = modifier,
    isActive = isActive,
)

@Composable
fun SongListItem(
    song: Song,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    isSelected: Boolean = false,
    showInLibraryIcon: Boolean = false,
    trailingContent: @Composable RowScope.() -> Unit = {},
) = ListItem(
    title = song.song.title,
    subtitle = {
        Text(
            text = joinByBullet(song.artists.joinToString { it.name }, makeTimeString(song.song.duration * 1000L)),
            fontSize = 12.sp,
            maxLines = 1
        )
    },
    thumbnailContent = {
        Box(modifier = Modifier.size(ListThumbnailSize)) {
            AsyncImage(
                model = song.song.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(ThumbnailCornerRadius))
            )
            if (isActive) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))) {
                    Icon(
                        painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.align(Alignment.Center).size(20.dp)
                    )
                }
            }
        }
    },
    trailingContent = trailingContent,
    modifier = modifier,
    isActive = isActive,
)

@Composable
fun PlaylistListItem(
    playlist: Playlist,
    modifier: Modifier = Modifier,
    autoPlaylist: Boolean = false,
    trailingContent: @Composable RowScope.() -> Unit = {},
) = ListItem(
    title = playlist.playlist.name,
    subtitle = {
        Text(text = if (autoPlaylist) "" else "${playlist.songCount} songs", fontSize = 12.sp)
    },
    thumbnailContent = {
        Box(modifier = Modifier.size(ListThumbnailSize).background(Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(ThumbnailCornerRadius))) {
            if (playlist.thumbnails.isNotEmpty()) {
                AsyncImage(model = playlist.thumbnails.first(), contentDescription = null, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(ThumbnailCornerRadius)))
            } else {
                Icon(painterResource(R.drawable.queue_music), null, modifier = Modifier.align(Alignment.Center), tint = Color.White)
            }
        }
    },
    trailingContent = trailingContent,
    modifier = modifier,
)

@Composable
fun ArtistListItem(
    artist: Artist,
    modifier: Modifier = Modifier,
    badges: @Composable RowScope.() -> Unit = {},
    trailingContent: @Composable RowScope.() -> Unit = {},
) = ListItem(
    title = artist.artist.name,
    subtitle = { Text("${artist.songCount} songs", fontSize = 12.sp) },
    thumbnailContent = {
        AsyncImage(model = artist.artist.thumbnailUrl, contentDescription = null, modifier = Modifier.size(ListThumbnailSize).clip(CircleShape))
    },
    trailingContent = trailingContent,
    modifier = modifier,
)

@Composable
fun AlbumListItem(
    album: Album,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    showLikedIcon: Boolean = true,
    badges: @Composable RowScope.() -> Unit = {},
    trailingContent: @Composable RowScope.() -> Unit = {},
) = ListItem(
    title = album.album.title,
    subtitle = { Text(joinByBullet(album.artists.joinToString { it.name }, album.album.year?.toString()), fontSize = 12.sp) },
    thumbnailContent = {
        AsyncImage(model = album.album.thumbnailUrl, contentDescription = null, modifier = Modifier.size(ListThumbnailSize).clip(RoundedCornerShape(ThumbnailCornerRadius)))
    },
    trailingContent = trailingContent,
    modifier = modifier,
    isActive = isActive,
)

@Composable
fun MediaMetadataListItem(
    mediaMetadata: MediaMetadata,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    isSelected: Boolean = false,
    trailingContent: @Composable RowScope.() -> Unit = {},
) = ListItem(
    title = mediaMetadata.title,
    subtitle = { Text(joinByBullet(mediaMetadata.artists.joinToString { it.name }, makeTimeString(mediaMetadata.duration * 1000L)), fontSize = 12.sp) },
    thumbnailContent = {
        AsyncImage(model = mediaMetadata.thumbnailUrl, contentDescription = null, modifier = Modifier.size(ListThumbnailSize).clip(RoundedCornerShape(ThumbnailCornerRadius)))
    },
    trailingContent = trailingContent,
    modifier = modifier,
    isActive = isActive,
)