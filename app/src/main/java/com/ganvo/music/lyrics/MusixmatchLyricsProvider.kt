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

object MusixmatchLyricsProvider : LyricsProvider {
    override val name = "Musixmatch"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private var userToken: String? = null

    override fun isEnabled(context: Context) = true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (userToken == null) {
                val tokenUrl = "https://api.musixmatch.com/ws/1.1/token.get".toHttpUrl().newBuilder()
                    .addQueryParameter("app_id", "android-player-v1.0")
                    .addQueryParameter("format", "json")
                    .build()
                val req = Request.Builder().url(tokenUrl).build()
                val res = client.newCall(req).execute()
                val body = res.body?.string() ?: ""
                val json = JSONObject(body)
                userToken = json.optJSONObject("message")?.optJSONObject("body")?.optString("user_token")
            }

            val token = userToken ?: throw Exception("Failed to get Musixmatch token")

            val url = "https://api.musixmatch.com/ws/1.1/macro.subtitles.get".toHttpUrl().newBuilder()
                .addQueryParameter("format", "json")
                .addQueryParameter("q_track", title)
                .addQueryParameter("q_artist", artist)
                .addQueryParameter("user_token", token)
                .addQueryParameter("app_id", "android-player-v1.0")
                .build()

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Dalvik/2.1.0 (Linux; U; Android 11; Pixel 4 Build/RQ3A.210705.001)")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) throw Exception("Failed to fetch from Musixmatch")

            val responseBody = response.body?.string() ?: throw Exception("Empty response body")
            val jsonObject = JSONObject(responseBody)
            val message = jsonObject.optJSONObject("message")
            val body = message?.optJSONObject("body")
            val macroCalls = body?.optJSONObject("macro_calls")

            // Try to get RichSync (Word-level)
            val richSyncJson = macroCalls?.optJSONObject("track.richsync.get")
                ?.optJSONObject("message")
                ?.optJSONObject("body")
                ?.optString("richsync_body", "")

            if (!richSyncJson.isNullOrBlank()) {
                return@runCatching parseRichSyncToEnhancedLrc(richSyncJson)
            }

            // Fallback to normal subtitle (Line-level)
            val subtitleJson = macroCalls?.optJSONObject("track.subtitles.get")
                ?.optJSONObject("message")
                ?.optJSONObject("body")
                ?.optJSONArray("subtitle_list")

            if (subtitleJson != null && subtitleJson.length() > 0) {
                val subtitleBody = subtitleJson.getJSONObject(0)
                    .optJSONObject("subtitle")
                    ?.optString("subtitle_body", "")
                if (!subtitleBody.isNullOrBlank()) return@runCatching subtitleBody
            }

            throw Exception("No lyrics found in Musixmatch")
        }
    }

    private fun parseRichSyncToEnhancedLrc(richSyncJson: String): String {
        val array = JSONArray(richSyncJson)
        val sb = java.lang.StringBuilder()

        for (i in 0 until array.length()) {
            val line = array.getJSONObject(i)
            val ts = line.getDouble("ts")
            val words = line.getJSONArray("l")

            sb.append("[").append(formatTime(ts)).append("] ")

            for (j in 0 until words.length()) {
                val wordObj = words.getJSONObject(j)
                val wordStr = wordObj.getString("c")
                val offset = wordObj.getDouble("o")

                val absoluteTime = ts + offset
                sb.append("<").append(formatTime(absoluteTime)).append(">").append(wordStr)
            }
            sb.append("\n")
        }

        return sb.toString()
    }

    private fun formatTime(seconds: Double): String {
        val totalMs = (seconds * 1000).toLong()
        val min = totalMs / 60000
        val sec = (totalMs % 60000) / 1000
        val ms = (totalMs % 1000) / 10
        return String.format("%02d:%02d.%02d", min, sec, ms)
    }
}