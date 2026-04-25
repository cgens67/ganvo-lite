package com.Ganvo.innertube.pages

import com.Ganvo.innertube.models.YTItem

data class ArtistItemsContinuationPage(
    val items: List<YTItem>,
    val continuation: String?,
)
