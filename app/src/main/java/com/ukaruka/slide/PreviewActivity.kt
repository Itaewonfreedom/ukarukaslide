package com.ukaruka.slide

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.WindowManager

class PreviewActivity : Activity() {
    private lateinit var player: SlideshowPlayerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        player = SlideshowPlayerView(this)
        setContentView(player)
    }

    override fun onStart() {
        super.onStart()
        val store = PhotoSourceStore(this)
        player.start(PhotoRepository(this).loadConfiguredPhotos(store), store.slideIntervalMs)
    }

    override fun onStop() {
        player.stop()
        super.onStop()
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }
}
