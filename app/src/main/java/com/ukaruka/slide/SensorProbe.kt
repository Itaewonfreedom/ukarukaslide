package com.ukaruka.slide

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * Registers each candidate vendor sensor on its own and reports whether it delivers events and
 * what its values look like. Samsung guards some of its sensors behind a signature permission, so a
 * sensor that is listed may still refuse registration or stay silent; this makes that visible.
 */
class SensorProbe(private val manager: SensorManager, private val wanted: List<String>) {
    private inner class Entry(val sensor: Sensor) : SensorEventListener {
        var registered = false
        var events = 0
        var last = FloatArray(0)
        var lastAt = 0L
        var intervalMs = 0L
        override fun onSensorChanged(event: SensorEvent) {
            events++
            if (lastAt != 0L) intervalMs = (event.timestamp - lastAt) / 1_000_000
            lastAt = event.timestamp
            last = event.values.copyOf(minOf(event.values.size, 4))
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        val line: String get() {
            val name = sensor.stringType.substringAfterLast('.')
            if (!registered) return "$name: 등록 거부"
            if (events == 0) return "$name: 등록됨 · 이벤트 없음"
            return "$name: ${last.joinToString(", ") { "%.2f".format(it) }} · ${events}건 · ${intervalMs}ms"
        }
    }
    private val entries: List<Entry> = manager.getSensorList(Sensor.TYPE_ALL)
        .filter { s -> wanted.any { s.stringType.endsWith(it) } }.map { Entry(it) }
    private var running = false
    fun start() {
        if (running) return
        running = true
        entries.forEach { it.registered = manager.registerListener(it, it.sensor, SensorManager.SENSOR_DELAY_GAME) }
    }
    fun stop() {
        running = false
        entries.forEach { manager.unregisterListener(it); it.registered = false; it.events = 0; it.lastAt = 0 }
    }
    val report: String get() = if (entries.isEmpty()) "탐침 대상 센서 없음" else entries.joinToString("\n") { it.line }
}
