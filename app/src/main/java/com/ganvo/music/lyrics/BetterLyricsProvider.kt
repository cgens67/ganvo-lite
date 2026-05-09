package com.ganvo.music.lyrics

import android.content.Context
import com.ganvo.music.betterlyrics.BetterLyrics

object BetterLyricsProvider : LyricsProvider {
    override val name = "BetterLyrics (Apple Music)"

    override fun isEnabled(context: Context) = true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
    ): Result<String> {
        return BetterLyrics.getLyrics(title, artist, duration, null)
    }
}