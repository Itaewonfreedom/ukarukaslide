package com.ukaruka.slide

import android.content.Context

class DisplayStyleStore(context: Context) {
    enum class ClockFont { SANS, SERIF, MONO }
    enum class ClockPosition { TOP_START, TOP_END, BOTTOM_START, BOTTOM_END }

    private val prefs = context.getSharedPreferences("ukaruka_display_style", Context.MODE_PRIVATE)

    val clockEnabled get() = prefs.getBoolean("clock_enabled", true)
    val clockSizeSp get() = prefs.getInt("clock_size", 64).coerceIn(32, 120)
    val clockFont: ClockFont
        get() = enumValue(prefs.getString("clock_font", ClockFont.SANS.name), ClockFont.SANS)
    val clockPosition: ClockPosition
        get() = enumValue(
            prefs.getString("clock_position", ClockPosition.TOP_END.name),
            ClockPosition.TOP_END
        )
    val showDate get() = prefs.getBoolean("show_date", true)
    val showSeconds get() = prefs.getBoolean("show_seconds", false)
    val notificationsEnabled get() = prefs.getBoolean("notifications_enabled", false)
    val bubbleEffectsEnabled get() = prefs.getBoolean("bubble_effects_enabled", true)

    fun setClockEnabled(value: Boolean) = putBoolean("clock_enabled", value)
    fun setClockSizeSp(value: Int) = prefs.edit().putInt("clock_size", value.coerceIn(32, 120)).apply()
    fun setClockFont(value: ClockFont) = putString("clock_font", value.name)
    fun setClockPosition(value: ClockPosition) = putString("clock_position", value.name)
    fun setShowDate(value: Boolean) = putBoolean("show_date", value)
    fun setShowSeconds(value: Boolean) = putBoolean("show_seconds", value)
    fun setNotificationsEnabled(value: Boolean) = putBoolean("notifications_enabled", value)
    fun setBubbleEffectsEnabled(value: Boolean) = putBoolean("bubble_effects_enabled", value)

    private fun putBoolean(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
    private fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()

    private inline fun <reified T : Enum<T>> enumValue(raw: String?, fallback: T): T =
        runCatching { enumValueOf<T>(raw.orEmpty()) }.getOrDefault(fallback)
}
