package com.ganvo.music.lyrics

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object BetterLyricsProvider : LyricsProvider {
    override val name = "BetterLyrics"

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
            // Using Paxsenix API which returns Apple Music style word-synced JSON
            val url = "https://paxsenix.id.vn/api/lyrics".toHttpUrl().newBuilder()
                .addQueryParameter("q", "$title $artist")
                .addQueryParameter("type", "default")
                .build()

            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) throw Exception("Failed to fetch")
            
            val body = response.body?.string() ?: throw Exception("Empty body")
            // Return the raw JSON so LyricsUtils can detect and parse it
            body 
        }
    }
}