package com.ukaruka.slide

import org.junit.Assert.*
import org.junit.Test

class FramingPathTest {
    @Test fun faceBoundsStayVisibleForEveryFrameAndAspectRatio() {
        val sizes = listOf(1200f to 800f, 800f to 1200f, 600f to 800f)
        val faces = listOf(FaceBounds(0f, 0f, 1f, 1f),
            FaceBounds(0.75f, 0.1f, 0.99f, 0.5f), FaceBounds(0.01f, 0.6f, 0.2f, 0.95f))
        for ((w, h) in sizes) for ((bw, bh) in sizes) for (face in faces)
            for (zoom in listOf(true, false)) {
                val path = FramingPath(w, h, bw, bh, true, face, true, false, zoom)
                var previous = path.at(0f)
                val start = path.at(0f)
                val end = path.at(1f)
                for (i in 1..200) {
                    val t = path.at(i / 200f)
                    assertTrue(face.left * bw * t.scale + t.x >= -0.01f)
                    assertTrue(face.right * bw * t.scale + t.x <= w + 0.01f)
                    assertTrue(face.top * bh * t.scale + t.y >= -0.01f)
                    assertTrue(face.bottom * bh * t.scale + t.y <= h + 0.01f)
                    assertTrue((t.scale - previous.scale) * (end.scale - start.scale) >= 0)
                    assertTrue((t.x - previous.x) * (end.x - start.x) >= -0.001f)
                    assertTrue((t.y - previous.y) * (end.y - start.y) >= -0.001f)
                    previous = t
                }
            }
    }
    @Test fun detectionFallbackPreservesEntirePhoto() {
        val path = FramingPath(1200f, 800f, 800f, 1200f, true, null, false, true, false)
        for (i in 0..100) {
            val t = path.at(i / 100f)
            assertTrue(t.x >= 0 && t.y >= 0)
            assertTrue(t.x + 800 * t.scale <= 1200)
            assertTrue(t.y + 1200 * t.scale <= 800)
        }
    }
}
