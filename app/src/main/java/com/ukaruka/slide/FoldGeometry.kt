package com.ukaruka.slide

/** Angle-driven, reversible effect. Unknown readings leave the scene untouched. */
object FoldGeometry {
    fun openness(angle: Float): Float = if (angle.isFinite()) (angle / 180f).coerceIn(0f, 1f) else 1f
    fun reveal(angle: Float): Float {
        val p = ((openness(angle) - 0.08f) / 0.87f).coerceIn(0f, 1f)
        return p * p * (3 - 2 * p)
    }
    /**
     * Alpha of the pane edge nearest the hinge. Ease-out keeps most brightness until late in the
     * dissolve and, unlike a square root, has a finite slope at 0 so the fade starts without a pop.
     */
    fun nearAlpha(reveal: Float): Float { val r = reveal.coerceIn(0f, 1f); return 1f - (1f - r) * (1f - r) }
    /** Alpha of the far pane edge: follows the eased reveal directly so both ends settle with zero slope. */
    fun farAlpha(reveal: Float): Float = reveal.coerceIn(0f, 1f)
    fun corners(width: Float, height: Float, amount: Float, farRight: Boolean): FloatArray {
        val inset = height * 0.22f * amount.coerceIn(0f, 1f)
        return if (farRight) floatArrayOf(0f, 0f, width, inset, width, height - inset, 0f, height)
        else floatArrayOf(0f, inset, width, 0f, width, height, 0f, height - inset)
    }
}
