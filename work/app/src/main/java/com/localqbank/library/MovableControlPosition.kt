package com.localqbank.library

/** Pure geometry policy for persisted draggable flashcard controls. */
object MovableControlPosition {
    data class Point(val x: Float, val y: Float)

    fun resolve(
        savedX: Float,
        savedY: Float,
        maxX: Int,
        maxY: Int,
        defaultXFraction: Float,
        defaultYFraction: Float
    ): Point {
        val safeMaxX = maxX.coerceAtLeast(0)
        val safeMaxY = maxY.coerceAtLeast(0)
        val safeDefaultX = defaultXFraction.takeIf { it.isFinite() } ?: 0.5f
        val safeDefaultY = defaultYFraction.takeIf { it.isFinite() } ?: 0.5f
        val x = if (!savedX.isFinite()) safeMaxX * safeDefaultX else savedX * safeMaxX
        val y = if (!savedY.isFinite()) safeMaxY * safeDefaultY else savedY * safeMaxY
        return Point(
            x.coerceIn(0f, safeMaxX.toFloat()),
            y.coerceIn(0f, safeMaxY.toFloat())
        )
    }

    fun normalized(x: Float, y: Float, maxX: Int, maxY: Int): Point {
        val safeMaxX = maxX.coerceAtLeast(1)
        val safeMaxY = maxY.coerceAtLeast(1)
        val safeX = if (x.isFinite()) x else 0f
        val safeY = if (y.isFinite()) y else 0f
        return Point(
            (safeX / safeMaxX).coerceIn(0f, 1f),
            (safeY / safeMaxY).coerceIn(0f, 1f)
        )
    }
}
