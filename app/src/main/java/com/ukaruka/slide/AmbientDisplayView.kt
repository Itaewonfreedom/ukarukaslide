package com.ukaruka.slide

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.format.DateFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextClock
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.random.Random

class AmbientDisplayView(context: Context) : FrameLayout(context) {
    private val player = SlideshowPlayerView(context)
    private val style = DisplayStyleStore(context)
    private val clockContainer = LinearLayout(context)
    private val timeView = TextClock(context)
    private val dateView = TextClock(context)
    private val nightCover = View(context).apply {
        setBackgroundColor(Color.BLACK)
        visibility = View.GONE
        isClickable = true
        contentDescription = "야간 검은 화면. 뒤로 가기로 종료할 수 있습니다."
    }
    private val burnInHandler = Handler(Looper.getMainLooper())
    private val burnInShift = object : Runnable {
        override fun run() {
            if (style.clockEnabled) {
                val range = dp(14)
                clockContainer.animate()
                    .translationX(Random.nextInt(-range, range + 1).toFloat())
                    .translationY(Random.nextInt(-range, range + 1).toFloat())
                    .setDuration(12_000L)
                    .start()
            }
            burnInHandler.postDelayed(this, 45_000L)
        }
    }

    init {
        addView(player, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        configureClock()
        burnInHandler.post(burnInShift)
        clockContainer.bringToFront()
        addView(nightCover, LayoutParams(-1, -1))
    }

    fun setNight(enabled: Boolean, black: Boolean) {
        clockContainer.alpha = if (enabled) 0.35f else 1f
        nightCover.visibility = if (black) View.VISIBLE else View.GONE
        player.setNightPaused(black)
    }

    fun start(
        photos: List<SlidePhoto>,
        slideIntervalMs: Long,
        playbackOrder: PhotoSourceStore.PlaybackOrder
    ) {
        player.start(photos, slideIntervalMs, playbackOrder)
        burnInHandler.removeCallbacks(burnInShift)
        burnInHandler.post(burnInShift)
    }

    fun stop() {
        burnInHandler.removeCallbacks(burnInShift)
        clockContainer.animate().cancel()
        player.stop()
    }
    fun release() {
        burnInHandler.removeCallbacks(burnInShift)
        clockContainer.animate().cancel()
        player.release()
    }

    fun snapshot() = player.snapshot()
    fun setFoldReveal(value: Float?) { player.setFoldReveal(value) }
    fun restore(state: SlideshowPlayerView.Snapshot) { player.restore(state) }
    fun suspendPlayback() {
        burnInHandler.removeCallbacks(burnInShift)
        clockContainer.animate().cancel()
        player.suspendPlayback()
    }
    fun resumePlayback() {
        player.resumePlayback()
        burnInHandler.removeCallbacks(burnInShift)
        burnInHandler.post(burnInShift)
    }

    private fun configureClock() {
        clockContainer.orientation = LinearLayout.VERTICAL
        clockContainer.visibility = if (style.clockEnabled) View.VISIBLE else View.GONE
        clockContainer.gravity = when (style.clockPosition) {
            DisplayStyleStore.ClockPosition.TOP_START,
            DisplayStyleStore.ClockPosition.BOTTOM_START -> Gravity.START
            DisplayStyleStore.ClockPosition.TOP_END,
            DisplayStyleStore.ClockPosition.BOTTOM_END -> Gravity.END
        }
        val typeface = when (style.clockFont) {
            DisplayStyleStore.ClockFont.SANS -> Typeface.create("sans-serif-thin", Typeface.NORMAL)
            DisplayStyleStore.ClockFont.SERIF -> Typeface.create("serif", Typeface.NORMAL)
            DisplayStyleStore.ClockFont.MONO -> Typeface.create("monospace", Typeface.NORMAL)
        }
        val is24Hour = DateFormat.is24HourFormat(context)
        timeView.apply {
            format12Hour = if (style.showSeconds) "h:mm:ss" else "h:mm"
            format24Hour = if (style.showSeconds) "HH:mm:ss" else "HH:mm"
            textSize = style.clockSizeSp.toFloat()
            setTextColor(Color.WHITE)
            setShadowLayer(dp(12).toFloat(), 0f, dp(2).toFloat(), Color.argb(160, 0, 0, 0))
            this.typeface = typeface
            gravity = clockContainer.gravity
            contentDescription = if (is24Hour) "현재 시각 24시간제" else "현재 시각 12시간제"
        }
        dateView.apply {
            format12Hour = localizedDatePattern()
            format24Hour = localizedDatePattern()
            textSize = (style.clockSizeSp * 0.28f).coerceAtLeast(14f)
            setTextColor(Color.argb(220, 255, 255, 255))
            setShadowLayer(dp(8).toFloat(), 0f, dp(1).toFloat(), Color.argb(180, 0, 0, 0))
            this.typeface = Typeface.create(typeface, Typeface.NORMAL)
            gravity = clockContainer.gravity
            visibility = if (style.showDate) View.VISIBLE else View.GONE
            letterSpacing = 0.06f
        }
        clockContainer.addView(timeView)
        clockContainer.addView(dateView)
        addView(clockContainer, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = when (style.clockPosition) {
                DisplayStyleStore.ClockPosition.TOP_START -> Gravity.TOP or Gravity.START
                DisplayStyleStore.ClockPosition.TOP_END -> Gravity.TOP or Gravity.END
                DisplayStyleStore.ClockPosition.BOTTOM_START -> Gravity.BOTTOM or Gravity.START
                DisplayStyleStore.ClockPosition.BOTTOM_END -> Gravity.BOTTOM or Gravity.END
            }
            val horizontal = dp(28)
            val vertical = dp(26)
            setMargins(horizontal, vertical, horizontal, vertical)
        })
    }

    private fun localizedDatePattern(): String =
        if (Locale.getDefault().language == Locale.KOREAN.language) "M월 d일 EEEE" else "EEEE, MMM d"

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
}
