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
    @Test fun projectionNeverInvertsOrEscapesBoundsAndPinsTheHingeEdge() {
        for (right in listOf(true, false)) for (a in 0..180) {
            val amount = 1 - FoldGeometry.reveal(a.toFloat())
            val c = FoldGeometry.corners(500f, 1000f, amount, right)
            assertTrue(c[1] < c[7] && c[3] < c[5])
            c.forEachIndexed { i, v -> assertTrue(v.isFinite() && v in 0f..(if (i % 2 == 0) 500f else 1000f)) }
            // The hinge edge is the edge that does not move.
            if (right) { assertEquals(0f, c[0], 0f); assertEquals(0f, c[6], 0f); assertEquals(0f, c[1], 0f); assertEquals(1000f, c[7], 0f) }
            else { assertEquals(500f, c[2], 0f); assertEquals(500f, c[4], 0f); assertEquals(0f, c[3], 0f); assertEquals(1000f, c[5], 0f) }
        }
        assertArrayEquals(floatArrayOf(0f, 0f, 500f, 0f, 500f, 1000f, 0f, 1000f), FoldGeometry.corners(500f, 1000f, 0f, false), 0f)
    }
    @Test fun invalidAngleFallsBackToFullyVisibleInnerScreen() {
        assertEquals(1f, FoldGeometry.reveal(Float.NaN), 0f)
        assertEquals(1f, FoldGeometry.reveal(Float.POSITIVE_INFINITY), 0f)
    }
    @Test fun paneIsOpaqueAtTheHingeForEveryAngleAndDissolvesOnlyTowardTheFarEdge() {
        for (a in 0..1800) {
            val r = FoldGeometry.reveal(a / 10f)
            val stops = FoldGeometry.alphaStops(r)
            assertEquals(1f, stops.last(), 0f)
            assertEquals(FoldGeometry.farAlpha(r), stops.first(), 1e-6f)
            for (i in 1 until stops.size) assertTrue(stops[i] >= stops[i - 1] && stops[i] in 0f..1f)
        }
        assertTrue(FoldGeometry.alphaStops(1f).all { it == 1f })
        assertEquals(0f, FoldGeometry.alphaStops(0f).first(), 0f)
    }
    @Test fun lightingTermsVanishWhenFlatAndNeverStepAcrossTheAngleRange() {
        assertEquals(0f, FoldGeometry.glowAlpha(0f), 0f)
        assertEquals(0f, FoldGeometry.gutterShadow(0f), 0f)
        var glow = 0f; var gutter = 0f; var far = 0f
        for (a in 1800 downTo 0) {
            val r = FoldGeometry.reveal(a / 10f)
            val g = FoldGeometry.glowAlpha(1 - r); val s = FoldGeometry.gutterShadow(1 - r); val f = FoldGeometry.farAlpha(r)
            assertTrue(g >= glow && s >= gutter && g in 0f..1f && s in 0f..1f)
            // No 0.1° step moves any 8-bit channel by more than a few levels.
            assertTrue(g - glow < 4f / 255f && s - gutter < 4f / 255f && kotlin.math.abs(f - far) < 4f / 255f || a == 1800)
            glow = g; gutter = s; far = f
        }
    }
}
