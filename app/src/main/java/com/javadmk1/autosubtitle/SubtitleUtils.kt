package com.javadmk1.autosubtitle

/**
 * A single timed subtitle segment. Times are milliseconds from the start of the media.
 */
internal data class Cue(val startMs: Long, val endMs: Long, val text: String)

internal object SubtitleUtils {
    /**
     * Finds the cue active at [timeMs]. Cues must be sorted by startMs.
     * Binary search keeps preview rendering responsive for long subtitle files.
     */
    fun findActiveCueIndex(items: List<Cue>, timeMs: Long): Int {
        var low = 0
        var high = items.lastIndex
        var candidate = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (items[mid].startMs <= timeMs) {
                candidate = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return if (candidate >= 0 && timeMs < items[candidate].endMs) candidate else -1
    }

    fun formatTime(milliseconds: Long): String {
        val safe = milliseconds.coerceAtLeast(0)
        val hours = safe / 3_600_000
        val minutes = (safe % 3_600_000) / 60_000
        val seconds = (safe % 60_000) / 1_000
        val millis = safe % 1_000
        return "%02d:%02d:%02d,%03d".format(java.util.Locale.ROOT, hours, minutes, seconds, millis)
    }
}
