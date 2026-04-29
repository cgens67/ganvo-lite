package com.ganvo.music.lyrics

import org.json.JSONArray
import org.json.JSONObject

object LyricsUtils {
    const val ANIMATE_SCROLL_DURATION = 300L
    val LINE_REGEX = "((\\[\\d\\d:\\d\\d\\.\\d{2,3}\\] ?)+)(.+)".toRegex()
    val TIME_REGEX = "\\[(\\d\\d):(\\d\\d)\\.(\\d{2,3})\\]".toRegex()

    fun parseLyrics(lyrics: String): List<LyricsEntry> {
        if (lyrics.trim().startsWith("{") || lyrics.trim().startsWith("[")) {
            try {
                return parseJsonLyrics(lyrics)
            } catch (e: Exception) {
                // Fallback to LRC if JSON fails
            }
        }
        
        return lyrics
            .lines()
            .flatMap { line ->
                parseLine(line).orEmpty()
            }.sorted()
    }

    private fun parseJsonLyrics(jsonStr: String): List<LyricsEntry> {
        val list = mutableListOf<LyricsEntry>()
        val json = JSONObject(jsonStr)
        val data = json.optJSONArray("data") ?: return emptyList()

        for (i in 0 until data.length()) {
            val lineObj = data.getJSONObject(i)
            val startTime = (lineObj.getDouble("startTime") * 1000).toLong()
            val text = lineObj.optString("text", "")
            
            val words = mutableListOf<LyricsWord>()
            val wordsArray = lineObj.optJSONArray("words")
            if (wordsArray != null) {
                for (j in 0 until wordsArray.length()) {
                    val w = wordsArray.getJSONObject(j)
                    words.add(LyricsWord(
                        startTime = (w.getDouble("startTime") * 1000).toLong(),
                        endTime = (w.getDouble("endTime") * 1000).toLong(),
                        text = w.getString("text")
                    ))
                }
            }
            list.add(LyricsEntry(startTime, text, null, words))
        }
        return list
    }

    private fun parseLine(line: String): List<LyricsEntry>? {
        if (line.isEmpty()) return null
        val matchResult = LINE_REGEX.matchEntire(line.trim()) ?: return null
        val times = matchResult.groupValues[1]
        val text = matchResult.groupValues[3]
        val timeMatchResults = TIME_REGEX.findAll(times).toList()
        if (timeMatchResults.isEmpty()) return null
        return timeMatchResults.map { timeMatchResult ->
            val min = timeMatchResult.groupValues[1].toLong()
            val sec = timeMatchResult.groupValues[2].toLong()
            val milString = timeMatchResult.groupValues[3]
            var mil = milString.toLong()
            if (milString.length == 2) mil *= 10
            val time = min * 60000 + sec * 1000 + mil
            LyricsEntry(time, text)
        }.toList()
    }

    fun findCurrentLineIndex(lines: List<LyricsEntry>, position: Long): Int {
        for (index in lines.indices) {
            if (lines[index].time >= position + ANIMATE_SCROLL_DURATION) {
                return index - 1
            }
        }
        return lines.lastIndex
    }
}