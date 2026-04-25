package com.Ganvo.innertube.pages

import com.Ganvo.innertube.models.SongItem

data class PlaylistContinuationPage(
    val songs: List<SongItem>,
    val continuation: String?,
)
