package com.ukaruka.slide

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.sin
import kotlin.random.Random

class BubbleFieldView(context: Context) : View(context) {
    private data class Bubble(val x: Float, val y: Float, val radius: Float, val speed: Float, val phase: Float)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density
    }
    private val bubbles = List(14) {
        Bubble(
            x = Random.nextFloat(),
            y = Random.nextFloat(),
            radius = 8f + Random.nextFloat() * 34f,
            speed = 0.35f + Random.nextFloat() * 0.9f,
            phase = Random.nextFloat() * 6.28f
        )
    }
    private var progress = 0f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 18_000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            progress = it.animatedValue as Float
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!animator.isStarted) animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        bubbles.forEachIndexed { index, bubble ->
            val drift = sin(progress * 6.28f * bubble.speed + bubble.phase) * width * 0.035f
            val y = ((bubble.y - progress * bubble.speed + 1.5f) % 1.3f) * height
            val alpha = 28 + (index % 4) * 9
            paint.color = Color.argb(alpha, 210, 245, 255)
            canvas.drawCircle(
                bubble.x * width + drift,
                y,
                bubble.radius * resources.displayMetrics.density,
                paint
            )
        }
    }
}
