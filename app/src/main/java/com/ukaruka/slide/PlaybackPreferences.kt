package com.ukaruka.slide

import android.content.Context

class PlaybackPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("playback_experience", Context.MODE_PRIVATE)
    var foldEffect: Boolean
        get() = prefs.getBoolean("fold_effect", false)
        set(value) { prefs.edit().putBoolean("fold_effect", value).apply() }
    var faceFraming: Boolean
        get() = prefs.getBoolean("face", true)
        set(value) { prefs.edit().putBoolean("face", value).apply() }
    var portraitPairs: Boolean
        get() = prefs.getBoolean("pairs", true)
        set(value) { prefs.edit().putBoolean("pairs", value).apply() }
    var reduceBursts: Boolean
        get() = prefs.getBoolean("bursts", true)
        set(value) { prefs.edit().putBoolean("bursts", value).apply() }
    var nightEnabled: Boolean
        get() = prefs.getBoolean("night", false)
        set(value) { prefs.edit().putBoolean("night", value).apply() }
    var nightBlack: Boolean
        get() = prefs.getBoolean("black", false)
        set(value) { prefs.edit().putBoolean("black", value).apply() }
    var nightStart: Int
        get() = prefs.getInt("start", 22 * 60)
        set(value) { prefs.edit().putInt("start", value.coerceIn(0, 1439)).apply() }
    var nightEnd: Int
        get() = prefs.getInt("end", 7 * 60)
        set(value) { prefs.edit().putInt("end", value.coerceIn(0, 1439)).apply() }
    var nightBrightness: Int
        get() = prefs.getInt("brightness", 15).coerceIn(5, 50)
        set(value) { prefs.edit().putInt("brightness", value.coerceIn(5, 50)).apply() }
    val hidden: Set<String> get() = prefs.getStringSet("hidden", emptySet())!!.toSet()
    fun hide(uri: String) { prefs.edit().putStringSet("hidden", hidden + uri).apply() }
    fun restoreHidden() { prefs.edit().remove("hidden").apply() }
}
