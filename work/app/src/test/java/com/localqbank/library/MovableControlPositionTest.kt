package com.localqbank.library

import org.junit.Assert.assertEquals
import org.junit.Test

class MovableControlPositionTest {
    @Test fun invalidSavedCoordinatesFallBackToSafeDefaults() {
        val point = MovableControlPosition.resolve(Float.NaN, Float.POSITIVE_INFINITY, 1000, 800, 0.25f, 0.5f)
        assertEquals(250f, point.x, 0.001f)
        assertEquals(400f, point.y, 0.001f)
    }

    @Test fun normalizedCoordinatesAreClamped() {
        val point = MovableControlPosition.normalized(Float.POSITIVE_INFINITY, -20f, 100, 100)
        assertEquals(0f, point.x, 0.001f)
        assertEquals(0f, point.y, 0.001f)
    }

    @Test fun zeroSizedParentRemainsSafe() {
        val point = MovableControlPosition.resolve(0.5f, 0.5f, 0, 0, 0.25f, 0.75f)
        assertEquals(0f, point.x, 0.001f)
        assertEquals(0f, point.y, 0.001f)
    }
}
