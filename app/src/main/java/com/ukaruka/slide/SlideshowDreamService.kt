package com.ukaruka.slide

import android.service.dreams.DreamService
import android.os.Handler
import android.os.Looper
import kotlin.concurrent.thread

class SlideshowDreamService : DreamService() {
    private lateinit var display: AmbientDisplayView
    @Volatile private var dreamingStarted = false
    private var nightMode: NightModeController? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = true
        isFullscreen = true
        isScreenBright = true
        display = AmbientDisplayView(this)
        setContentView(display)
        window?.let { nightMode = NightModeController(this, it, display) }
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        dreamingStarted = true
        nightMode?.start()
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
        nightMode?.stop()
        display.stop()
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        dreamingStarted = false
        nightMode?.stop()
        display.release()
        super.onDetachedFromWindow()
    }
}
