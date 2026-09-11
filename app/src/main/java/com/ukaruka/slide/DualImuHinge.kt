package com.ukaruka.slide

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Experimental raw-angle estimate from two accelerometers, one per half of the device.
 *
 * Samsung states that its foldables measure the fold angle with two 6-axis IMUs and a Hall IC. If
 * the second IMU is exposed as a sensor, the angle between the gravity vectors of both halves,
 * projected onto the plane perpendicular to the hinge (device Y axis), is the hinge angle. The
 * mounting offset of the second IMU is learned whenever the platform sensor reports a flat device.
 * The estimate is undefined while gravity runs along the hinge, so it is diagnostic only for now.
 */
class DualImuHinge(private val manager: SensorManager) : SensorEventListener {
    private val accelerometers: List<Sensor> = run {
        val default = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val typed = manager.getSensorList(Sensor.TYPE_ACCELEROMETER).filter { it != default }
        val all = manager.getSensorList(Sensor.TYPE_ALL)
        val typedSub = all.filter { it.stringType.endsWith("accelerometer_sub") }
        val named = all.filter { s ->
            s.type != Sensor.TYPE_ACCELEROMETER && s.name.contains("acc", true) && !s.name.contains("uncal", true) &&
                (s.name.contains("sub", true) || s.name.contains("second", true) || s.name.contains("cover", true))
        }
        (listOfNotNull(default) + typedSub + typed + named).distinct().take(2)
    }
    val available: Boolean get() = accelerometers.size == 2
    private val main = FloatArray(3)
    private val sub = FloatArray(3)
    private var mainAt = 0L
    private var subAt = 0L
    private var offset = Float.NaN
    private var running = false
    /** Hinge angle in degrees when both IMUs see enough gravity across the hinge, else null. */
    var estimate: Float? = null
        private set
    var status = if (available) "IMU 추정 대기 (${accelerometers[1].name})" else "보조 가속도계 없음 · IMU 추정 불가"
        private set
    fun start() {
        if (running || !available) return
        val ok = accelerometers.map { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        running = ok.all { it }
        if (!running) {
            manager.unregisterListener(this)
            status = accelerometers.filterIndexed { i, _ -> !ok[i] }.joinToString(" / ") { "${it.name} 등록 거부" } + " · IMU 추정 불가"
        }
    }
    fun stop() { manager.unregisterListener(this); running = false; estimate = null; mainAt = 0; subAt = 0 }
    /** Learn the second IMU's mounting rotation while the platform sensor says the device is flat. */
    fun calibrateFlat() {
        val d = relative() ?: return
        offset = if (offset.isNaN()) d else offset + wrap(d - offset) * 0.1f
    }
    override fun onSensorChanged(event: SensorEvent) {
        if (!running || event.values.size < 3) return
        val into = when (event.sensor) { accelerometers[0] -> main; accelerometers[1] -> sub; else -> return }
        for (i in 0..2) into[i] += (event.values[i] - into[i]) * 0.25f
        if (into === main) mainAt = event.timestamp else subAt = event.timestamp
        update()
    }
    private fun relative(): Float? {
        if (mainAt == 0L || subAt == 0L || abs(mainAt - subAt) > 300_000_000L) return null
        // Gravity across the hinge must dominate on both halves; along the hinge it says nothing.
        if (across(main) < 2.5f || across(sub) < 2.5f) return null
        return wrap(angle(sub) - angle(main))
    }
    private fun update() {
        val d = relative()
        if (d == null) {
            estimate = null
            status = if (mainAt == 0L || subAt == 0L) "IMU 추정 대기" else "IMU 추정 불가 · 힌지 축이 중력과 나란함"
            return
        }
        val e = (180f - abs(wrap(d - (if (offset.isNaN()) 0f else offset)))).coerceIn(0f, 180f)
        estimate = e
        status = "IMU 추정 ${e.toInt()}°" + (if (offset.isNaN()) " · 미보정(펼친 상태에서 보정됨)" else " · 오프셋 ${offset.toInt()}°") +
            " · 중력 ${"%.1f".format(across(main))}/${"%.1f".format(across(sub))}"
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    private fun across(v: FloatArray) = sqrt(v[0] * v[0] + v[2] * v[2])
    private fun angle(v: FloatArray) = Math.toDegrees(atan2(v[2].toDouble(), v[0].toDouble())).toFloat()
    private fun wrap(a: Float): Float { var r = a % 360f; if (r > 180f) r -= 360f; if (r <= -180f) r += 360f; return r }
}
