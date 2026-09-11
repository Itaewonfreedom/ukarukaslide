package com.ukaruka.slide

import kotlin.math.abs
import kotlin.math.exp

/**
 * Turns hinge samples into one angle per frame.
 *
 * Fine sensors (about 1° steps) get a bounded short prediction followed by a frame-rate-independent
 * low-pass filter. Sensors that only step between a few postures – Galaxy Z Fold reports 0/90/180 to
 * third-party apps – are recognised from their coarse deltas and driven through a critically damped
 * spring instead, so a posture change plays as a fold motion rather than a cut. The spring retargets
 * smoothly when the next posture arrives mid-motion and never overshoots the reported angle.
 */
class HingeSmoother {
    private var target: Float? = null
    private var shown = 0f
    private var sampleTime = 0L
    private var frameTime = 0L
    private var velocity = 0f
    private var acceleration = 0f
    private var springVelocity = 0f
    private val seen = LinkedHashSet<Float>()
    var intervalMs = 0L
        private set
    /** Smallest non-zero change between consecutive readings; NaN until two different readings arrived. */
    var step = Float.NaN
        private set
    /** Number of distinct readings so far, capped for display. */
    val levels: Int get() = seen.size
    /** True when the sensor is only stepping between postures instead of tracking the angle. */
    val coarse: Boolean get() = step.isFinite() && step >= COARSE_STEP
    fun sample(angle: Float, time: Long) {
        if (!angle.isFinite() || angle !in 0f..180f || time <= sampleTime) return
        val old = target
        if (old == null) { shown = angle; frameTime = time; springVelocity = 0f }
        else {
            val dt = (time - sampleTime) / 1_000_000_000f
            intervalMs = (time - sampleTime) / 1_000_000
            val delta = abs(angle - old)
            if (delta > 0.01f && !(step <= delta)) step = delta
            val nextVelocity = ((angle - old) / dt).coerceIn(-540f, 540f)
            acceleration = if (velocity * nextVelocity < 0) 0f
                else ((nextVelocity - velocity) / dt).coerceIn(-1200f, 1200f)
            velocity = nextVelocity
        }
        if (seen.size < MAX_LEVELS) seen.add(angle)
        target = angle; sampleTime = time
    }
    fun frame(time: Long): Float? {
        val raw = target ?: return null
        // A display handoff can stall frames; do not turn that gap into a one-frame jump.
        val dt = ((time - frameTime) / 1_000_000_000f).coerceIn(0f, 1f / 30f)
        frameTime = time
        if (coarse) {
            val x = shown - raw
            val v = springVelocity
            val decay = exp(-SPRING_OMEGA * dt)
            val slope = v + SPRING_OMEGA * x
            shown = raw + (x + slope * dt) * decay
            springVelocity = (v - SPRING_OMEGA * slope * dt) * decay
            if (abs(shown - raw) < 0.02f && abs(springVelocity) < 1f) { shown = raw; springVelocity = 0f }
            return shown.coerceIn(0f, 180f)
        }
        springVelocity = 0f
        val age = ((time - sampleTime) / 1_000_000_000f).coerceAtLeast(0f)
        // Do not continue extrapolating when the user holds a partially open hinge.
        val horizon = minOf(age, 0.04f)
        val confidence = (1f - age / 0.12f).coerceIn(0f, 1f)
        val lead = ((velocity * horizon + 0.5f * acceleration * horizon * horizon) * confidence).coerceIn(-4f, 4f)
        val predicted = (raw + if (raw <= 1f || raw >= 179f) 0f else lead).coerceIn(0f, 180f)
        shown += (predicted - shown) * (1f - exp(-dt / 0.045f))
        if (age > 0.12f && abs(shown - raw) < 0.02f) shown = raw
        return shown.coerceIn(0f, 180f)
    }
    fun reset() {
        target = null; sampleTime = 0; frameTime = 0; velocity = 0f; acceleration = 0f
        springVelocity = 0f; intervalMs = 0; step = Float.NaN; seen.clear()
    }
    companion object {
        /** Readings that never move by less than this are posture steps, not angles. */
        const val COARSE_STEP = 20f
        /** Critically damped spring rate: a 90° posture step is 90 % done after about 0.55 s. */
        const val SPRING_OMEGA = 7f
        private const val MAX_LEVELS = 16
    }
}
