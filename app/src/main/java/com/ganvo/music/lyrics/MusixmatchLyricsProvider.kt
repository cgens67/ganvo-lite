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
    
    private var token: String? = null
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
        
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"

    override fun isEnabled(context: Context) = true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (token == null) {
                val tokenUrl = "https://apic-desktop.musixmatch.com/ws/1.1/token.get?app_id=web-desktop-app-v1.0"
                val tokenReq = Request.Builder().url(tokenUrl).header("User-Agent", USER_AGENT).build()
                val tokenRes = client.newCall(tokenReq).execute()
                val tokenJson = JSONObject(tokenRes.body?.string() ?: "")
                token = tokenJson.optJSONObject("message")?.optJSONObject("body")?.optString("user_token")
            }

            val currentToken = token ?: throw Exception("Failed to get Musixmatch token")

            val url = "https://apic-desktop.musixmatch.com/ws/1.1/macro.subtitles.get".toHttpUrl().newBuilder()
                .addQueryParameter("format", "json")
                .addQueryParameter("q_track", title)
                .addQueryParameter("q_artist", artist)
                .addQueryParameter("user_token", currentToken)
                .addQueryParameter("app_id", "web-desktop-app-v1.0")
                .build()

            val req = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
            val res = client.newCall(req).execute()
            val json = JSONObject(res.body?.string() ?: "")
            val macro = json.optJSONObject("message")?.optJSONObject("body")?.optJSONObject("macro_calls")
            
            // Richsync (Word-By-Word)
            val richSync = macro?.optJSONObject("track.richsync.get")
                ?.optJSONObject("message")?.optJSONObject("body")
                ?.optJSONObject("richsync")?.optString("richsync_body")

            if (!richSync.isNullOrBlank() && richSync != "null") {
                return@runCatching parseRichSyncToEnhancedLrc(richSync)
            }

            // Fallback (Lines)
            val subtitles = macro?.optJSONObject("track.subtitles.get")
                ?.optJSONObject("message")?.optJSONObject("body")
                ?.optJSONArray("subtitle_list")?.optJSONObject(0)
                ?.optJSONObject("subtitle")?.optString("subtitle_body")
                
            if (!subtitles.isNullOrBlank() && subtitles != "null") {
                return@runCatching subtitles
            }

            throw Exception("No lyrics found on Musixmatch")
        }
    }

    private fun parseRichSyncToEnhancedLrc(json: String): String {
        val array = JSONArray(json)
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
                sb.append("<").append(formatTime(ts + offset)).append(">").append(wordStr)
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