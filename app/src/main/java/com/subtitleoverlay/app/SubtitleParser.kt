package com.subtitleoverlay.app

import java.io.BufferedReader

/**
 * Minimal, dependency-free parser for .srt and .vtt subtitle files.
 * Tolerant of missing SRT index lines, VTT cue identifiers/settings,
 * and both ',' and '.' as the millisecond separator.
 */
object SubtitleParser {

    private val timeRegex = Regex(
        """(\d{1,2}:)?(\d{2}):(\d{2})[.,](\d{1,3})\s*-->\s*(\d{1,2}:)?(\d{2}):(\d{2})[.,](\d{1,3})"""
    )
    private val tagRegex = Regex("""<[^>]*>""")

    fun parse(reader: BufferedReader, isVtt: Boolean): List<Cue> {
        val lines = reader.readLines()
        return if (isVtt) parseVtt(lines) else parseSrt(lines)
    }

    private fun toMillis(hours: String, minutes: String, seconds: String, millis: String): Long {
        val h = hours.trimEnd(':').toLongOrNull() ?: 0L
        val m = minutes.toLong()
        val s = seconds.toLong()
        val ms = millis.padEnd(3, '0').take(3).toLong()
        return h * 3_600_000L + m * 60_000L + s * 1_000L + ms
    }

    private fun rangeFrom(line: String): Pair<Long, Long>? {
        val m = timeRegex.find(line) ?: return null
        val g = m.groupValues
        val start = toMillis(g[1], g[2], g[3], g[4])
        val end = toMillis(g[5], g[6], g[7], g[8])
        return start to end
    }

    private fun collectText(lines: List<String>, from: Int): Pair<String, Int> {
        val textLines = mutableListOf<String>()
        var j = from
        while (j < lines.size && lines[j].trim().isNotEmpty()) {
            textLines.add(lines[j].trim())
            j++
        }
        val text = textLines.joinToString("\n").replace(tagRegex, "").trim()
        return text to j
    }

    private fun parseSrt(lines: List<String>): List<Cue> {
        val cues = mutableListOf<Cue>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) {
                i++
                continue
            }

            var timeLineIndex = i
            // Optional numeric index line preceding the timecode line
            if (line.toIntOrNull() != null &&
                i + 1 < lines.size &&
                timeRegex.containsMatchIn(lines[i + 1])
            ) {
                timeLineIndex = i + 1
            }

            val range = rangeFrom(lines.getOrElse(timeLineIndex) { "" })
            if (range == null) {
                i++
                continue
            }

            val (text, next) = collectText(lines, timeLineIndex + 1)
            if (text.isNotBlank()) {
                cues.add(Cue(range.first, range.second, text))
            }
            i = next + 1
        }
        return cues
    }

    private fun parseVtt(lines: List<String>): List<Cue> {
        val cues = mutableListOf<Cue>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty() || line.startsWith("WEBVTT") ||
                line.startsWith("NOTE") || line.startsWith("STYLE")
            ) {
                i++
                continue
            }

            val timeLineIndex = when {
                timeRegex.containsMatchIn(line) -> i
                i + 1 < lines.size && timeRegex.containsMatchIn(lines[i + 1]) -> i + 1
                else -> -1
            }

            if (timeLineIndex == -1) {
                i++
                continue
            }

            val range = rangeFrom(lines[timeLineIndex])
            if (range == null) {
                i = timeLineIndex + 1
                continue
            }

            val (text, next) = collectText(lines, timeLineIndex + 1)
            if (text.isNotBlank()) {
                cues.add(Cue(range.first, range.second, text))
            }
            i = next + 1
        }
        return cues
    }
}
