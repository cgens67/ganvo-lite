package com.ganvo.music.lyrics

data class LyricsEntry(
    val time: Long,
    val text: String,
    val romanizedText: String? = null,
    val words: List<LyricsWord> = emptyList()
) : Comparable<LyricsEntry> {
    override fun compareTo(other: LyricsEntry): Int = (time - other.time).toInt()

    companion object {
        val HEAD_LYRICS_ENTRY = LyricsEntry(0L, "", null, emptyList())
    }
}

data class LyricsWord(
    val time: Long,
    val duration: Long,
    val text: String
)