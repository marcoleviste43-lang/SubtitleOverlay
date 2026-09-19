package com.subtitleoverlay.app

/**
 * A single subtitle cue: text visible between [startMs] and [endMs],
 * measured in milliseconds from the start of the subtitle track.
 */
data class Cue(
    val startMs: Long,
    val endMs: Long,
    val text: String
)
