package com.ukaruka.slide

/** Angle-driven, reversible effect. Unknown readings leave the scene untouched. */
object FoldGeometry {
    fun openness(angle: Float): Float = if (angle.isFinite()) (angle / 180f).coerceIn(0f, 1f) else 1f
    fun reveal(angle: Float): Float {
        val p = ((openness(angle) - 0.08f) / 0.87f).coerceIn(0f, 1f)
        return p * p * (3 - 2 * p)
    }
    /** Alpha of the pane's far edge. The hinge edge is always fully opaque so both panes meet as one continuum. */
    fun farAlpha(reveal: Float): Float = reveal.coerceIn(0f, 1f)
    /**
     * Alpha across the pane, sampled from the far edge (index 0) to the hinge (last index). Ease-out keeps
     * most of the pane bright and concentrates the dissolve where the defocus already is.
     */
    fun alphaStops(reveal: Float, samples: Int = 7): FloatArray {
        val far = farAlpha(reveal)
        return FloatArray(samples) { i ->
            val t = i / (samples - 1f)
            far + (1f - far) * (1f - (1f - t) * (1f - t))
        }
    }
    /** Strength of the blurred ambient light behind the pane; zero slope-free at flat so it never pops. */
    fun glowAlpha(amount: Float): Float { val a = amount.coerceIn(0f, 1f); return 0.5f * (1f - (1f - a) * (1f - a)) }
    /** Darkness of the symmetric shadow in the hinge gutter. */
    fun gutterShadow(amount: Float): Float = 0.24f * amount.coerceIn(0f, 1f)
    /** Half width of the gutter shadow as a fraction of the full surface width. */
    const val GUTTER_FRACTION = 0.055f
    /**
     * Perspective corners of the pane: the far edge shrinks vertically and moves slightly toward the hinge,
     * the way a plane rotating away foreshortens. The hinge edge never moves.
     */
    fun corners(width: Float, height: Float, amount: Float, farRight: Boolean): FloatArray {
        val a = amount.coerceIn(0f, 1f)
        val inset = height * 0.22f * a
        val shrink = width * 0.10f * a
        return if (farRight) floatArrayOf(0f, 0f, width - shrink, inset, width - shrink, height - inset, 0f, height)
        else floatArrayOf(shrink, inset, width, 0f, width, height, shrink, height - inset)
    }
}
