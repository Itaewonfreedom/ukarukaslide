package com.ukaruka.slide

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.SystemClock
import android.view.Choreographer
import kotlin.math.abs

/**
 * Reads every sensor that describes the hinge and feeds the finest one into [HingeSmoother].
 *
 * The platform TYPE_HINGE_ANGLE sensor is the default. Galaxy Z Fold devices report only 0/90/180
 * through it to third-party apps, so vendor sensors whose name mentions the hinge are also listened
 * to; one is adopted only after it has shown finer steps than the platform sensor while staying
 * consistent with it. Which source drives the transition is visible in [status].
 */
class HingeMonitor(context: Context, private val update: (Float?) -> Unit) : SensorEventListener {
    private class Source(val sensor: Sensor, val platform: Boolean) {
        var last = Float.NaN
        var step = Float.NaN
        var samples = 0
        var trusted = true
        val label: String get() = (if (platform) "기본" else "벤더") + " ${sensor.name}"
        fun observe(angle: Float) {
            if (samples > 0) { val d = abs(angle - last); if (d > 0.01f && !(step <= d)) step = d }
            last = angle; samples++
        }
    }
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sources: List<Source> = run {
        val platform = if (Build.VERSION.SDK_INT >= 30) manager.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE) else null
        val vendor = manager.getSensorList(Sensor.TYPE_ALL).filter { s ->
            s != platform && (s.name.contains("hinge", true) || s.stringType.contains("hinge", true))
        }
        listOfNotNull(platform?.let { Source(it, true) }) + vendor.map { Source(it, false) }
    }
    private var active: Source? = sources.firstOrNull { it.platform } ?: sources.firstOrNull()
    /** One line per sensor for the test overlay: name, declared resolution and observed step. */
    val inventory: String get() = if (sources.isEmpty()) "힌지 센서 없음" else sources.joinToString(" / ") { s ->
        val observed = if (s.step.isFinite()) "관측 ${fmt(s.step)}°" else "관측 대기"
        "${s.label} (해상도 ${fmt(s.sensor.resolution)}° · $observed${if (s === active) " · 사용 중" else ""})"
    }
    var status = if (sources.isEmpty()) "힌지 각도 센서 미지원" else "힌지 센서 대기"
        private set
    /** Every sensor on the device, for finding a hidden angle or second IMU on the test screen. */
    fun sensorDump(): String = manager.getSensorList(Sensor.TYPE_ALL).joinToString("\n") { s ->
        "${s.name} · type ${s.type} ${s.stringType} · res ${fmt(s.resolution)} · max ${fmt(s.maximumRange)} · ${s.vendor}"
    }
    /** Last raw reading of the driving sensor, for calibrating the IMU estimate. */
    var rawAngle = Float.NaN
        private set
    /** True once the driving sensor has proven to step between postures only. */
    val coarse: Boolean get() = smoother.coarse
    private var running = false
    private val smoother = HingeSmoother()
    private val choreographer = Choreographer.getInstance()
    private val frame = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            update(smoother.frame(SystemClock.elapsedRealtimeNanos()))
            choreographer.postFrameCallback(this)
        }
    }
    fun start() {
        if (running) return
        running = sources.map { manager.registerListener(this, it.sensor, SensorManager.SENSOR_DELAY_GAME) }.any { it }
        if (running) choreographer.postFrameCallback(frame)
        else { status = "힌지 각도를 읽을 수 없음 · 일반 재생"; update(null) }
    }
    fun stop() {
        manager.unregisterListener(this); running = false
        choreographer.removeFrameCallback(frame); smoother.reset(); rawAngle = Float.NaN
        sources.forEach { it.last = Float.NaN; it.step = Float.NaN; it.samples = 0; it.trusted = true }
        active = sources.firstOrNull { it.platform } ?: sources.firstOrNull()
        update(null)
    }
    override fun onSensorChanged(event: SensorEvent) {
        if (!running) return
        val source = sources.firstOrNull { it.sensor == event.sensor } ?: return
        val angle = event.values.firstOrNull()?.takeIf { it.isFinite() && it in 0f..180f } ?: return
        source.observe(angle)
        val platform = sources.firstOrNull { it.platform }
        // A vendor reading far from the platform posture is not an angle in degrees; never adopt it.
        if (!source.platform && platform != null && platform.samples > 0 && abs(angle - platform.last) > 60f) source.trusted = false
        val current = active ?: return
        val platformFine = platform?.step?.let { it < HingeSmoother.COARSE_STEP } ?: false
        val finerVendor = !source.platform && source.trusted && source.samples >= 3 &&
            source.step < HingeSmoother.COARSE_STEP && !platformFine
        if (current.platform && finerVendor) active = source
        else if (!current.trusted && platform != null) active = platform
        if (active !== source) return
        rawAngle = angle
        smoother.sample(angle, event.timestamp)
        val mode = if (smoother.coarse) "${smoother.levels}단계 센서 · 스프링 보간" else "연속 센서"
        status = "${source.label} ${angle.toInt()}° · 간격 ${smoother.intervalMs}ms · $mode"
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    private fun fmt(v: Float) = if (v == v.toInt().toFloat()) v.toInt().toString() else "%.2f".format(v)
}
