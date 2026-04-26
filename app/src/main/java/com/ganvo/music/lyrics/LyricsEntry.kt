package com.ganvo.music.lyrics

data class LyricsEntry(
    val time: Long,
    val text: String,
    val words: List<WordEntry>? = null
) : Comparable<LyricsEntry> {
    override fun compareTo(other: LyricsEntry): Int = (time - other.time).toInt()

    companion object {
        val HEAD_LYRICS_ENTRY = LyricsEntry(0L, "")
    }
}

data class WordEntry(
    val startTime: Long,
    val endTime: Long,
    val text: String
)