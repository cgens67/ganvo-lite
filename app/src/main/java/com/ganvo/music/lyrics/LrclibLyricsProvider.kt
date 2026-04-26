package com.ganvo.music.lyrics

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object LrclibLyricsProvider : LyricsProvider {
    override val name = "LRCLIB"
    
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
            // First attempt: exact match with get endpoint
            val getUrl = "https://lrclib.net/api/get".toHttpUrl().newBuilder()
                .addQueryParameter("track_name", title)
                .addQueryParameter("artist_name", artist)
                .addQueryParameter("duration", duration.toString())
                .build()

            val request = Request.Builder()
                .url(getUrl)
                .header("User-Agent", "GanvoMusicPlayer")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: throw Exception("Empty response body")
                val jsonObject = JSONObject(responseBody)
                val syncedLyrics = jsonObject.optString("syncedLyrics", "")
                if (syncedLyrics.isNotBlank()) return@runCatching syncedLyrics
            }

            // Fallback attempt: search endpoint
            val searchUrl = "https://lrclib.net/api/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", "$title $artist")
                .build()

            val searchReq = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "GanvoMusicPlayer")
                .build()

            val searchRes = client.newCall(searchReq).execute()
            if (!searchRes.isSuccessful) throw Exception("Failed to fetch lyrics")
            
            val searchBody = searchRes.body?.string() ?: throw Exception("Empty search response")
            val array = JSONArray(searchBody)
            
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val synced = item.optString("syncedLyrics", "")
                if (synced.isNotBlank()) return@runCatching synced
            }
            
            throw Exception("No synced lyrics found")
        }
    }
}