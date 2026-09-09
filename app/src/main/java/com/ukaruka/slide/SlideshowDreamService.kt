package com.ukaruka.slide

import android.service.dreams.DreamService

class SlideshowDreamService : DreamService() {
    private lateinit var player: SlideshowPlayerView

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = true
        player = SlideshowPlayerView(this)
        setContentView(player)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        val store = PhotoSourceStore(this)
        player.start(PhotoRepository(this).loadConfiguredPhotos(store), store.slideIntervalMs)
    }

    override fun onDreamingStopped() {
        player.stop()
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        player.release()
        super.onDetachedFromWindow()
    }
}
