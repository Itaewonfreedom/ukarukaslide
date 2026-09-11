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
    @Test fun brightnessRampsAreMonotonicAndStepFreeAcrossTheAngleRange() {
        assertEquals(0f, FoldGeometry.nearAlpha(0f), 0f); assertEquals(1f, FoldGeometry.nearAlpha(1f), 0f)
        assertEquals(0f, FoldGeometry.farAlpha(0f), 0f); assertEquals(1f, FoldGeometry.farAlpha(1f), 0f)
        var near = 0f; var far = 0f
        for (a in 0..1800) {
            val r = FoldGeometry.reveal(a / 10f)
            val n = FoldGeometry.nearAlpha(r); val f = FoldGeometry.farAlpha(r)
            assertTrue(n >= near && f >= far && n >= f)
            // No 0.1° step ever moves an 8-bit alpha by more than a few levels.
            assertTrue(n - near < 4f / 255f && f - far < 4f / 255f)
            near = n; far = f
        }
        assertEquals(1f, FoldGeometry.nearAlpha(2f), 0f); assertEquals(0f, FoldGeometry.farAlpha(-1f), 0f)
    }
}
