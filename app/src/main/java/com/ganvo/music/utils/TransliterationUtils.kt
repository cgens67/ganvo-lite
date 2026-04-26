package com.ganvo.music.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object TransliterationUtils {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun transliterate(text: String): String? = withContext(Dispatchers.IO) {
        try {
            val containsCJK = text.any {
                val block = Character.UnicodeBlock.of(it)
                block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
                        block == Character.UnicodeBlock.HIRAGANA ||
                        block == Character.UnicodeBlock.KATAKANA ||
                        block == Character.UnicodeBlock.HANGUL_SYLLABLES ||
                        block == Character.UnicodeBlock.HANGUL_JAMO ||
                        block == Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO
            }

            if (!containsCJK) return@withContext null

            val encodedText = URLEncoder.encode(text, "UTF-8")
            val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=en&dt=rm&q=$encodedText"

            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) return@withContext null

            val jsonStr = response.body?.string() ?: return@withContext null
            val jsonArray = JSONArray(jsonStr)
            val firstArr = jsonArray.optJSONArray(0) ?: return@withContext null
            val sb = StringBuilder()

            for (i in 0 until firstArr.length()) {
                val innerArr = firstArr.optJSONArray(i)
                if (innerArr != null) {
                    val roman = innerArr.optString(3, "")
                    if (roman.isNotBlank() && roman != "null") {
                        sb.append(roman).append(" ")
                    }
                }
            }
            
            val result = sb.toString().trim()
            return@withContext if (result.isNotEmpty()) result else null
        } catch (e: Exception) {
            null
        }
    }
}