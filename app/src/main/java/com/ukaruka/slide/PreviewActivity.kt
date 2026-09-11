package com.ukaruka.slide

import androidx.activity.ComponentActivity
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import kotlin.concurrent.thread

class PreviewActivity : ComponentActivity() {
    private lateinit var display: AmbientDisplayView
    private var active = false
    private var loaded = false
    private var destroyed = false
    private lateinit var fold: FoldController
    private lateinit var nightMode: NightModeController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        display = AmbientDisplayView(this)
        val surface = FoldSurface(this).apply { addView(this@PreviewActivity.display, android.widget.FrameLayout.LayoutParams(-1, -1)) }
        setContentView(surface)
        fold = FoldController(this, surface, display, intent.getBooleanExtra("dual_screen_test", false))
        nightMode = NightModeController(this, window, display)
        val saved = lastCustomNonConfigurationInstance as? SlideshowPlayerView.Snapshot
        if (saved != null && saved.all.isNotEmpty()) {
            display.restore(saved); display.suspendPlayback(); loaded = true
        }
    }

    override fun onStart() {
        super.onStart()
        active = true
        nightMode.start()
        fold.start()
        if (loaded) { display.resumePlayback(); return }
        loaded = true
        val store = PhotoSourceStore(this)
        thread(name = "preview-source-loader") {
            val photos = runCatching { PhotoRepository(applicationContext).loadConfiguredPhotos(store) }.getOrDefault(emptyList())
            runOnUiThread {
                if (!destroyed) {
                    display.start(photos, store.slideIntervalMs, store.playbackOrder)
                    if (!active) display.suspendPlayback() else display.resumePlayback()
                }
            }
        }
    }

    override fun onStop() {
        active = false
        display.suspendPlayback()
        fold.stop()
        nightMode.stop()
        super.onStop()
    }

    override fun onDestroy() {
        destroyed = true
        fold.stop()
        display.release()
        super.onDestroy()
    }
    override fun onRetainCustomNonConfigurationInstance(): Any = display.snapshot()
}
