package com.ganvo.music.lyrics

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object NeteaseLyricsProvider : LyricsProvider {
    override val name = "Netease"
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).build()

    override fun isEnabled(context: Context) = true

    override suspend fun getLyrics(id: String, title: String, artist: String, duration: Int): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val searchUrl = "http://music.163.com/api/search/get/web".toHttpUrl().newBuilder()
                .addQueryParameter("s", "$title $artist")
                .addQueryParameter("type", "1")
                .addQueryParameter("limit", "1")
                .build()
            
            val req = Request.Builder().url(searchUrl).header("User-Agent", "Mozilla/5.0").build()
            val res = client.newCall(req).execute()
            val body = res.body?.string() ?: throw Exception("Netease search empty")
            
            val trackId = JSONObject(body).optJSONObject("result")?.optJSONArray("songs")?.optJSONObject(0)?.optLong("id")
                ?: throw Exception("Track not found on Netease")
            
            val lyricUrl = "http://music.163.com/api/song/lyric".toHttpUrl().newBuilder()
                .addQueryParameter("id", trackId.toString())
                .addQueryParameter("tv", "-1")
                .addQueryParameter("yrc", "1")
                .build()
                
            val lyrReq = Request.Builder().url(lyricUrl).header("User-Agent", "Mozilla/5.0").build()
            val lyrRes = client.newCall(lyrReq).execute()
            val lyrBody = lyrRes.body?.string() ?: throw Exception("Netease lyrics empty")
            
            val yrc = JSONObject(lyrBody).optJSONObject("yrc")?.optString("lyric")
            if (!yrc.isNullOrBlank() && yrc != "null") {
                return@runCatching parseYrc(yrc)
            }
            
            val lrc = JSONObject(lyrBody).optJSONObject("lrc")?.optString("lyric")
            if (!lrc.isNullOrBlank() && lrc != "null") return@runCatching lrc
            
            throw Exception("No lyrics available")
        }
    }
    
    private fun parseYrc(yrc: String): String {
        // YRC format: [1234,5678](1234,500,0)Word(1734,200,0)Word
        val result = StringBuilder()
        val lineRegex = "\\[(\\d+),(\\d+)\\](.*)".toRegex()
        val wordRegex = "\\((\\d+),(\\d+),\\d+\\)([^\\(]*)".toRegex()
        
        yrc.lines().forEach { line ->
            val lineMatch = lineRegex.matchEntire(line.trim())
            if (lineMatch != null) {
                val lineStartMs = lineMatch.groupValues[1].toLong()
                result.append("[").append(formatTime(lineStartMs)).append("] ")
                
                val wordsStr = lineMatch.groupValues[3]
                val words = wordRegex.findAll(wordsStr)
                words.forEach { wordMatch ->
                    val wordStartMs = wordMatch.groupValues[1].toLong()
                    val wordText = wordMatch.groupValues[3]
                    result.append("<").append(formatTime(wordStartMs)).append(">").append(wordText)
                }
                result.append("\n")
            }
        }
        return result.toString()
    }
    
    private fun formatTime(ms: Long): String {
        val min = ms / 60000
        val sec = (ms % 60000) / 1000
        val millis = (ms % 1000) / 10
        return String.format("%02d:%02d.%02d", min, sec, millis)
    }
}