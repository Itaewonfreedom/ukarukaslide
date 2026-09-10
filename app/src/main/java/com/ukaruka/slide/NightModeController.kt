package com.ukaruka.slide

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Window
import java.time.LocalTime

class NightModeController(context: Context, private val window: Window,
    private val display: AmbientDisplayView) {
    private val prefs = PlaybackPreferences(context)
    private val handler = Handler(Looper.getMainLooper())
    private var originalBrightness = -1f
    private var active = false
    private val update = object : Runnable {
        override fun run() {
            if (!active) return
            val time = LocalTime.now()
            val night = prefs.nightEnabled && PlaybackRules.isNight(
                time.hour * 60 + time.minute, prefs.nightStart, prefs.nightEnd)
            window.attributes = window.attributes.apply {
                screenBrightness = if (night) prefs.nightBrightness / 100f else originalBrightness
            }
            display.setNight(night, night && prefs.nightBlack)
            handler.postDelayed(this, 15_000)
        }
    }
    fun start() {
        if (active) return
        originalBrightness = window.attributes.screenBrightness
        active = true
        update.run()
    }
    fun stop() {
        if (!active) return
        active = false
        handler.removeCallbacks(update)
        window.attributes = window.attributes.apply { screenBrightness = originalBrightness }
        display.setNight(false, false)
    }
}
