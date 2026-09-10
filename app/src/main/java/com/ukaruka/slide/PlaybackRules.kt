package com.ukaruka.slide

import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random

/** Pure scheduling rules, shared by the player and regression checks. */
object PlaybackRules {
    fun isNight(minute: Int, start: Int, end: Int): Boolean =
        if (start == end) false
        else if (start < end) minute >= start && minute < end
        else minute >= start || minute < end

    fun sameMoment(a: Long, b: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        a > 0 && b > 0 && kotlin.math.abs(a - b) <= 2 * 60 * 60 * 1000L &&
            Instant.ofEpochMilli(a).atZone(zone).toLocalDate() ==
            Instant.ofEpochMilli(b).atZone(zone).toLocalDate()

    fun <T> memories(
        photos: List<T>, time: (T) -> Long, bucket: (T) -> String,
        reduceBursts: Boolean, reverse: Boolean, random: Random = Random.Default
    ): List<T> {
        val groups = mutableListOf<MutableList<T>>()
        for (photo in photos.sortedBy(time)) {
            val last = groups.lastOrNull()?.lastOrNull()
            if (last == null || !sameMoment(time(last), time(photo))) groups.add(mutableListOf())
            groups.last().add(photo)
        }
        val orderedGroups = if (reverse) groups.asReversed() else groups
        return orderedGroups.flatMap { group ->
            if (!reduceBursts) group.shuffled(random) else {
                // Time alone cannot prove duplicate content. Sample very close shots
                // only in the same album, and allow this option to be disabled.
                val bursts = mutableListOf<MutableList<T>>()
                group.groupBy(bucket).values.forEach { album ->
                    var burst: MutableList<T>? = null
                    for (photo in album) {
                        if (burst == null || time(photo) <= 0 ||
                            time(burst.first()) <= 0 ||
                            time(photo) - time(burst.first()) > 2_000L) {
                            burst = mutableListOf()
                            bursts.add(burst)
                        }
                        burst.add(photo)
                    }
                }
                bursts.map { it.random(random) }.shuffled(random)
            }
        }
    }
}
