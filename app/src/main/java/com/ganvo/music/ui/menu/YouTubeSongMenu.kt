package com.ganvo.music.ui.menu

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.Ganvo.innertube.models.SongItem
import com.ganvo.music.ui.component.ListItem

@Composable
fun YouTubeSongMenu(
    song: SongItem,
    navController: NavController,
    onDismiss: () -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = 24.dp)) {
        ListItem(
            title = song.title,
            subtitle = {
                Text(text = song.artists.joinToString { it.name })
            },
            thumbnailContent = {
               // Thumbnail logic
            }
        )
        // Grid Menu actions...
    }
}