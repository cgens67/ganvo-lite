package com.ganvo.music.lyrics

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object PaxsenixLyricsProvider : LyricsProvider {
    override val name = "Paxsenix (Spotify / BetterLyrics)"

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    override fun isEnabled(context: Context) = true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            // Paxsenix universal generic search lyric proxy (Standard ViMusic standard plugin route)
            val url = "https://api.paxsenix.biz.id/lyrics".toHttpUrl().newBuilder()
                .addQueryParameter("q", "$title $artist")
                .addQueryParameter("type", "default")
                .build()

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "GanvoMusic/1.0")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: throw Exception("Paxsenix body was completely empty")

            if (!response.isSuccessful) {
                throw Exception("Paxsenix network err code: ${response.code} with payload: $body")
            }

            try {
                // Scenario 1: Native root json 
                val jsonObject = JSONObject(body)
                val syncedLyrics = jsonObject.optString("lyrics", "")
                if (syncedLyrics.isNotBlank() && syncedLyrics != "null") return@runCatching syncedLyrics
            } catch(e: Exception) {}
            
            try {
                // Scenario 2: Array listing payload responses
                val array = org.json.JSONArray(body)
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val syncedLyrics = item.optString("lyrics", "")
                    if (syncedLyrics.isNotBlank() && syncedLyrics != "null") return@runCatching syncedLyrics
                }
            } catch (e: Exception) {}

            // Scenario 3: Already an unfiltered raw string 
            if (body.contains("[") && body.contains("]")) {
                return@runCatching body
            }

            throw Exception("Failed traversing synced lyrics array safely via paxsenix output provider context payload parser")
        }
    }
}