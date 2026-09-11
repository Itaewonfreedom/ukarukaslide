package com.ukaruka.slide

import org.junit.Assert.*
import org.junit.Test

class FoldGeometryTest {
    @Test fun reversibleAndMonotonicWithStableEndpoints() {
        assertEquals(0f, FoldGeometry.reveal(0f), 0f)
        assertEquals(1f, FoldGeometry.reveal(180f), 0f)
        var previous = 0f
        val forward = (0..180).map { FoldGeometry.reveal(it.toFloat()) }
        for (p in forward) { assertTrue(p in 0f..1f && p >= previous); previous = p }
        for (a in 180 downTo 0) assertEquals(forward[a], FoldGeometry.reveal(a.toFloat()), 0f)
    }
    @Test fun projectionNeverInvertsOrEscapesBounds() {
        for (right in listOf(true, false)) for (a in 0..180) {
            val c = FoldGeometry.corners(500f, 1000f, FoldGeometry.reveal(a.toFloat()), right)
            assertTrue(c[1] < c[7] && c[3] < c[5])
            c.forEachIndexed { i, v -> assertTrue(v.isFinite() && v in 0f..(if (i % 2 == 0) 500f else 1000f)) }
        }
    }
    @Test fun invalidAngleFallsBackToFullyVisibleInnerScreen() {
        assertEquals(1f, FoldGeometry.reveal(Float.NaN), 0f)
        assertEquals(1f, FoldGeometry.reveal(Float.POSITIVE_INFINITY), 0f)
    }
}
