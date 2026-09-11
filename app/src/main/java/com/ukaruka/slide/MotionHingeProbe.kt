package com.ukaruka.slide

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Last public-sensor candidates for a continuous fold angle once every Samsung angle sensor refuses
 * registration: the main-half gyroscope integrated about each device axis since the last posture
 * step of the platform hinge sensor, and the uncalibrated magnetometer, whose raw magnitude is not
 * re-zeroed by the OS and changes as the magnets in the other half approach. The integrated rotation
 * and field at every posture step are kept so the transition thresholds and the hinge axis can be
 * read off the device. Diagnostic only.
 */
class MotionHingeProbe(private val manager: SensorManager) : SensorEventListener {
    private val gyro = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val magnet = manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED)
        ?: manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val turned = FloatArray(3)
    private val bias = FloatArray(3)
    private var stillSince = 0L
    private var lastGyroAt = 0L
    private var anchor = Float.NaN
    private var fieldMagnitude = Float.NaN
    private var fieldMin = Float.NaN
    private var fieldMax = Float.NaN
    private val transitions = ArrayDeque<String>()
    private var running = false
    fun start() {
        if (running) return
        running = true
        gyro?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        magnet?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }
    fun stop() { manager.unregisterListener(this); running = false; lastGyroAt = 0; turned.fill(0f) }
    /** Record what the gyro and magnet saw at a posture step, then restart the integration. */
    fun anchor(platformAngle: Float) {
        if (platformAngle.isNaN() || platformAngle == anchor) return
        if (!anchor.isNaN()) {
            transitions.addFirst("${anchor.toInt()}→${platformAngle.toInt()}: X ${fmt(turned[0])} Y ${fmt(turned[1])} Z ${fmt(turned[2])} · 자기장 ${fmt(fieldMagnitude)}µT")
            while (transitions.size > 4) transitions.removeLast()
        }
        anchor = platformAngle; turned.fill(0f)
    }
    override fun onSensorChanged(event: SensorEvent) {
        if (!running) return
        when (event.sensor.type) {
            Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED, Sensor.TYPE_MAGNETIC_FIELD -> {
                fieldMagnitude = sqrt(event.values[0] * event.values[0] + event.values[1] * event.values[1] + event.values[2] * event.values[2])
                if (fieldMin.isNaN() || fieldMagnitude < fieldMin) fieldMin = fieldMagnitude
                if (fieldMax.isNaN() || fieldMagnitude > fieldMax) fieldMax = fieldMagnitude
            }
            Sensor.TYPE_GYROSCOPE -> {
                if (lastGyroAt != 0L) {
                    val dt = ((event.timestamp - lastGyroAt) / 1_000_000_000f).coerceIn(0f, 0.1f)
                    val still = (0..2).all { abs(event.values[it] - bias[it]) < 0.03f }
                    if (still) {
                        if (stillSince == 0L) stillSince = event.timestamp
                        // Learn the bias only after a second at rest so a slow fold is not absorbed.
                        if (event.timestamp - stillSince > 1_000_000_000L) for (i in 0..2) bias[i] += (event.values[i] - bias[i]) * 0.02f
                    } else stillSince = 0L
                    for (i in 0..2) turned[i] += Math.toDegrees(((event.values[i] - bias[i]) * dt).toDouble()).toFloat()
                }
                lastGyroAt = event.timestamp
            }
        }
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    val report: String get() {
        if (gyro == null) return "자이로 없음"
        val a = if (anchor.isNaN()) "앵커 없음" else "앵커 ${anchor.toInt()}°"
        val raw = magnet?.type == Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED
        val b = if (fieldMagnitude.isNaN()) "" else " · 자기장${if (raw) "(비보정)" else ""} ${fmt(fieldMagnitude)}µT [${fmt(fieldMin)}–${fmt(fieldMax)}]"
        val now = "자이로 적분 $a · X ${fmt(turned[0])} Y ${fmt(turned[1])} Z ${fmt(turned[2])}$b"
        return if (transitions.isEmpty()) now else now + "\n단계 전환 기록: " + transitions.joinToString(" | ")
    }
    private fun fmt(v: Float) = if (v.isNaN()) "—" else "%.0f".format(v)
}
