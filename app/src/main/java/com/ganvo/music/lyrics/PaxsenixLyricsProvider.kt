package com.ganvo.music.lyrics

import android.content.Context
import com.metrolist.paxsenix.Paxsenix

object PaxsenixLyricsProvider : LyricsProvider {
    override val name = "Paxsenix (Spotify)"

    override fun isEnabled(context: Context) = true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
    ): Result<String> {
        return Paxsenix.getLyrics(title, artist, duration, null)
    }

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        callback: (String) -> Unit,
    ) {
        Paxsenix.getAllLyrics(title, artist, duration, null) {
            callback(it)
        }
    }
}