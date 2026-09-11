package com.ukaruka.slide

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.SystemClock
import android.view.Choreographer

class HingeMonitor(context: Context, private val update: (Float?) -> Unit) : SensorEventListener {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor = if (Build.VERSION.SDK_INT >= 30) manager.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE) else null
    var status = if (sensor == null) "힌지 각도 센서 미지원" else "힌지 센서 대기"
        private set
    private var active = false
    private val smoother = HingeSmoother()
    private val choreographer = Choreographer.getInstance()
    private val frame = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!active) return
            update(smoother.frame(SystemClock.elapsedRealtimeNanos()))
            choreographer.postFrameCallback(this)
        }
    }
    fun start() {
        if (active) return
        active = sensor?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) } == true
        if (active) choreographer.postFrameCallback(frame)
        if (!active) { status = "힌지 각도를 읽을 수 없음 · 일반 재생"; update(null) }
    }
    fun stop() {
        manager.unregisterListener(this); active = false
        choreographer.removeFrameCallback(frame); smoother.reset(); update(null)
    }
    override fun onSensorChanged(event: SensorEvent) {
        if (!active) return
        val angle = event.values.firstOrNull()?.takeIf { it.isFinite() && it in 0f..180f } ?: return
        smoother.sample(angle, event.timestamp)
        status = "센서 ${angle.toInt()}° · 간격 ${smoother.intervalMs}ms"
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
