package com.ganvo.music.lyrics

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.zip.Inflater

object KugouLyricsProvider : LyricsProvider {
    override val name = "Kugou"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
        
    private val KRC_KEY = byteArrayOf(64, 71, 97, 119, 94, 50, 116, 71, 51, 86, 49, 45, 54, 49, 45, 56, 50)

    override fun isEnabled(context: Context) = true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            // 1. Search song to get Hash
            val searchUrl = "https://msearch.kugou.com/api/v3/search/song".toHttpUrl().newBuilder()
                .addQueryParameter("keyword", "$title $artist")
                .addQueryParameter("page", "1")
                .addQueryParameter("pagesize", "1")
                .build()
                
            val searchReq = Request.Builder().url(searchUrl).build()
            val searchRes = client.newCall(searchReq).execute()
            if (!searchRes.isSuccessful) throw Exception("Failed to search song")
            val searchBody = searchRes.body?.string() ?: throw Exception("Empty search response")
            val searchJson = JSONObject(searchBody)
            val infoArray = searchJson.optJSONObject("data")?.optJSONArray("info")
            if (infoArray == null || infoArray.length() == 0) throw Exception("Song not found in Kugou")
            val hash = infoArray.getJSONObject(0).getString("hash")
            
            // 2. Get Lyrics ID and AccessKey
            val infoUrl = "https://krcs.kugou.com/search".toHttpUrl().newBuilder()
                .addQueryParameter("ver", "1")
                .addQueryParameter("man", "yes")
                .addQueryParameter("client", "mobi")
                .addQueryParameter("keyword", "$title $artist")
                .addQueryParameter("duration", (duration * 1000).toString())
                .addQueryParameter("hash", hash)
                .build()
                
            val infoReq = Request.Builder().url(infoUrl).build()
            val infoRes = client.newCall(infoReq).execute()
            if (!infoRes.isSuccessful) throw Exception("Failed to get krc info")
            val infoBody = infoRes.body?.string() ?: throw Exception("Empty info response")
            val infoJson = JSONObject(infoBody)
            val candidates = infoJson.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) throw Exception("KRC not found")
            val candidate = candidates.getJSONObject(0)
            val krcId = candidate.getString("id")
            val accessKey = candidate.getString("accesskey")
            
            // 3. Download KRC
            val dlUrl = "https://krcs.kugou.com/download".toHttpUrl().newBuilder()
                .addQueryParameter("ver", "1")
                .addQueryParameter("client", "mobi")
                .addQueryParameter("id", krcId)
                .addQueryParameter("accesskey", accessKey)
                .addQueryParameter("fmt", "krc")
                .addQueryParameter("charset", "utf8")
                .build()
                
            val dlReq = Request.Builder().url(dlUrl).build()
            val dlRes = client.newCall(dlReq).execute()
            if (!dlRes.isSuccessful) throw Exception("Failed to download krc")
            val dlBody = dlRes.body?.string() ?: throw Exception("Empty download response")
            val dlJson = JSONObject(dlBody)
            val contentBase64 = dlJson.getString("content")
            
            // 4. Decrypt KRC to Enhanced LRC
            decryptKrc(contentBase64)
        }
    }
    
    private fun decryptKrc(base64Content: String): String {
        val decoded = Base64.decode(base64Content, Base64.DEFAULT)
        if (decoded.size < 4 || String(decoded, 0, 4) != "krc1") {
            throw Exception("Invalid KRC format")
        }
        
        val encrypted = decoded.copyOfRange(4, decoded.size)
        for (i in encrypted.indices) {
            encrypted[i] = (encrypted[i].toInt() xor KRC_KEY[i % KRC_KEY.size].toInt()).toByte()
        }
        
        val inflater = Inflater()
        inflater.setInput(encrypted)
        val buffer = ByteArray(1024 * 1024)
        val uncompressedLength = inflater.inflate(buffer)
        inflater.end()
        
        val krcText = String(buffer, 0, uncompressedLength, Charsets.UTF_8)
        return krcToEnhancedLrc(krcText)
    }
    
    private fun formatTime(ms: Long): String {
        val min = ms / 60000
        val sec = (ms % 60000) / 1000
        val hs = (ms % 1000) / 10
        return String.format("%02d:%02d.%02d", min, sec, hs)
    }

    private fun krcToEnhancedLrc(krc: String): String {
        val lineRegex = "\\[(\\d+),(\\d+)\\](.*)".toRegex()
        val wordRegex = "<(\\d+),(\\d+),\\d+>([^<]*)".toRegex()
        
        return krc.lines().mapNotNull { line ->
            val lineMatch = lineRegex.matchEntire(line)
            if (lineMatch != null) {
                val lineStart = lineMatch.groupValues[1].toLong()
                val wordsData = lineMatch.groupValues[3]
                
                val words = wordRegex.findAll(wordsData).joinToString("") { wordMatch ->
                    val wordStartRel = wordMatch.groupValues[1].toLong()
                    val wordText = wordMatch.groupValues[3]
                    val absoluteTime = lineStart + wordStartRel
                    "<$wordText><${formatTime(absoluteTime)}>" // Internal structure representation 
                }.replace("><", "> <") // Space formatting for LRC Enhanced standard 

                // Proper Enhanced LRC mapping
                val formattedWords = wordRegex.findAll(wordsData).joinToString("") { wordMatch ->
                    val wordStartRel = wordMatch.groupValues[1].toLong()
                    val wordText = wordMatch.groupValues[3]
                    val absoluteTime = lineStart + wordStartRel
                    "<${formatTime(absoluteTime)}>$wordText"
                }

                if (formattedWords.isNotEmpty()) "[${formatTime(lineStart)}] $formattedWords" else null
            } else if (line.startsWith("[id:") || line.startsWith("[ar:") || line.startsWith("[ti:") || line.startsWith("[by:") || line.startsWith("[hash:") || line.startsWith("[sign:") || line.startsWith("[qq:") || line.startsWith("[total:") || line.startsWith("[offset:")) {
                 null 
            } else {
                 line 
            }
        }.joinToString("\n")
    }
}