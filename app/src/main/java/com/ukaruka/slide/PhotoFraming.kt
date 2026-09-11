package com.ukaruka.slide

import android.content.Context
import android.graphics.*
import android.media.FaceDetector
import android.view.View
import kotlin.math.*
import kotlin.random.Random

data class PreparedPhoto(val photo: SlidePhoto, val bitmap: Bitmap, val faces: RectF?,
    val dx: Boolean = Random.nextBoolean(), val dy: Boolean = Random.nextBoolean(),
    val zoomIn: Boolean = Random.nextBoolean())
data class PhotoViewport(val width: Int, val height: Int)

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
    private val protectFaces: Boolean, previousViewport: PhotoViewport? = null) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val matrix = Matrix()
    private var progress = 0f
    private var path: FramingPath? = null
    private var viewport = previousViewport
    private var anchor: PhotoViewport? = previousViewport
    private var foldReveal: Float? = null
    fun viewportState() = anchor ?: viewport
    fun foldReveal(value: Float?) {
        foldReveal = value
        if (value == null || value >= 1f) anchor = null
        invalidate()
    }
    private val dx = prepared.dx
    private val dy = prepared.dy
    private val zoomIn = prepared.zoomIn

    fun progress(value: Float) { progress = value; invalidate() }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w <= 0 || h <= 0) return
        if (foldReveal != null && anchor == null) anchor = viewport
        viewport = PhotoViewport(w, h)
        val faces = prepared.faces?.let { FaceBounds(it.left, it.top, it.right, it.bottom) }
        path = FramingPath(w.toFloat(), h.toFloat(), prepared.bitmap.width.toFloat(),
            prepared.bitmap.height.toFloat(), protectFaces, faces, dx, dy, zoomIn)
    }

    override fun onDraw(canvas: Canvas) {
        var transform = path?.at(progress) ?: return
        val old = anchor
        val reveal = foldReveal
        if (old != null && reveal != null && reveal < 1f && old.width > 0 && old.height > 0) {
            val faces = prepared.faces?.let { FaceBounds(it.left, it.top, it.right, it.bottom) }
            val from = FramingPath(old.width.toFloat(), old.height.toFloat(),
                prepared.bitmap.width.toFloat(), prepared.bitmap.height.toFloat(), protectFaces,
                faces, dx, dy, zoomIn).at(progress)
            // Keep the left edge and vertical center anchored while the viewport unfolds.
            val y = from.y + (height - old.height) / 2f
            transform = PhotoTransform(from.scale + (transform.scale - from.scale) * reveal,
                from.x + (transform.x - from.x) * reveal, y + (transform.y - y) * reveal)
        }
        matrix.setScale(transform.scale, transform.scale)
        matrix.postTranslate(transform.x, transform.y)
        canvas.drawBitmap(prepared.bitmap, matrix, paint)
    }
}
