package com.ukaruka.slide

import org.junit.Assert.*
import org.junit.Test

class FramingPathTest {
    @Test fun smallCoverAnchorCannotLeaveInnerScreenUnfilled() {
        for ((w, h) in listOf(2200f to 1800f, 1100f to 1800f, 900f to 2400f)) {
            for (x in listOf(-3000f, 0f, 1000f)) for (y in listOf(-3000f, 0f, 1000f)) {
                val t = PhotoTransform(0.25f, x, y).covering(w, h, 800f, 1200f)
                assertTrue(t.x <= 0f && t.y <= 0f)
                assertTrue(t.x + 800 * t.scale >= w - 0.01f)
                assertTrue(t.y + 1200 * t.scale >= h - 0.01f)
            }
        }
    }
    @Test fun viewportStaysCoveredWithoutReversingForEveryFrameAndAspectRatio() {
        val sizes = listOf(1200f to 800f, 800f to 1200f, 600f to 800f, 300f to 1600f)
        val faces = listOf(null, FaceBounds(0f, 0f, 1f, 1f),
            FaceBounds(0.75f, 0.1f, 0.99f, 0.5f), FaceBounds(0.01f, 0.6f, 0.2f, 0.95f))
        for ((w, h) in sizes) for ((bw, bh) in sizes) for (face in faces)
            for (zoom in listOf(true, false)) for (protect in listOf(true, false))
                for (dx in listOf(true, false)) for (dy in listOf(true, false)) {
                val path = FramingPath(w, h, bw, bh, protect, face, dx, dy, zoom)
                var previous = path.at(0f)
                val start = path.at(0f)
                val end = path.at(1f)
                assertTrue(kotlin.math.abs(end.scale - start.scale) > 0f)
                for (i in 0..200) {
                    val t = path.at(i / 200f)
                    assertTrue(t.x <= 0.01f && t.y <= 0.01f)
                    assertTrue(bw * t.scale + t.x >= w - 0.01f)
                    assertTrue(bh * t.scale + t.y >= h - 0.01f)
                    assertTrue((t.scale - previous.scale) * (end.scale - start.scale) >= 0)
                    assertTrue((t.x - previous.x) * (end.x - start.x) >= -0.001f)
                    assertTrue((t.y - previous.y) * (end.y - start.y) >= -0.001f)
                    previous = t
                }
            }
    }
    @Test fun feasibleFaceStaysVisibleWhileFillingScreen() {
        val face = FaceBounds(0.1f, 0.1f, 0.3f, 0.3f)
        val path = FramingPath(1200f, 800f, 800f, 1200f, true, face, false, true, false)
        for (i in 0..100) {
            val t = path.at(i / 100f)
            assertTrue(face.left * 800 * t.scale + t.x >= 0)
            assertTrue(face.right * 800 * t.scale + t.x <= 1200)
            assertTrue(face.top * 1200 * t.scale + t.y >= 0)
            assertTrue(face.bottom * 1200 * t.scale + t.y <= 800)
        }
    }
}
