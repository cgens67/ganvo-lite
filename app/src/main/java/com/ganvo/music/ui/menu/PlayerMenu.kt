package com.ganvo.music.ui.menu

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ganvo.music.LocalPlayerConnection
import com.ganvo.music.R
import com.ganvo.music.models.MediaMetadata
import com.ganvo.music.ui.component.ListItem

@Composable
fun PlayerMenu(
    mediaMetadata: MediaMetadata,
    navController: NavController,
    onDismiss: () -> Unit,
    // Add other missing parameters if your project uses them
) {
    val playerConnection = LocalPlayerConnection.current ?: return

    Column(modifier = Modifier.padding(bottom = 24.dp)) {
        ListItem(
            title = mediaMetadata.title,
            subtitle = {
                Text(text = mediaMetadata.artists.joinToString { it.name })
            },
            thumbnailContent = {
                // AsyncImage call here
            }
        )
        // Grid Menu logic...
    }
}