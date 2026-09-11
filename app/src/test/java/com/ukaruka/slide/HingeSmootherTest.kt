package com.ukaruka.slide

import org.junit.Assert.*
import org.junit.Test

class HingeSmootherTest {
    private val frame = 16_666_667L
    @Test fun coarseSamplesProduceIntermediateFramesAndSettle() {
        val s = HingeSmoother()
        s.sample(45f, 1_000_000_000)
        s.sample(60f, 1_100_000_000)
        assertFalse(s.coarse)
        val values = (1..60).map { s.frame(1_100_000_000L + it * frame)!! }
        assertTrue(values.any { it > 45f && it < 60f })
        assertTrue(values.all { it.isFinite() && it in 0f..64f })
        assertEquals(60f, values.last(), 0.03f)
    }
    @Test fun reversalEndpointsAndStaleSamplesStayBounded() {
        val s = HingeSmoother()
        var time = 1_000_000_000L
        for (angle in listOf(0f, 45f, 90f, 180f, 90f, 45f, 0f)) {
            s.sample(angle, time)
            for (i in 1..6) assertTrue(s.frame(time + i * frame)!! in 0f..180f)
            time += 100_000_002L
        }
        s.sample(180f, 1L) // old event must not move the target
        repeat(100) { s.frame(time + it * frame) }
        assertEquals(0f, s.frame(time + 2_000_000_000L)!!, 0.02f)
        s.reset()
        assertNull(s.frame(time))
        assertFalse(s.coarse)
        s.sample(Float.NaN, time)
        assertNull(s.frame(time))
    }
    @Test fun postureOnlySensorIsTweenedAsMotionWithoutOvershoot() {
        val s = HingeSmoother()
        s.sample(0f, 1_000_000_000)
        assertFalse(s.coarse) // one reading cannot tell a posture sensor from a fine one
        s.sample(90f, 1_200_000_000)
        assertTrue(s.coarse)
        assertEquals(90f, s.step, 0f)
        assertEquals(2, s.levels)
        val values = (1..90).map { s.frame(1_200_000_000L + it * frame)!! }
        // The first frames stay near the old posture and the motion eases in.
        assertTrue(values[0] < 3f)
        assertTrue(values[5] - values[0] < values[15] - values[10])
        // Plenty of distinct intermediate frames rather than a cut.
        assertTrue(values.count { it > 5f && it < 85f } >= 12)
        // No overshoot, monotonic, and settled after 1.5 s.
        var previous = -1f
        for (v in values) { assertTrue(v in 0f..90f); assertTrue(v >= previous - 0.001f); previous = v }
        assertEquals(90f, values.last(), 0.1f)
    }
    @Test fun nextPostureArrivingMidMotionRetargetsWithoutJump() {
        val s = HingeSmoother()
        s.sample(0f, 1_000_000_000)
        s.sample(90f, 1_100_000_000)
        val early = (1..12).map { s.frame(1_100_000_000L + it * frame)!! }
        s.sample(180f, 1_100_000_000L + 12 * frame + 1)
        val late = (13..120).map { s.frame(1_100_000_000L + it * frame)!! }
        val all = early + late
        for (i in 1 until all.size) assertTrue("frame $i jumped ${all[i - 1]} -> ${all[i]}", all[i] - all[i - 1] in 0f..12f)
        assertEquals(180f, all.last(), 0.1f)
    }
    @Test fun fineSensorNeverEntersCoarseModeEvenAfterFastMotion() {
        val s = HingeSmoother()
        var time = 1_000_000_000L
        s.sample(180f, time)
        for (angle in listOf(120f, 60f, 0f)) { time += 50_000_000L; s.sample(angle, time) }
        assertTrue(s.coarse) // only large deltas seen so far
        for (angle in listOf(3f, 6f)) { time += 50_000_000L; s.sample(angle, time) }
        assertFalse(s.coarse) // one fine delta proves a real angle sensor
        assertEquals(3f, s.step, 0f)
    }
}
