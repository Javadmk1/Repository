package com.javadmk1.autosubtitle

import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleUtilsTest {
    @Test
    fun findsCueAtStartAndBeforeEnd() {
        val cues = listOf(
            Cue(0, 1500, "first"),
            Cue(2000, 3500, "second")
        )
        assertEquals(0, SubtitleUtils.findActiveCueIndex(cues, 0))
        assertEquals(0, SubtitleUtils.findActiveCueIndex(cues, 1499))
        assertEquals(-1, SubtitleUtils.findActiveCueIndex(cues, 1500))
        assertEquals(1, SubtitleUtils.findActiveCueIndex(cues, 2000))
        assertEquals(-1, SubtitleUtils.findActiveCueIndex(cues, 3500))
    }

    @Test
    fun returnsNoCueForEmptyListAndGaps() {
        assertEquals(-1, SubtitleUtils.findActiveCueIndex(emptyList(), 100))
        assertEquals(-1, SubtitleUtils.findActiveCueIndex(listOf(Cue(0, 1000, "one"), Cue(2000, 3000, "two")), 1500))
    }

    @Test
    fun formatsSrtTimestampWithMilliseconds() {
        assertEquals("00:00:00,000", SubtitleUtils.formatTime(0))
        assertEquals("00:01:02,345", SubtitleUtils.formatTime(62_345))
        assertEquals("01:02:03,004", SubtitleUtils.formatTime(3_723_004))
    }

    @Test
    fun clampsNegativeTimeToZero() {
        assertEquals("00:00:00,000", SubtitleUtils.formatTime(-5))
    }
}
