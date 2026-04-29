package com.ganvo.music.lyrics

object LyricsUtils {
    const val ANIMATE_SCROLL_DURATION = 300L
    val LINE_REGEX = "((\\[\\d{2,}:\\d{2}\\.\\d{2,3}\\] ?)+)(.+)".toRegex()
    val TIME_REGEX = "\\[(\\d{2,}):(\\d{2})\\.(\\d{2,3})\\]".toRegex()
    
    // Matchea los tags de palabras de Enhanced LRC: <mm:ss.xxx>Palabra
    val WORD_REGEX = "<(\\d{2,}):(\\d{2})\\.(\\d{2,3})>([^<]*)".toRegex()

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

        val wordMatchResults = WORD_REGEX.findAll(rawText).toList()
        val words = mutableListOf<LyricsWord>()
        var cleanText = rawText

        if (wordMatchResults.isNotEmpty()) {
            val sb = java.lang.StringBuilder()
            wordMatchResults.forEachIndexed { index, wordMatch ->
                val wTime = calculateTimeFromGroups(
                    wordMatch.groupValues[1],
                    wordMatch.groupValues[2],
                    wordMatch.groupValues[3]
                )
                
                val wText = wordMatch.groupValues[4]
                    .replace("&apos;", "'")
                    .replace("&quot;", "\"")
                
                // Estimate word duration as distance to next word, or 2000L if end of line
                val nextWordTime = wordMatchResults.getOrNull(index + 1)?.let {
                    calculateTimeFromGroups(it.groupValues[1], it.groupValues[2], it.groupValues[3])
                } ?: (wTime + 2000L)
                
                words.add(LyricsWord(wTime, (nextWordTime - wTime).coerceAtLeast(0L), wText))
                sb.append(wText)
            }
            cleanText = sb.toString()
        } else {
            // Remueve artefactos de escape si no hay words
            cleanText = cleanText.replace("&apos;", "'").replace("&quot;", "\"")
        }

        return timeMatchResults.map { timeMatchResult ->
            val time = calculateTimeFromGroups(
                timeMatchResult.groupValues[1],
                timeMatchResult.groupValues[2],
                timeMatchResult.groupValues[3]
            )
            LyricsEntry(time, cleanText.trim(), null, words)
        }.toList()
    }
    
    private fun calculateTimeFromGroups(minGroup: String, secGroup: String, milGroup: String): Long {
        val min = minGroup.toLong()
        val sec = secGroup.toLong()
        var mil = milGroup.toLong()
        if (milGroup.length == 2) mil *= 10
        return min * 60000 + sec * 1000 + mil
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