package com.ukaruka.slide

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Context
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import java.util.ArrayDeque
import java.util.concurrent.Executors
import kotlin.math.roundToInt
import kotlin.random.Random

class SlideshowPlayerView(context: Context) : FrameLayout(context) {
    private val imageViews = listOf(createImageView(), createImageView())
    private val emptyMessage = TextView(context).apply {
        text = "표시할 사진이 없습니다.\nUkaruka Slide 앱에서 앨범을 선택해 주세요."
        setTextColor(Color.WHITE)
        textSize = 18f
        gravity = Gravity.CENTER
        setPadding(dp(32), dp(32), dp(32), dp(32))
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val decodeExecutor = Executors.newSingleThreadExecutor()
    private val viewAnimators = mutableMapOf<ImageView, AnimatorSet>()
    private val queue = ArrayDeque<Uri>()
    private var allPhotos: List<Uri> = emptyList()
    private var activeIndex = 0
    private var running = false
    private var generation = 0
    private var intervalMs = PhotoSourceStore.DEFAULT_INTERVAL_MS

    private val nextPhoto = object : Runnable {
        override fun run() {
            if (!running) return
            showNext()
        }
    }

    init {
        setBackgroundColor(Color.BLACK)
        imageViews.forEach { addView(it, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)) }
        addView(emptyMessage, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun start(photos: List<Uri>, slideIntervalMs: Long) {
        stopAnimationsOnly()
        imageViews.forEach {
            it.alpha = 0f
            it.setImageDrawable(null)
            it.tag = null
        }
        activeIndex = 0
        allPhotos = photos.distinct()
        intervalMs = slideIntervalMs.coerceIn(5_000L, 60_000L)
        queue.clear()
        running = true
        generation++
        emptyMessage.visibility = if (allPhotos.isEmpty()) View.VISIBLE else View.GONE
        if (allPhotos.isNotEmpty()) showNext()
    }

    fun stop() {
        running = false
        generation++
        mainHandler.removeCallbacksAndMessages(null)
        stopAnimationsOnly()
    }

    fun release() {
        stop()
        imageViews.forEach { it.setImageDrawable(null) }
        decodeExecutor.shutdownNow()
    }

    private fun showNext() {
        mainHandler.removeCallbacks(nextPhoto)
        val uri = takeNextUri() ?: return
        val requestGeneration = generation
        decodeExecutor.execute {
            val drawable = decode(uri)
            mainHandler.post {
                if (!running || requestGeneration != generation) return@post
                if (drawable == null) {
                    allPhotos = allPhotos.filterNot { it == uri }
                    if (allPhotos.isEmpty()) {
                        emptyMessage.visibility = View.VISIBLE
                    } else {
                        mainHandler.post(nextPhoto)
                    }
                    return@post
                }
                display(uri, drawable)
                mainHandler.postDelayed(nextPhoto, intervalMs)
            }
        }
    }

    private fun takeNextUri(): Uri? {
        if (allPhotos.isEmpty()) return null
        if (queue.isEmpty()) {
            val last = imageViews[activeIndex].tag as? Uri
            val shuffled = allPhotos.shuffled().toMutableList()
            if (shuffled.size > 1 && shuffled.first() == last) {
                val swapIndex = Random.nextInt(1, shuffled.size)
                val first = shuffled[0]
                shuffled[0] = shuffled[swapIndex]
                shuffled[swapIndex] = first
            }
            shuffled.forEach(queue::addLast)
        }
        return queue.pollFirst()
    }

    private fun decode(uri: Uri): Drawable? = runCatching {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeDrawable(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val width = info.size.width
            val height = info.size.height
            val longest = maxOf(width, height)
            if (longest > MAX_DECODE_EDGE) {
                val ratio = MAX_DECODE_EDGE.toFloat() / longest
                decoder.setTargetSize(
                    (width * ratio).roundToInt().coerceAtLeast(1),
                    (height * ratio).roundToInt().coerceAtLeast(1)
                )
            }
        }
    }.getOrNull()

    private fun display(uri: Uri, drawable: Drawable) {
        val oldView = imageViews[activeIndex]
        val newIndex = 1 - activeIndex
        val newView = imageViews[newIndex]
        activeIndex = newIndex

        viewAnimators.remove(newView)?.cancel()
        newView.setImageDrawable(drawable)
        newView.tag = uri
        val hasPreviousImage = oldView.drawable != null
        newView.alpha = if (hasPreviousImage) 0f else 1f
        newView.bringToFront()
        emptyMessage.bringToFront()

        val xDirection = if (Random.nextBoolean()) 1f else -1f
        val yDirection = if (Random.nextBoolean()) 1f else -1f
        val xTravel = width.coerceAtLeast(resources.displayMetrics.widthPixels) * 0.035f
        val yTravel = height.coerceAtLeast(resources.displayMetrics.heightPixels) * 0.025f
        val startScale = if (Random.nextBoolean()) 1.06f else 1.15f
        val endScale = if (startScale < 1.1f) 1.15f else 1.07f

        newView.scaleX = startScale
        newView.scaleY = startScale
        newView.translationX = -xTravel * xDirection
        newView.translationY = -yTravel * yDirection

        val motion = ObjectAnimator.ofPropertyValuesHolder(
            newView,
            PropertyValuesHolder.ofFloat(View.SCALE_X, startScale, endScale),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, startScale, endScale),
            PropertyValuesHolder.ofFloat(
                View.TRANSLATION_X,
                -xTravel * xDirection,
                xTravel * xDirection
            ),
            PropertyValuesHolder.ofFloat(
                View.TRANSLATION_Y,
                -yTravel * yDirection,
                yTravel * yDirection
            )
        ).apply {
            duration = intervalMs + CROSSFADE_MS
            interpolator = LinearInterpolator()
        }

        val animations = mutableListOf<Animator>(motion)
        if (hasPreviousImage) {
            val fadeIn = ObjectAnimator.ofFloat(newView, View.ALPHA, 0f, 1f).apply {
                duration = CROSSFADE_MS
                interpolator = DecelerateInterpolator()
                addListener(object : AnimatorListenerAdapter() {
                    private var cancelled = false

                    override fun onAnimationCancel(animation: Animator) {
                        cancelled = true
                    }

                    override fun onAnimationEnd(animation: Animator) {
                        if (!cancelled && running && oldView !== imageViews[activeIndex]) {
                            viewAnimators.remove(oldView)?.cancel()
                            oldView.alpha = 0f
                            oldView.setImageDrawable(null)
                            oldView.tag = null
                        }
                    }
                })
            }
            animations += fadeIn
        }

        AnimatorSet().also { set ->
            viewAnimators[newView] = set
            set.playTogether(animations)
            set.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (viewAnimators[newView] === set) viewAnimators.remove(newView)
                }
            })
            set.start()
        }
    }

    private fun createImageView() = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        alpha = 0f
    }

    private fun stopAnimationsOnly() {
        mainHandler.removeCallbacks(nextPhoto)
        viewAnimators.values.toList().forEach { it.cancel() }
        viewAnimators.clear()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val MAX_DECODE_EDGE = 2560
        private const val CROSSFADE_MS = 2_000L
    }
}
