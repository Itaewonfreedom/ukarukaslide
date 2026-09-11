package com.ukaruka.slide

import android.graphics.Canvas
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi

/**
 * Spatially varying defocus, depth shading, a hint of dispersion and a passing sheen on the pane
 * that folds away. Every term is proportional to the distance from the hinge, so the hinge edge is
 * the untouched scene and meets the other pane without a seam.
 */
@RequiresApi(33)
class FoldLiquid {
    private val node = RenderNode("fold-liquid")
    private val shader = RuntimeShader("""
        uniform shader content;
        uniform float2 size;
        uniform float edge;
        uniform float amount;
        uniform float inner;
        half4 tap(float2 q, float2 o, float2 lo, float2 hi) { return content.eval(clamp(q + o, lo, hi)); }
        half4 main(float2 p) {
            float d = clamp(p.x / max(edge, 1.0), 0.0, 1.0);
            d = mix(d, 1.0 - d, inner);                 // 0 at the hinge, 1 at the far edge
            float depth = d * d * amount;
            float r = 34.0 * depth;
            float2 lo = float2(0.5);
            float2 hi = size - float2(0.5);
            float2 bend = float2(sin(p.y / max(size.y, 1.0) * 6.28 + amount * 2.0),
                                 sin(p.x / max(edge, 1.0) * 3.14)) * depth * 10.0;
            float2 q = clamp(p + bend, lo, hi);
            half4 c = content.eval(q) * 0.16;
            c += tap(q, float2( 1.0,  0.0) * r, lo, hi) * 0.09;
            c += tap(q, float2( 0.5,  0.866) * r, lo, hi) * 0.09;
            c += tap(q, float2(-0.5,  0.866) * r, lo, hi) * 0.09;
            c += tap(q, float2(-1.0,  0.0) * r, lo, hi) * 0.09;
            c += tap(q, float2(-0.5, -0.866) * r, lo, hi) * 0.09;
            c += tap(q, float2( 0.5, -0.866) * r, lo, hi) * 0.09;
            float h = r * 0.5;
            c += tap(q, float2( 0.866,  0.5) * h, lo, hi) * 0.05;
            c += tap(q, float2( 0.0,    1.0) * h, lo, hi) * 0.05;
            c += tap(q, float2(-0.866,  0.5) * h, lo, hi) * 0.05;
            c += tap(q, float2(-0.866, -0.5) * h, lo, hi) * 0.05;
            c += tap(q, float2( 0.0,   -1.0) * h, lo, hi) * 0.05;
            c += tap(q, float2( 0.866, -0.5) * h, lo, hi) * 0.05;
            // Dispersion: red and blue drift apart along the fold direction as the pane recedes.
            float shift = r * 0.35;
            half4 cr = (tap(q, float2(shift, 0.0), lo, hi) + tap(q, float2(shift * 0.5, 0.0), lo, hi)) * 0.5;
            half4 cb = (tap(q, float2(-shift, 0.0), lo, hi) + tap(q, float2(-shift * 0.5, 0.0), lo, hi)) * 0.5;
            half k = half(0.35 * depth);
            c.r = mix(c.r, cr.r, k);
            c.b = mix(c.b, cb.b, k);
            // Light falls off toward the far edge.
            c.rgb *= half(1.0 - 0.38 * depth);
            // A soft sheen sweeps across the pane mid-fold and vanishes at both ends and at the hinge.
            float cx = mix(0.12, 0.85, amount);
            float u = (d - cx) / 0.16;
            float sheen = exp(-u * u) * amount * (1.0 - amount) * 0.45 * smoothstep(0.0, 0.15, d);
            c.rgb += (half3(1.0) - c.rgb) * half3(0.92, 0.96, 1.0) * half(sheen);
            return c;
        }
    """.trimIndent())
    fun draw(canvas: Canvas, width: Int, height: Int, edge: Float, amount: Float,
        inner: Boolean, drawScene: (Canvas) -> Unit) {
        node.setPosition(0, 0, width, height)
        shader.setFloatUniform("size", width.toFloat(), height.toFloat())
        shader.setFloatUniform("edge", edge)
        shader.setFloatUniform("amount", amount)
        shader.setFloatUniform("inner", if (inner) 1f else 0f)
        node.setRenderEffect(RenderEffect.createRuntimeShaderEffect(shader, "content"))
        val recording = node.beginRecording(width, height)
        try { drawScene(recording) } finally { node.endRecording() }
        canvas.drawRenderNode(node)
    }
    fun release() { node.discardDisplayList() }
}
