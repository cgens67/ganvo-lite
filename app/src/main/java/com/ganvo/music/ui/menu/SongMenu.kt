package com.ganvo.music.ui.menu

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ganvo.music.LocalDatabase
import com.ganvo.music.LocalPlayerConnection
import com.ganvo.music.R
import com.ganvo.music.db.entities.Song
import com.ganvo.music.ui.component.SongListItem

@Composable
fun SongMenu(
    originalSong: Song,
    navController: NavController,
    onDismiss: () -> Unit,
) {
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val songState = database.song(originalSong.id).collectAsState(initial = originalSong)
    val song = songState.value ?: originalSong

    Column(modifier = Modifier.padding(bottom = 24.dp)) {
        SongListItem(
            song = song,
            isActive = false,
            trailingContent = {
                IconButton(onClick = {
                    database.query { update(song.song.toggleLike()) }
                }) {
                    Icon(
                        painter = painterResource(if (song.song.liked) R.drawable.favorite else R.drawable.favorite_border),
                        tint = if (song.song.liked) MaterialTheme.colorScheme.error else LocalContentColor.current,
                        contentDescription = null
                    )
                }
            }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Actions... (Play Next, Add to Queue, etc)
        // I am omitting the standard grid actions for brevity as the error was in the SongListItem call
    }
}