package com.ukaruka.slide

import kotlin.math.abs
import kotlin.math.exp

/** Bounded short prediction followed by a frame-rate-independent low-pass filter. */
class HingeSmoother {
    private var target: Float? = null
    private var shown = 0f
    private var sampleTime = 0L
    private var frameTime = 0L
    private var velocity = 0f
    private var acceleration = 0f
    var intervalMs = 0L
        private set
    fun sample(angle: Float, time: Long) {
        if (!angle.isFinite() || angle !in 0f..180f || time <= sampleTime) return
        val old = target
        if (old == null) { shown = angle; frameTime = time }
        else {
            val dt = (time - sampleTime) / 1_000_000_000f
            intervalMs = (time - sampleTime) / 1_000_000
            val nextVelocity = ((angle - old) / dt).coerceIn(-540f, 540f)
            acceleration = if (velocity * nextVelocity < 0) 0f
                else ((nextVelocity - velocity) / dt).coerceIn(-1200f, 1200f)
            velocity = nextVelocity
        }
        target = angle; sampleTime = time
    }
    fun frame(time: Long): Float? {
        val raw = target ?: return null
        val age = ((time - sampleTime) / 1_000_000_000f).coerceAtLeast(0f)
        // Do not continue extrapolating when the user holds a partially open hinge.
        val horizon = minOf(age, 0.04f)
        val confidence = (1f - age / 0.12f).coerceIn(0f, 1f)
        val lead = ((velocity * horizon + 0.5f * acceleration * horizon * horizon) * confidence).coerceIn(-4f, 4f)
        val predicted = (raw + if (raw <= 1f || raw >= 179f) 0f else lead).coerceIn(0f, 180f)
        val dt = ((time - frameTime) / 1_000_000_000f).coerceIn(0f, 0.1f)
        frameTime = time
        shown += (predicted - shown) * (1f - exp(-dt / 0.045f))
        if (age > 0.12f && abs(shown - raw) < 0.02f) shown = raw
        return shown.coerceIn(0f, 180f)
    }
    fun reset() { target = null; sampleTime = 0; frameTime = 0; velocity = 0f; acceleration = 0f; intervalMs = 0 }
}
