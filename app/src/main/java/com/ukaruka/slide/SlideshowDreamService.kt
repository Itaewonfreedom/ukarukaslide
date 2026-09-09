package com.ukaruka.slide

import android.service.dreams.DreamService
import android.os.Handler
import android.os.Looper
import kotlin.concurrent.thread

class SlideshowDreamService : DreamService() {
    private lateinit var display: AmbientDisplayView
    @Volatile private var dreamingStarted = false

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = true
        isFullscreen = true
        isScreenBright = true
        display = AmbientDisplayView(this)
        setContentView(display)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        dreamingStarted = true
        val store = PhotoSourceStore(this)
        thread(name = "dream-source-loader") {
            val photos = PhotoRepository(this).loadConfiguredPhotos(store)
            Handler(Looper.getMainLooper()).post {
                if (dreamingStarted) {
                    display.start(photos, store.slideIntervalMs, store.playbackOrder)
                }
            }
        }
    }

    override fun onDreamingStopped() {
        dreamingStarted = false
        display.stop()
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        dreamingStarted = false
        display.release()
        super.onDetachedFromWindow()
    }
}
