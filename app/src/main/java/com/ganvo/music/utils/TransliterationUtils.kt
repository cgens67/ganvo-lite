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
            // Check if text contains CJK characters (Chinese, Japanese, Korean)
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
            
            // Extract the romanized text array
            val firstArr = jsonArray.optJSONArray(0) ?: return@withContext null
            val sb = java.lang.StringBuilder()

            if (firstArr.optJSONArray(0) != null) {
                // Multiline result
                for (i in 0 until firstArr.length()) {
                    val innerArr = firstArr.optJSONArray(i)
                    if (innerArr != null) {
                        for (j in innerArr.length() - 1 downTo 0) {
                            val str = innerArr.optString(j, "")
                            if (str.isNotBlank() && str != "null") {
                                sb.append(str).append("\n")
                                break
                            }
                        }
                    }
                }
            } else {
                // Single line result
                for (i in firstArr.length() - 1 downTo 0) {
                    val str = firstArr.optString(i, "")
                    if (str.isNotBlank() && str != "null") {
                        sb.append(str)
                        break
                    }
                }
            }
            
            val result = sb.toString().trim()
            return@withContext if (result.isNotEmpty()) result else null
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }
}