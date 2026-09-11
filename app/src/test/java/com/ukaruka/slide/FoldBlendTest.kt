package com.ukaruka.slide

import org.junit.Assert.*
import org.junit.Test

class FoldBlendTest {
    private val bw = 4000f
    private val bh = 3000f
    private val from = PhotoTransform(0.6f, -400f, -100f)   // cover viewport 1200 × 1700
    private val to = PhotoTransform(0.75f, -900f, -300f)    // inner viewport 2200 × 1900
    @Test fun endpointsMatchTheTwoFramingsWithTheCoverCentredVertically() {
        val (fx, fy) = FramingPath.focus(FaceBounds(0.6f, 0.2f, 0.8f, 0.5f))
        val start = FramingPath.foldBlend(from, 1700f, 1900f, to, 0f, fx, fy, bw, bh)
        assertEquals(from.scale, start.scale, 1e-4f)
        assertEquals(from.x, start.x, 1e-2f)
        assertEquals(from.y + 100f, start.y, 1e-2f)
        val end = FramingPath.foldBlend(from, 1700f, 1900f, to, 1f, fx, fy, bw, bh)
        assertEquals(to.scale, end.scale, 1e-4f)
        assertEquals(to.x, end.x, 1e-2f)
        assertEquals(to.y, end.y, 1e-2f)
    }
    @Test fun focusPointTravelsInAStraightLineAtConstantPace() {
        val (fx, fy) = FramingPath.focus(null)
        assertEquals(0.5f, fx, 0f); assertEquals(0.45f, fy, 0f)
        fun screen(t: PhotoTransform) = Pair(t.x + fx * bw * t.scale, t.y + fy * bh * t.scale)
        val (sx0, sy0) = screen(FramingPath.foldBlend(from, 1700f, 1900f, to, 0f, fx, fy, bw, bh))
        val (sx1, sy1) = screen(FramingPath.foldBlend(from, 1700f, 1900f, to, 1f, fx, fy, bw, bh))
        for (i in 0..20) {
            val r = i / 20f
            val (sx, sy) = screen(FramingPath.foldBlend(from, 1700f, 1900f, to, r, fx, fy, bw, bh))
            assertEquals(sx0 + (sx1 - sx0) * r, sx, 0.05f)
            assertEquals(sy0 + (sy1 - sy0) * r, sy, 0.05f)
        }
    }
    @Test fun scaleIsMonotonicAndStaysBetweenTheEndpoints() {
        var previous = 0f
        for (i in 0..50) {
            val s = FramingPath.foldBlend(from, 1700f, 1900f, to, i / 50f, 0.5f, 0.45f, bw, bh).scale
            assertTrue(s >= previous - 1e-6f && s in from.scale..to.scale)
            previous = s
        }
    }
    @Test fun degenerateFacesFallBackToTheDefaultFocus() {
        assertEquals(Pair(0.5f, 0.45f), FramingPath.focus(FaceBounds(0.3f, 0.3f, 0.3f, 0.3f)))
    }
}
