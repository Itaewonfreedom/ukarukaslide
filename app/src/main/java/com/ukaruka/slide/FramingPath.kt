package com.ukaruka.slide

import kotlin.math.max
import kotlin.math.min

data class FaceBounds(val left: Float, val top: Float, val right: Float, val bottom: Float)
data class PhotoTransform(val scale: Float, val x: Float, val y: Float)

class FramingPath(
    w: Float, h: Float, bw: Float, bh: Float, protect: Boolean,
    detected: FaceBounds?, dx: Boolean, dy: Boolean, zoomIn: Boolean
) {
    private val start: PhotoTransform
    private val end: PhotoTransform
    init {
        require(w > 0 && h > 0 && bw > 0 && bh > 0)
        val faces = if (protect) detected else null
        val fit = min(w / bw, h / bh)
        val cover = max(w / bw, h / bh)
        val margin = min(w, h) * 0.025f
        val maxScale = if (!protect) cover * 1.08f
            else if (faces == null) fit * 0.96f
            else min(cover * 1.08f, min(
                (w - margin * 2) / ((faces.right - faces.left) * bw).coerceAtLeast(1f),
                (h - margin * 2) / ((faces.bottom - faces.top) * bh).coerceAtLeast(1f)))
        fun offset(view: Float, size: Float, scale: Float, lo: Float?, hi: Float?, fraction: Float): Float {
            val gap = view - size * scale
            var low = min(0f, gap)
            var high = max(0f, gap)
            if (lo != null && hi != null) {
                low = max(low, margin - lo * size * scale)
                high = min(high, view - margin - hi * size * scale)
            }
            return if (low > high) (low + high) / 2 else low + (high - low) * fraction
        }
        fun endpoint(scale: Float, fraction: Float) = PhotoTransform(scale,
            offset(w, bw, scale, faces?.left, faces?.right, if (dx) fraction else 1 - fraction),
            offset(h, bh, scale, faces?.top, faces?.bottom, if (dy) fraction else 1 - fraction))
        val smaller = maxScale / 1.035f
        start = endpoint(if (zoomIn) smaller else maxScale, 0.35f)
        end = endpoint(if (zoomIn) maxScale else smaller, 0.65f)
    }
    fun at(progress: Float): PhotoTransform {
        val p = progress.coerceIn(0f, 1f)
        return PhotoTransform(start.scale + (end.scale - start.scale) * p,
            start.x + (end.x - start.x) * p, start.y + (end.y - start.y) * p)
    }
}
