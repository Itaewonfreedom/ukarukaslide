package com.ukaruka.slide

import android.content.Context
import android.graphics.*
import android.os.Build
import android.view.View
import android.widget.FrameLayout

/**
 * Draws either the local scene or the very same live scene on a supported rear display.
 *
 * While the device folds, the pane on the hinge's far side is drawn in four passes: a blurred,
 * dimmed copy of the scene as ambient light behind it, the pane itself warped in perspective and
 * defocused toward its far edge, an alpha ramp that is fully opaque at the hinge and dissolves only
 * toward the far edge, and a symmetric shadow in the hinge gutter over both panes. Nothing in the
 * pane changes at the hinge edge, so the pixel left of the hinge always matches the pixel right of it.
 */
class FoldSurface(context: Context, private val mirrorSource: View? = null) : FrameLayout(context) {
    var angle: Float? = null
        set(value) { field = value; invalidate() }
    var inner = true
    var hingeX: Float? = null
    private val transform = Matrix()
    private var liquid: FoldLiquid? = null
    private var liquidFailed = false
    private var glow: RenderNode? = null
    private var glowFailed = false
    private val mask = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    private val shade = Paint()
    private val redraw = object : Runnable {
        override fun run() { invalidate(); if (isAttachedToWindow) postOnAnimation(this) }
    }
    init { setBackgroundColor(Color.BLACK); setWillNotDraw(false) }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (mirrorSource != null) postOnAnimation(redraw)
    }
    override fun onDetachedFromWindow() {
        removeCallbacks(redraw)
        if (Build.VERSION.SDK_INT >= 33) liquid?.release()
        if (Build.VERSION.SDK_INT >= 29) glow?.discardDisplayList()
        liquid = null; glow = null
        super.onDetachedFromWindow()
    }
    override fun dispatchDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0 || h <= 0) return
        fun scene(target: Canvas = canvas) {
            val source = mirrorSource
            if (source == null) super.dispatchDraw(target)
            else if (source.width > 0 && source.height > 0) {
                val save = target.save()
                val scale = maxOf(w / source.width, h / source.height)
                // The cover and left inner pane share a left-edge anchor.
                target.translate(0f, (h - source.height * scale) / 2)
                target.scale(scale, scale)
                source.draw(target)
                target.restoreToCount(save)
            }
        }
        val a = angle
        if (a == null) { scene(); return }
        val reveal = FoldGeometry.reveal(a)
        if (inner && reveal >= 1f) { scene(); return }
        val edge = if (inner) (hingeX ?: (w / 2)).coerceIn(1f, w) else w
        val amount = if (inner) 1 - reveal else reveal
        val paneReveal = if (inner) reveal else 1 - reveal
        if (inner && edge < w) {
            val save = canvas.save()
            canvas.clipRect(edge, 0f, w, h)
            scene()
            canvas.restoreToCount(save)
        }
        // 1. Ambient light: the picture's own colours, blurred wide and dimmed, fill what perspective bares.
        if (amount > 0.001f && Build.VERSION.SDK_INT >= 31 && canvas.isHardwareAccelerated && !glowFailed) {
            try {
                val node = glow ?: RenderNode("fold-glow").also { glow = it }
                node.setPosition(0, 0, width, height)
                val radius = (edge * 0.09f).coerceIn(12f, 120f)
                node.setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP))
                node.alpha = FoldGeometry.glowAlpha(amount)
                val recording = node.beginRecording(width, height)
                try { scene(recording) } finally { node.endRecording() }
                val save = canvas.save()
                canvas.clipRect(0f, 0f, edge, h)
                canvas.drawRenderNode(node)
                canvas.restoreToCount(save)
            } catch (_: RuntimeException) { glowFailed = true }
        }
        // 2. The pane: perspective, defocus and the far-edge dissolve.
        val layer = canvas.saveLayer(0f, 0f, edge, h, null)
        canvas.clipRect(0f, 0f, edge, h)
        val src = floatArrayOf(0f, 0f, edge, 0f, edge, h, 0f, h)
        transform.setPolyToPoly(src, 0, FoldGeometry.corners(edge, h, amount, !inner), 0, 4)
        val warp = canvas.save()
        canvas.concat(transform)
        if (Build.VERSION.SDK_INT >= 33 && canvas.isHardwareAccelerated && !liquidFailed && amount > 0.001f) {
            try {
                val renderer = liquid ?: FoldLiquid().also { liquid = it }
                renderer.draw(canvas, width, height, edge, amount, inner) { scene(it) }
            } catch (_: RuntimeException) { liquidFailed = true; scene() }
        } else scene()
        canvas.restoreToCount(warp)
        val stops = FoldGeometry.alphaStops(paneReveal)
        val colors = IntArray(stops.size) { Color.argb(Math.round(255 * stops[it]).coerceIn(0, 255), 255, 255, 255) }
        val positions = FloatArray(stops.size) { it / (stops.size - 1f) }
        // Stops run from the far edge to the hinge: x = 0 → edge on the inner display, edge → 0 on the cover.
        mask.shader = if (inner) LinearGradient(0f, 0f, edge, 0f, colors, positions, Shader.TileMode.CLAMP)
            else LinearGradient(edge, 0f, 0f, 0f, colors, positions, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, edge, h, mask)
        canvas.restoreToCount(layer)
        // 3. Gutter shadow, mirrored on both panes so the seam stays one continuum.
        if (inner && edge < w && amount > 0.001f) {
            val half = w * FoldGeometry.GUTTER_FRACTION
            val dark = Math.round(255 * FoldGeometry.gutterShadow(amount)).coerceIn(0, 255)
            shade.shader = LinearGradient(edge - half, 0f, edge + half, 0f,
                intArrayOf(0, Color.argb(dark / 3, 0, 0, 0), Color.argb(dark, 0, 0, 0), Color.argb(dark / 3, 0, 0, 0), 0),
                floatArrayOf(0f, 0.3f, 0.5f, 0.7f, 1f), Shader.TileMode.CLAMP)
            canvas.drawRect(edge - half, 0f, edge + half, h, shade)
        }
    }
}
