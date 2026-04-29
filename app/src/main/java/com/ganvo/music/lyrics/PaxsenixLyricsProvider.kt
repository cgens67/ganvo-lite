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

object PaxsenixLyricsProvider : LyricsProvider {
    override val name = "Paxsenix (Spotify)"

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
            val url = "https://api.paxsenix.biz.id/lyrics".toHttpUrl().newBuilder()
                .addQueryParameter("q", "$title $artist")
                .addQueryParameter("type", "default")
                .build()

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: throw Exception("Paxsenix body was empty")

            if (!response.isSuccessful) {
                throw Exception("Paxsenix network error: ${response.code}")
            }

            try {
                // Escenario 1: JSON Object nativo
                val jsonObject = JSONObject(body)
                val syncedLyrics = jsonObject.optString("lyrics", "")
                if (syncedLyrics.isNotBlank() && syncedLyrics != "null") return@runCatching syncedLyrics
            } catch(e: Exception) {}
            
            try {
                // Escenario 2: Arreglo de JSON 
                val array = JSONArray(body)
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val syncedLyrics = item.optString("lyrics", "")
                    if (syncedLyrics.isNotBlank() && syncedLyrics != "null") return@runCatching syncedLyrics
                }
            } catch (e: Exception) {}

            // Escenario 3: Raw LRC devuelto como texto
            if (body.contains("[") && body.contains("]")) {
                return@runCatching body
            }

            throw Exception("Failed traversing Paxsenix JSON structure")
        }
    }
}