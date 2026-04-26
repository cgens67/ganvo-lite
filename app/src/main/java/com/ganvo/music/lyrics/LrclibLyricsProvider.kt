package com.ganvo.music.lyrics

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object LrclibLyricsProvider : LyricsProvider {
    override val name = "LRCLIB"
    
    // OkHttpClient with timeouts
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    override fun isEnabled(context: Context) = true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://lrclib.net/api/get".toHttpUrl().newBuilder()
                .addQueryParameter("track_name", title)
                .addQueryParameter("artist_name", artist)
                .addQueryParameter("duration", duration.toString())
                .build()

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "GanvoMusicPlayer")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) throw Exception("Failed to fetch lyrics: HTTP ${response.code}")

            val responseBody = response.body?.string() ?: throw Exception("Empty response body")
            val jsonObject = JSONObject(responseBody)
            
            // Prefer syncedLyrics as it contains line/word timestamps
            val syncedLyrics = jsonObject.optString("syncedLyrics", "")
            if (syncedLyrics.isNotBlank()) {
                syncedLyrics
            } else {
                val plainLyrics = jsonObject.optString("plainLyrics", "")
                if (plainLyrics.isNotBlank()) {
                    plainLyrics
                } else {
                    throw Exception("No lyrics found in response")
                }
            }
        }
    }
}