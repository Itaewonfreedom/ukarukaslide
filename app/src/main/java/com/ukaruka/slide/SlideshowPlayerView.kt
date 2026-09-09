package com.ukaruka.slide

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
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
    private val viewMotions = mutableMapOf<ImageView, Runnable>()
    private val queue = ArrayDeque<SlidePhoto>()
    private var allPhotos: List<SlidePhoto> = emptyList()
    private var activeIndex = 0
    private var running = false
    private var generation = 0
    private var intervalMs = PhotoSourceStore.DEFAULT_INTERVAL_MS
    private var playbackOrder = PhotoSourceStore.PlaybackOrder.RANDOM

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

    fun start(
        photos: List<SlidePhoto>,
        slideIntervalMs: Long,
        order: PhotoSourceStore.PlaybackOrder
    ) {
        stopAnimationsOnly()
        imageViews.forEach {
            it.alpha = 0f
            it.setImageDrawable(null)
            it.tag = null
        }
        activeIndex = 0
        allPhotos = photos.distinctBy { it.uri }
        intervalMs = slideIntervalMs.coerceIn(5_000L, 60_000L)
        playbackOrder = order
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
        val photo = takeNextPhoto() ?: return
        val uri = photo.uri
        val requestGeneration = generation
        decodeExecutor.execute {
            val drawable = decode(uri)
            mainHandler.post {
                if (!running || requestGeneration != generation) return@post
                if (drawable == null) {
                    allPhotos = allPhotos.filterNot { it.uri == uri }
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

    private fun takeNextPhoto(): SlidePhoto? {
        if (allPhotos.isEmpty()) return null
        if (queue.isEmpty()) {
            val last = imageViews[activeIndex].tag as? Uri
            val ordered = when (playbackOrder) {
                PhotoSourceStore.PlaybackOrder.RANDOM -> allPhotos.shuffled()
                PhotoSourceStore.PlaybackOrder.CHRONOLOGICAL -> {
                    locallyShuffled(allPhotos.sortedBy { it.capturedAtMs })
                }
                PhotoSourceStore.PlaybackOrder.REVERSE_CHRONOLOGICAL -> {
                    locallyShuffled(allPhotos.sortedByDescending { it.capturedAtMs })
                }
            }
            val cycle = ordered.toMutableList()
            if (cycle.size > 1 && cycle.first().uri == last) {
                val swapIndex = Random.nextInt(1, minOf(cycle.size, ORDER_WINDOW_SIZE))
                val first = cycle[0]
                cycle[0] = cycle[swapIndex]
                cycle[swapIndex] = first
            }
            cycle.forEach(queue::addLast)
        }
        return queue.pollFirst()
    }

    private fun locallyShuffled(photos: List<SlidePhoto>): List<SlidePhoto> =
        photos.chunked(ORDER_WINDOW_SIZE).flatMap { it.shuffled() }

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
        stopMotion(newView)
        newView.setImageDrawable(drawable)
        newView.tag = uri
        val hasPreviousImage = oldView.drawable != null
        newView.alpha = if (hasPreviousImage) 0f else 1f
        newView.bringToFront()
        emptyMessage.bringToFront()

        val xDirection = if (Random.nextBoolean()) 1f else -1f
        val yDirection = if (Random.nextBoolean()) 1f else -1f
        val xTravel = width.coerceAtLeast(1) * 0.03f
        val yTravel = height.coerceAtLeast(1) * 0.025f
        val startScale = if (Random.nextBoolean()) 1.08f else 1.16f
        val endScale = if (startScale < 1.12f) 1.16f else 1.08f

        newView.scaleX = startScale
        newView.scaleY = startScale
        newView.translationX = -xTravel * xDirection
        newView.translationY = -yTravel * yDirection

        val startedAt = SystemClock.uptimeMillis()
        val travelTimeMs = (intervalMs + CROSSFADE_MS).toDouble()
        // Monotonic, bounded progress: never reverse or finish while still visible,
        // including when decoding the next photo takes longer than expected.
        val motion = object : Runnable {
            override fun run() {
                if (!running || viewMotions[newView] !== this) return
                val elapsed = (SystemClock.uptimeMillis() - startedAt).toDouble()
                val progress = (elapsed / (elapsed + travelTimeMs)).toFloat()
                val scale = startScale + (endScale - startScale) * progress
                newView.scaleX = scale
                newView.scaleY = scale
                newView.translationX = xTravel * xDirection * (2f * progress - 1f)
                newView.translationY = yTravel * yDirection * (2f * progress - 1f)
                newView.postOnAnimation(this)
            }
        }
        viewMotions[newView] = motion
        newView.postOnAnimation(motion)

        val animations = mutableListOf<Animator>()
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
                            stopMotion(oldView)
                            oldView.alpha = 0f
                            oldView.setImageDrawable(null)
                            oldView.tag = null
                        }
                    }
                })
            }
            animations += fadeIn
        }

        if (animations.isNotEmpty()) AnimatorSet().also { set ->
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
        imageViews.forEach(::stopMotion)
        viewAnimators.values.toList().forEach { it.cancel() }
        viewAnimators.clear()
    }

    private fun stopMotion(view: ImageView) {
        viewMotions.remove(view)?.let { view.removeCallbacks(it) }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val MAX_DECODE_EDGE = 2560
        private const val CROSSFADE_MS = 2_000L
        private const val ORDER_WINDOW_SIZE = 24
    }
}
