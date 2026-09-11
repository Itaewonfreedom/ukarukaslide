package com.ukaruka.slide

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Last public-sensor candidate for a continuous fold angle once every Samsung angle sensor refuses
 * registration: integrate the main-half gyroscope about each device axis since the last posture
 * step of the platform hinge sensor, and watch the magnetometer magnitude, which changes as the
 * magnets in the other half approach. Diagnostic only; which axis follows the hinge, and whether
 * the main IMU sits in the half that moves, is decided on the device.
 */
class MotionHingeProbe(private val manager: SensorManager) : SensorEventListener {
    private val gyro = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val magnet = manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val turned = FloatArray(3)
    private val bias = FloatArray(3)
    private var stillSince = 0L
    private var lastGyroAt = 0L
    private var anchor = Float.NaN
    private var fieldMagnitude = Float.NaN
    private var running = false
    fun start() {
        if (running) return
        running = true
        gyro?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        magnet?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }
    fun stop() { manager.unregisterListener(this); running = false; lastGyroAt = 0; turned.fill(0f) }
    /** Reset the integration whenever the platform sensor steps to a new posture. */
    fun anchor(platformAngle: Float) {
        if (platformAngle.isNaN() || platformAngle == anchor) return
        anchor = platformAngle; turned.fill(0f)
    }
    override fun onSensorChanged(event: SensorEvent) {
        if (!running) return
        when (event.sensor.type) {
            Sensor.TYPE_MAGNETIC_FIELD -> fieldMagnitude = sqrt(event.values[0] * event.values[0] +
                event.values[1] * event.values[1] + event.values[2] * event.values[2])
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
        val b = if (fieldMagnitude.isNaN()) "" else " · 자기장 ${"%.0f".format(fieldMagnitude)}µT"
        return "자이로 적분 $a · X ${"%.0f".format(turned[0])}° Y ${"%.0f".format(turned[1])}° Z ${"%.0f".format(turned[2])}°$b"
    }
}
