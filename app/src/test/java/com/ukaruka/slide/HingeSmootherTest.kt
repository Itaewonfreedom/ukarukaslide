package com.ukaruka.slide

import org.junit.Assert.*
import org.junit.Test

class HingeSmootherTest {
    @Test fun coarseSamplesProduceIntermediateFramesAndSettle() {
        val s = HingeSmoother()
        s.sample(45f, 1_000_000_000)
        s.sample(60f, 1_100_000_000)
        val values = (1..60).map { s.frame(1_100_000_000L + it * 16_666_667L)!! }
        assertTrue(values.any { it > 45f && it < 60f })
        assertTrue(values.all { it.isFinite() && it in 0f..64f })
        assertEquals(60f, values.last(), 0.03f)
    }
    @Test fun reversalEndpointsAndStaleSamplesStayBounded() {
        val s = HingeSmoother()
        var time = 1_000_000_000L
        for (angle in listOf(0f, 45f, 90f, 180f, 90f, 45f, 0f)) {
            s.sample(angle, time)
            for (i in 1..6) assertTrue(s.frame(time + i * 16_666_667L)!! in 0f..180f)
            time += 100_000_002L
        }
        s.sample(180f, 1L) // old event must not move the target
        repeat(100) { s.frame(time + it * 16_666_667L) }
        assertEquals(0f, s.frame(time + 2_000_000_000L)!!, 0.02f)
        s.reset()
        assertNull(s.frame(time))
        s.sample(Float.NaN, time)
        assertNull(s.frame(time))
    }
}
