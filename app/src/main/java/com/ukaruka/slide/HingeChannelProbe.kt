package com.ukaruka.slide

import android.hardware.Sensor
import android.hardware.SensorDirectChannel
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.MemoryFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Settles whether TYPE_HINGE_ANGLE can deliver anything finer than posture steps on this device:
 * the same sensor registered at SENSOR_DELAY_FASTEST, its full metadata, and, when supported, a
 * SensorDirectChannel that maps the HAL's own event buffer so a quantisation applied per app in the
 * framework would be bypassed. Every distinct value each path ever produced is listed.
 */
class HingeChannelProbe(private val manager: SensorManager) : SensorEventListener {
    private val hinge: Sensor? = if (Build.VERSION.SDK_INT >= 30) manager.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE) else null
    private val fastestValues = LinkedHashSet<Float>()
    private var fastestEvents = 0
    private var fastestRegistered = false
    private val directValues = LinkedHashSet<Float>()
    private var directLatest = Float.NaN
    private var directEvents = 0
    private var directStatus = "직접 채널 미시도"
    private var memory: MemoryFile? = null
    private var channel: SensorDirectChannel? = null
    private val buffer = ByteArray(EVENT_SIZE * SLOTS)
    private var running = false
    val metadata: String get() {
        val s = hinge ?: return "TYPE_HINGE_ANGLE 없음"
        val direct = s.isDirectChannelTypeSupported(SensorDirectChannel.TYPE_MEMORY_FILE)
        val mode = when (s.reportingMode) {
            Sensor.REPORTING_MODE_CONTINUOUS -> "continuous"; Sensor.REPORTING_MODE_ON_CHANGE -> "on-change"
            Sensor.REPORTING_MODE_ONE_SHOT -> "one-shot"; else -> "special"
        }
        return "type36 메타: ${s.name} · $mode · minDelay ${s.minDelay}µs · maxDelay ${s.maxDelay}µs · fifo ${s.fifoMaxEventCount} · " +
            "wakeup ${s.isWakeUpSensor} · 직접채널 ${if (direct) "지원(rate ${s.highestDirectReportRateLevel})" else "미지원"} · 버전 ${s.version}"
    }
    fun start() {
        if (running) return
        running = true
        val s = hinge ?: return
        fastestRegistered = manager.registerListener(this, s, SensorManager.SENSOR_DELAY_FASTEST)
        openDirect(s)
    }
    private fun openDirect(s: Sensor) {
        if (!s.isDirectChannelTypeSupported(SensorDirectChannel.TYPE_MEMORY_FILE)) { directStatus = "직접 채널 미지원"; return }
        try {
            val file = MemoryFile("hinge-direct", buffer.size)
            val ch = manager.createDirectChannel(file)
            val rate = minOf(s.highestDirectReportRateLevel, SensorDirectChannel.RATE_NORMAL)
            val token = ch.configure(s, rate)
            if (token == 0) { ch.close(); file.close(); directStatus = "직접 채널 configure 실패"; return }
            memory = file; channel = ch
            directStatus = "직접 채널 열림 · rate $rate · token $token"
        } catch (e: Exception) { directStatus = "직접 채널 예외 ${e.javaClass.simpleName}: ${e.message}" }
    }
    /** Scan the shared buffer for the newest hinge event. Called from the overlay refresh. */
    fun poll() {
        val file = memory ?: return
        try { file.readBytes(buffer, 0, 0, buffer.size) } catch (_: Exception) { return }
        val bb = ByteBuffer.wrap(buffer).order(ByteOrder.nativeOrder())
        var bestCounter = -1L
        var best = Float.NaN
        for (slot in 0 until SLOTS) {
            val base = slot * EVENT_SIZE
            if (bb.getInt(base) != EVENT_SIZE || bb.getInt(base + 8) != Sensor.TYPE_HINGE_ANGLE) continue
            val counter = bb.getInt(base + 12).toLong() and 0xFFFFFFFFL
            if (counter > bestCounter) { bestCounter = counter; best = bb.getFloat(base + 24) }
        }
        if (bestCounter < 0) return
        if (bestCounter.toInt() != directEvents) { directEvents = bestCounter.toInt(); directLatest = best; if (directValues.size < 32) directValues.add(best) }
    }
    fun stop() {
        running = false
        manager.unregisterListener(this)
        try { channel?.close() } catch (_: Exception) {}
        try { memory?.close() } catch (_: Exception) {}
        channel = null; memory = null
    }
    override fun onSensorChanged(event: SensorEvent) {
        if (!running || event.sensor.type != Sensor.TYPE_HINGE_ANGLE) return
        fastestEvents++
        if (fastestValues.size < 32) fastestValues.add(event.values[0])
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    val report: String get() {
        if (hinge == null) return "TYPE_HINGE_ANGLE 없음"
        val fast = if (!fastestRegistered) "FASTEST 등록 거부" else "FASTEST ${fastestEvents}건 · 관측값 {${fastestValues.joinToString(", ") { fmt(it) }}}"
        val direct = if (memory == null) directStatus
            else "$directStatus · ${directEvents}건 · 최근 ${fmt(directLatest)} · 관측값 {${directValues.joinToString(", ") { fmt(it) }}}"
        return "$fast\n$direct"
    }
    private fun fmt(v: Float) = if (v.isNaN()) "—" else if (v == v.toInt().toFloat()) v.toInt().toString() else "%.2f".format(v)
    companion object {
        private const val EVENT_SIZE = 104
        private const val SLOTS = 64
    }
}
