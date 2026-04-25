package com.ganvo.music.models

import com.Ganvo.innertube.models.YTItem
import com.ganvo.music.db.entities.LocalItem

data class SimilarRecommendation(
    val title: LocalItem,
    val items: List<YTItem>,
)
