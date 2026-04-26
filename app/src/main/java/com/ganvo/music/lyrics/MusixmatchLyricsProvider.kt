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
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Musixmatch/0.9.4581"

    override fun isEnabled(context: Context) = true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            // 1. Get Guest Token
            if (userToken == null) {
                val tokenUrl = "https://apic-desktop.musixmatch.com/ws/1.1/token.get".toHttpUrl().newBuilder()
                    .addQueryParameter("app_id", "web-desktop-app-v1.0")
                    .build()
                val req = Request.Builder().url(tokenUrl).header("User-Agent", USER_AGENT).build()
                val res = client.newCall(req).execute()
                val body = res.body?.string() ?: ""
                val token = JSONObject(body).optJSONObject("message")?.optJSONObject("body")?.optString("user_token")
                if (token.isNullOrBlank() || token == "null") throw Exception("Failed to get Musixmatch token")
                userToken = token
            }

            val token = userToken ?: throw Exception("Invalid token")

            // 2. Search Track ID
            val searchUrl = "https://apic-desktop.musixmatch.com/ws/1.1/track.search".toHttpUrl().newBuilder()
                .addQueryParameter("q_track", title)
                .addQueryParameter("q_artist", artist)
                .addQueryParameter("s_track_rating", "desc")
                .addQueryParameter("user_token", token)
                .addQueryParameter("app_id", "web-desktop-app-v1.0")
                .build()

            val searchReq = Request.Builder().url(searchUrl).header("User-Agent", USER_AGENT).build()
            val searchRes = client.newCall(searchReq).execute()
            val searchBody = searchRes.body?.string() ?: throw Exception("Empty search response")
            val trackList = JSONObject(searchBody).optJSONObject("message")?.optJSONObject("body")?.optJSONArray("track_list")
            if (trackList == null || trackList.length() == 0) throw Exception("Track not found on Musixmatch")

            val trackId = trackList.getJSONObject(0).optJSONObject("track")?.optString("track_id") ?: throw Exception("Track ID missing")

            // 3. Get RichSync (Word-by-Word)
            val richSyncUrl = "https://apic-desktop.musixmatch.com/ws/1.1/track.richsync.get".toHttpUrl().newBuilder()
                .addQueryParameter("track_id", trackId)
                .addQueryParameter("user_token", token)
                .addQueryParameter("app_id", "web-desktop-app-v1.0")
                .build()

            val rsReq = Request.Builder().url(richSyncUrl).header("User-Agent", USER_AGENT).build()
            val rsRes = client.newCall(rsReq).execute()
            val rsBody = rsRes.body?.string() ?: ""
            val richSyncBody = JSONObject(rsBody).optJSONObject("message")?.optJSONObject("body")?.optJSONObject("richsync")?.optString("richsync_body")

            if (!richSyncBody.isNullOrBlank()) {
                return@runCatching parseRichSyncToEnhancedLrc(richSyncBody)
            }

            // 4. Fallback to standard Subtitles (Line-by-Line)
            val subsUrl = "https://apic-desktop.musixmatch.com/ws/1.1/track.subtitles.get".toHttpUrl().newBuilder()
                .addQueryParameter("track_id", trackId)
                .addQueryParameter("user_token", token)
                .addQueryParameter("app_id", "web-desktop-app-v1.0")
                .build()

            val subsReq = Request.Builder().url(subsUrl).header("User-Agent", USER_AGENT).build()
            val subsRes = client.newCall(subsReq).execute()
            val subsBody = subsRes.body?.string() ?: ""
            val subtitleBody = JSONObject(subsBody).optJSONObject("message")?.optJSONObject("body")?.optJSONArray("subtitle_list")
                ?.optJSONObject(0)?.optJSONObject("subtitle")?.optString("subtitle_body")

            if (!subtitleBody.isNullOrBlank()) return@runCatching subtitleBody

            throw Exception("No lyrics found for track")
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