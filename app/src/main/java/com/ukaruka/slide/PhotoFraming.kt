package com.ukaruka.slide

import android.content.Context
import android.graphics.*
import android.media.FaceDetector
import android.view.View
import kotlin.math.*
import kotlin.random.Random

data class PreparedPhoto(val photo: SlidePhoto, val bitmap: Bitmap, val faces: RectF?)

object FaceFraming {
    @Suppress("DEPRECATION")
    fun detect(bitmap: Bitmap): RectF? {
        val w = minOf(640, bitmap.width).coerceAtLeast(2).let { it - it % 2 }
        val h = (bitmap.height.toFloat() * w / bitmap.width).roundToInt().coerceAtLeast(1)
        val sample = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
        try {
            Canvas(sample).drawBitmap(bitmap, null, Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
            val faces = arrayOfNulls<FaceDetector.Face>(16)
            FaceDetector(w, h, faces.size).findFaces(sample, faces)
            var bounds: RectF? = null
            faces.filterNotNull().filter { it.confidence() >= 0.4f }.forEach { face ->
                val mid = PointF()
                face.getMidPoint(mid)
                val eye = face.eyesDistance()
                val box = RectF(
                    ((mid.x - 1.8f * eye) / w).coerceIn(0f, 1f),
                    ((mid.y - 1.9f * eye) / h).coerceIn(0f, 1f),
                    ((mid.x + 1.8f * eye) / w).coerceIn(0f, 1f),
                    ((mid.y + 2.5f * eye) / h).coerceIn(0f, 1f)
                )
                if (bounds == null) bounds = box else bounds!!.union(box)
            }
            return bounds
        } finally { sample.recycle() }
    }
}

/** A crop path has two safe endpoints. Interpolation never changes direction. */
class FramedPhotoView(context: Context, private val prepared: PreparedPhoto,
    private val protectFaces: Boolean) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val matrix = Matrix()
    private var progress = 0f
    private var start = floatArrayOf(1f, 0f, 0f)
    private var end = start.copyOf()
    private val dx = Random.nextBoolean()
    private val dy = Random.nextBoolean()
    private val zoomIn = Random.nextBoolean()

    fun progress(value: Float) { progress = value; invalidate() }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w <= 0 || h <= 0) return
        val bw = prepared.bitmap.width.toFloat()
        val bh = prepared.bitmap.height.toFloat()
        val fit = min(w / bw, h / bh)
        val cover = max(w / bw, h / bh)
        val faces = if (protectFaces) prepared.faces else null
        val margin = min(w, h) * 0.025f
        val maxScale = if (!protectFaces) cover * 1.08f
            else if (faces == null) fit * 0.96f
            else min(cover * 1.08f, min(
                (w - margin * 2) / (faces.width() * bw).coerceAtLeast(1f),
                (h - margin * 2) / (faces.height() * bh).coerceAtLeast(1f)
            ))
        fun offset(view: Float, size: Float, scale: Float, lo: Float?, hi: Float?, fraction: Float): Float {
            val gap = view - size * scale
            var low = min(0f, gap)
            var high = max(0f, gap)
            if (lo != null && hi != null) {
                low = max(low, margin - lo * size * scale)
                high = min(high, view - margin - hi * size * scale)
            }
            if (low > high) return (low + high) / 2f
            return low + (high - low) * fraction
        }
        fun endpoint(scale: Float, fraction: Float) = floatArrayOf(
            scale,
            offset(w.toFloat(), bw, scale, faces?.left, faces?.right, if (dx) fraction else 1 - fraction),
            offset(h.toFloat(), bh, scale, faces?.top, faces?.bottom, if (dy) fraction else 1 - fraction)
        )
        val smaller = maxScale / 1.035f
        start = endpoint(if (zoomIn) smaller else maxScale, 0.35f)
        end = endpoint(if (zoomIn) maxScale else smaller, 0.65f)
    }

    override fun onDraw(canvas: Canvas) {
        val scale = start[0] + (end[0] - start[0]) * progress
        matrix.setScale(scale, scale)
        matrix.postTranslate(start[1] + (end[1] - start[1]) * progress,
            start[2] + (end[2] - start[2]) * progress)
        canvas.drawBitmap(prepared.bitmap, matrix, paint)
    }
}
