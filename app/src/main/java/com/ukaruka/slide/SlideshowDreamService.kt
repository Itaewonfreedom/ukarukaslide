package com.ukaruka.slide

import android.service.dreams.DreamService
import android.os.Handler
import android.os.Looper
import kotlin.concurrent.thread

class SlideshowDreamService : DreamService() {
    private lateinit var display: AmbientDisplayView
    @Volatile private var dreamingStarted = false
    private var nightMode: NightModeController? = null
    private var hinge: HingeMonitor? = null
    private var savedPlayback: SlideshowPlayerView.Snapshot? = null
    private var loaded = false
    private var attachmentGeneration = 0

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = true
        isFullscreen = true
        isScreenBright = true
        display = AmbientDisplayView(this)
        attachmentGeneration++
        loaded = false
        val surface = FoldSurface(this).apply {
            inner = resources.configuration.smallestScreenWidthDp >= 600
            addView(display, android.widget.FrameLayout.LayoutParams(-1, -1))
        }
        setContentView(surface)
        if (PlaybackPreferences(this).foldEffect) hinge = HingeMonitor(this) {
            surface.inner = resources.configuration.smallestScreenWidthDp >= 600
            surface.angle = it
        }
        window?.let { nightMode = NightModeController(this, it, display) }
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        dreamingStarted = true
        nightMode?.start()
        hinge?.start()
        savedPlayback?.let {
            display.restore(it); savedPlayback = null; loaded = true
        }
        if (loaded) { display.resumePlayback(); return }
        loaded = true
        val token = attachmentGeneration
        val store = PhotoSourceStore(this)
        thread(name = "dream-source-loader") {
            val photos = runCatching { PhotoRepository(this).loadConfiguredPhotos(store) }.getOrDefault(emptyList())
            Handler(Looper.getMainLooper()).post {
                if (token == attachmentGeneration) {
                    display.start(photos, store.slideIntervalMs, store.playbackOrder)
                    if (dreamingStarted) display.resumePlayback() else display.suspendPlayback()
                }
            }
        }
    }

    override fun onDreamingStopped() {
        dreamingStarted = false
        savedPlayback = display.snapshot().takeIf { it.all.isNotEmpty() }
        display.suspendPlayback()
        hinge?.stop()
        nightMode?.stop()
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        dreamingStarted = false
        if (savedPlayback == null) savedPlayback = display.snapshot().takeIf { it.all.isNotEmpty() }
        attachmentGeneration++
        hinge?.stop(); hinge = null
        nightMode?.stop()
        display.release()
        super.onDetachedFromWindow()
    }
}
