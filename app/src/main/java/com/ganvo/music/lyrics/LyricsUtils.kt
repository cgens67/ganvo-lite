package com.ganvo.music.lyrics

import android.text.format.DateUtils
import com.ganvo.music.ui.component.ANIMATE_SCROLL_DURATION

@Suppress("RegExpRedundantEscape")
object LyricsUtils {
    val LINE_REGEX = "((\\[\\d\\d:\\d\\d\\.\\d{2,3}\\] ?)+)(.+)".toRegex()
    val TIME_REGEX = "\\[(\\d\\d):(\\d\\d)\\.(\\d{2,3})\\]".toRegex()
    
    // Extracts word-level tags like `<00:12.34>word `
    val WORD_TIME_REGEX = "<(\\d{2}):(\\d{2})\\.(\\d{2,3})>([^<]*)".toRegex()

    fun parseLyrics(lyrics: String): List<LyricsEntry> =
        lyrics
            .lines()
            .flatMap { line ->
                parseLine(line).orEmpty()
            }.sorted()

    private fun parseLine(line: String): List<LyricsEntry>? {
        if (line.isEmpty()) {
            return null
        }
        val matchResult = LINE_REGEX.matchEntire(line.trim()) ?: return null
        val times = matchResult.groupValues[1]
        val text = matchResult.groupValues[3]
        
        val timeMatchResults = TIME_REGEX.findAll(times).toList()
        if (timeMatchResults.isEmpty()) return null

        val wordMatchResults = WORD_TIME_REGEX.findAll(text).toList()

        // Reconstruct the words with start and end times if Enhanced LRC is detected
        val words = if (wordMatchResults.isNotEmpty()) {
            val result = mutableListOf<WordEntry>()
            
            // Check if there is leading text before the very first word timestamp
            val firstMatch = wordMatchResults.first()
            if (firstMatch.range.first > 0) {
                val leadingText = text.substring(0, firstMatch.range.first)
                
                val lineMin = timeMatchResults.first().groupValues[1].toLong()
                val lineSec = timeMatchResults.first().groupValues[2].toLong()
                val lineMilStr = timeMatchResults.first().groupValues[3]
                var lineMil = lineMilStr.toLong()
                if (lineMilStr.length == 2) lineMil *= 10
                val lineTime = lineMin * DateUtils.MINUTE_IN_MILLIS + lineSec * DateUtils.SECOND_IN_MILLIS + lineMil

                val nextMin = firstMatch.groupValues[1].toLong()
                val nextSec = firstMatch.groupValues[2].toLong()
                val nextMilStr = firstMatch.groupValues[3]
                var nextMil = nextMilStr.toLong()
                if (nextMilStr.length == 2) nextMil *= 10
                val nextTime = nextMin * DateUtils.MINUTE_IN_MILLIS + nextSec * DateUtils.SECOND_IN_MILLIS + nextMil

                result.add(WordEntry(lineTime, nextTime, leadingText))
            }

            // Map all found word boundaries
            wordMatchResults.forEachIndexed { index, wordMatch ->
                val min = wordMatch.groupValues[1].toLong()
                val sec = wordMatch.groupValues[2].toLong()
                val milStr = wordMatch.groupValues[3]
                var mil = milStr.toLong()
                if (milStr.length == 2) mil *= 10
                val startTime = min * DateUtils.MINUTE_IN_MILLIS + sec * DateUtils.SECOND_IN_MILLIS + mil
                val wordText = wordMatch.groupValues[4]

                val endTime = if (index < wordMatchResults.size - 1) {
                    val nextMatch = wordMatchResults[index + 1]
                    val nMin = nextMatch.groupValues[1].toLong()
                    val nSec = nextMatch.groupValues[2].toLong()
                    val nMilStr = nextMatch.groupValues[3]
                    var nMil = nMilStr.toLong()
                    if (nMilStr.length == 2) nMil *= 10
                    nMin * DateUtils.MINUTE_IN_MILLIS + nSec * DateUtils.SECOND_IN_MILLIS + nMil
                } else {
                    startTime + 2000L // Fallback 2 seconds for the last word chunk
                }
                
                result.add(WordEntry(startTime, endTime, wordText))
            }
            result
        } else null

        // Plain text stripped of all `<time>` tags to act as standard fallback
        val plainText = if (words != null) {
            words.joinToString("") { it.text }
        } else {
            text
        }

        return timeMatchResults
            .map { timeMatchResult ->
                val min = timeMatchResult.groupValues[1].toLong()
                val sec = timeMatchResult.groupValues[2].toLong()
                val milString = timeMatchResult.groupValues[3]
                var mil = milString.toLong()
                if (milString.length == 2) {
                    mil *= 10
                }
                val time = min * DateUtils.MINUTE_IN_MILLIS + sec * DateUtils.SECOND_IN_MILLIS + mil
                LyricsEntry(time, plainText, words)
            }.toList()
    }

    fun findCurrentLineIndex(
        lines: List<LyricsEntry>,
        position: Long,
    ): Int {
        for (index in lines.indices) {
            if (lines[index].time >= position + ANIMATE_SCROLL_DURATION) {
                return index - 1
            }
        }
        return lines.lastIndex
    }
}