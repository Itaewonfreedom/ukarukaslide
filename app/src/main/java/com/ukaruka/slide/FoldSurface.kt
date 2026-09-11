package com.ukaruka.slide

import android.content.Context
import android.graphics.*
import android.view.View
import android.widget.FrameLayout

/** Draws either the local scene or the very same live scene on a supported rear display. */
class FoldSurface(context: Context, private val mirrorSource: View? = null) : FrameLayout(context) {
    var angle: Float? = null
        set(value) { field = value; invalidate() }
    var inner = true
    var hingeX: Float? = null
    private val transform = Matrix()
    private val mask = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    private val redraw = object : Runnable {
        override fun run() { invalidate(); if (isAttachedToWindow) postOnAnimation(this) }
    }
    init { setBackgroundColor(Color.BLACK); setWillNotDraw(false) }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (mirrorSource != null) postOnAnimation(redraw)
    }
    override fun onDetachedFromWindow() { removeCallbacks(redraw); super.onDetachedFromWindow() }
    override fun dispatchDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0 || h <= 0) return
        fun scene() {
            val source = mirrorSource
            if (source == null) super.dispatchDraw(canvas)
            else if (source.width > 0 && source.height > 0) {
                val save = canvas.save()
                val scale = maxOf(w / source.width, h / source.height)
                // The cover and left inner pane share a left-edge anchor.
                canvas.translate(0f, (h - source.height * scale) / 2)
                canvas.scale(scale, scale)
                source.draw(canvas)
                canvas.restoreToCount(save)
            }
        }
        val a = angle
        if (a == null) { scene(); return }
        val reveal = FoldGeometry.reveal(a)
        if (inner && reveal >= 1f) { scene(); return }
        val edge = if (inner) (hingeX ?: (w / 2)).coerceIn(1f, w) else w
        if (inner && edge < w) {
            val save = canvas.save()
            canvas.clipRect(edge, 0f, w, h)
            scene()
            canvas.restoreToCount(save)
        }
        val layer = canvas.saveLayer(0f, 0f, edge, h, null)
        canvas.clipRect(0f, 0f, edge, h)
        val amount = if (inner) 1 - reveal else reveal
        val src = floatArrayOf(0f, 0f, edge, 0f, edge, h, 0f, h)
        transform.setPolyToPoly(src, 0, FoldGeometry.corners(edge, h, amount, !inner), 0, 4)
        val warp = canvas.save()
        canvas.concat(transform)
        scene()
        canvas.restoreToCount(warp)
        val alpha = if (inner) reveal else 1 - reveal
        val near = (255 * alpha).toInt().coerceIn(0, 255)
        val far = (255 * alpha * alpha).toInt().coerceIn(0, 255)
        mask.shader = LinearGradient(0f, 0f, edge, 0f,
            if (inner) intArrayOf(Color.argb(far,255,255,255), Color.argb(near,255,255,255))
            else intArrayOf(Color.argb(near,255,255,255), Color.argb(far,255,255,255)),
            null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, edge, h, mask)
        canvas.restoreToCount(layer)
    }
}
