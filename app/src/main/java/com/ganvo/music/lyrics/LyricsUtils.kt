package com.ganvo.music.lyrics

import android.text.format.DateUtils

object LyricsUtils {
    const val ANIMATE_SCROLL_DURATION = 300L
    val LINE_REGEX = "((\\[\\d\\d:\\d\\d\\.\\d{2,3}\\] ?)+)(.+)".toRegex()
    val TIME_REGEX = "\\[(\\d\\d):(\\d\\d)\\.(\\d{2,3})\\]".toRegex()
    
    // Format: <mm:ss.xx>word
    val WORD_REGEX = "<(\\d{2}):(\\d{2})\\.(\\d{2,3})>([^<]*)".toRegex()

    fun parseLyrics(lyrics: String): List<LyricsEntry> =
        lyrics
            .lines()
            .flatMap { line ->
                parseLine(line).orEmpty()
            }.sorted()

    private fun parseLine(line: String): List<LyricsEntry>? {
        if (line.isEmpty()) return null
        val matchResult = LINE_REGEX.matchEntire(line.trim()) ?: return null
        val times = matchResult.groupValues[1]
        val rawText = matchResult.groupValues[3]

        val timeMatchResults = TIME_REGEX.findAll(times).toList()
        if (timeMatchResults.isEmpty()) return null

        // Parse words for word-by-word sync
        val wordMatchResults = WORD_REGEX.findAll(rawText).toList()
        val words = mutableListOf<LyricsWord>()
        var cleanText = rawText

        if (wordMatchResults.isNotEmpty()) {
            val sb = java.lang.StringBuilder()
            for (wordMatch in wordMatchResults) {
                val wMin = wordMatch.groupValues[1].toLong()
                val wSec = wordMatch.groupValues[2].toLong()
                val wMilStr = wordMatch.groupValues[3]
                var wMil = wMilStr.toLong()
                if (wMilStr.length == 2) wMil *= 10
                
                val wTime = wMin * 60000 + wSec * 1000 + wMil
                val wText = wordMatch.groupValues[4]
                
                val cleanWText = wText.replace("&apos;", "'").replace("&quot;", "\"")
                words.add(LyricsWord(wTime, cleanWText))
                sb.append(cleanWText)
            }
            cleanText = sb.toString()
        } else {
            // If it's just plain text, decode it anyway
            cleanText = cleanText.replace("&apos;", "'").replace("&quot;", "\"")
        }

        return timeMatchResults.map { timeMatchResult ->
            val min = timeMatchResult.groupValues[1].toLong()
            val sec = timeMatchResult.groupValues[2].toLong()
            val milString = timeMatchResult.groupValues[3]
            var mil = milString.toLong()
            if (milString.length == 2) mil *= 10
            val time = min * 60000 + sec * 1000 + mil
            LyricsEntry(time, cleanText, null, words)
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