package com.ukaruka.slide

import android.content.Context
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
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
            it.animate().cancel()
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

        newView.animate().cancel()
        oldView.animate().cancel()
        newView.setImageDrawable(drawable)
        newView.tag = uri
        newView.alpha = 0f
        newView.scaleX = 1.04f
        newView.scaleY = 1.04f
        newView.translationX = 0f
        newView.translationY = 0f
        newView.bringToFront()
        emptyMessage.bringToFront()

        val direction = if (Random.nextBoolean()) 1f else -1f
        val xShift = width * 0.025f * direction
        val yShift = height * 0.018f * -direction
        newView.animate()
            .alpha(1f)
            .scaleX(1.14f)
            .scaleY(1.14f)
            .translationX(xShift)
            .translationY(yShift)
            .setDuration(intervalMs + FADE_MS)
            .start()

        oldView.animate()
            .alpha(0f)
            .setDuration(FADE_MS)
            .withEndAction { if (oldView !== imageViews[activeIndex]) oldView.setImageDrawable(null) }
            .start()
    }

    private fun createImageView() = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        alpha = 0f
    }

    private fun stopAnimationsOnly() {
        mainHandler.removeCallbacks(nextPhoto)
        imageViews.forEach { it.animate().cancel() }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val MAX_DECODE_EDGE = 2560
        private const val FADE_MS = 1_200L
    }
}
