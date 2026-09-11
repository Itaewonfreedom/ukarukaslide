package com.ukaruka.slide

import android.graphics.Canvas
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi

/** Spatially varying defocus and refractive drift; the near edge stays sharp. */
@RequiresApi(33)
class FoldLiquid {
    private val node = RenderNode("fold-liquid")
    private val shader = RuntimeShader("""
        uniform shader content;
        uniform float2 size;
        uniform float edge;
        uniform float amount;
        uniform float inner;
        half4 main(float2 p) {
            float distance = clamp(p.x / max(edge, 1.0), 0.0, 1.0);
            distance = mix(distance, 1.0 - distance, inner);
            float depth = distance * distance * amount;
            float radius = 26.0 * depth;
            float2 bend = float2(sin(p.y / max(size.y, 1.0) * 6.28 + amount * 2.0),
                                sin(p.x / max(edge, 1.0) * 3.14)) * depth * 14.0;
            float2 q = clamp(p + bend, float2(0.5), size - float2(0.5));
            half4 c = content.eval(q) * 0.20;
            c += content.eval(clamp(q + float2(radius, 0.0), float2(0.5), size - float2(0.5))) * 0.10;
            c += content.eval(clamp(q - float2(radius, 0.0), float2(0.5), size - float2(0.5))) * 0.10;
            c += content.eval(clamp(q + float2(0.0, radius), float2(0.5), size - float2(0.5))) * 0.10;
            c += content.eval(clamp(q - float2(0.0, radius), float2(0.5), size - float2(0.5))) * 0.10;
            c += content.eval(clamp(q + float2(radius, radius) * 0.7, float2(0.5), size - float2(0.5))) * 0.10;
            c += content.eval(clamp(q - float2(radius, radius) * 0.7, float2(0.5), size - float2(0.5))) * 0.10;
            c += content.eval(clamp(q + float2(radius, -radius) * 0.7, float2(0.5), size - float2(0.5))) * 0.10;
            c += content.eval(clamp(q + float2(-radius, radius) * 0.7, float2(0.5), size - float2(0.5))) * 0.10;
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
