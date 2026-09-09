package com.ukaruka.slide

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import kotlin.concurrent.thread

class PreviewActivity : Activity() {
    private lateinit var display: AmbientDisplayView
    private var active = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        display = AmbientDisplayView(this)
        setContentView(display)
    }

    override fun onStart() {
        super.onStart()
        active = true
        val store = PhotoSourceStore(this)
        thread(name = "preview-source-loader") {
            val photos = PhotoRepository(this).loadConfiguredPhotos(store)
            runOnUiThread {
                if (active) display.start(photos, store.slideIntervalMs, store.playbackOrder)
            }
        }
    }

    override fun onStop() {
        active = false
        display.stop()
        super.onStop()
    }

    override fun onDestroy() {
        display.release()
        super.onDestroy()
    }
}
