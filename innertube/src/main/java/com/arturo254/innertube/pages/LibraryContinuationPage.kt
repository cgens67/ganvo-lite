package com.Ganvo.innertube.pages

import com.Ganvo.innertube.models.YTItem

data class LibraryContinuationPage(
    val items: List<YTItem>,
    val continuation: String?,
)
