package com.ukaruka.slide

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone
import kotlin.random.Random

class PlaybackRulesTest {
    private data class Photo(val id: Int, val time: Long, val album: String = "camera")
    private fun date(day: Int, hour: Int = 12, second: Int = 0) =
        LocalDateTime.of(2026, 9, day, hour, 0, second).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test fun nightHandlesMidnightAndExactBoundaries() {
        assertTrue(PlaybackRules.isNight(22 * 60, 22 * 60, 7 * 60))
        assertTrue(PlaybackRules.isNight(0, 22 * 60, 7 * 60))
        assertFalse(PlaybackRules.isNight(7 * 60, 22 * 60, 7 * 60))
        assertFalse(PlaybackRules.isNight(12 * 60, 22 * 60, 7 * 60))
        assertTrue(PlaybackRules.isNight(600, 540, 1020))
        assertFalse(PlaybackRules.isNight(1020, 540, 1020))
        for (minute in 0..1439) assertFalse(PlaybackRules.isNight(minute, 300, 300))
    }

    @Test fun memoriesKeepDaysOrderedInBothDirectionsWithoutLosingPhotos() {
        val photos = (1..30).map { Photo(it, date(1 + it / 11, 12, it % 11)) }
        for (reverse in listOf(false, true)) {
            val result = PlaybackRules.memories(photos.shuffled(Random(5)), { it.time },
                { it.album }, false, reverse, Random(7))
            assertEquals(photos.map { it.id }.toSet(), result.map { it.id }.toSet())
            assertEquals(photos.size, result.size)
            val days = result.map { java.time.Instant.ofEpochMilli(it.time).atZone(ZoneId.systemDefault()).dayOfMonth }
            assertEquals(if (reverse) days.sortedDescending() else days.sorted(), days)
        }
    }

    @Test fun burstsDoNotMergeDifferentAlbumsOrDistantShots() {
        val photos = listOf(Photo(1, date(1)), Photo(2, date(1, second = 1)),
            Photo(3, date(1, second = 1), "other"), Photo(4, date(1, second = 10)))
        val result = PlaybackRules.memories(photos, { it.time }, { it.album }, true, false, Random(2))
        assertEquals(3, result.size)
        assertEquals(1, result.count { it.id == 1 || it.id == 2 })
        assertTrue(result.any { it.id == 3 })
        assertTrue(result.any { it.id == 4 })
        assertEquals(4, PlaybackRules.memories(photos, { it.time }, { it.album }, false, false).size)
    }

    @Test fun momentSplitsAtDateAndLongGapsAndRejectsMissingDates() {
        assertFalse(PlaybackRules.sameMoment(0, 0))
        assertFalse(PlaybackRules.sameMoment(date(1, 9), date(1, 12)))
        assertFalse(PlaybackRules.sameMoment(date(1, 23) + 59 * 60_000, date(2, 0)))
        assertTrue(PlaybackRules.sameMoment(date(1), date(1, 13)))
    }

    @Test fun nightCoversExactlyScheduledMinutes() {
        for (start in listOf(0, 330, 900, 1320)) for (end in listOf(0, 420, 1100, 1439)) {
            val expected = (end - start + 1440) % 1440
            assertEquals(expected, (0..1439).count { PlaybackRules.isNight(it, start, end) })
        }
    }
}
