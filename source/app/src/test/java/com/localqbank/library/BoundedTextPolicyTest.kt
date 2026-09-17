package com.localqbank.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BoundedTextPolicyTest {
    @Test fun nullAndEmptyAreRejected() {
        assertNull(BoundedTextPolicy.normalize(null, 10))
        assertNull(BoundedTextPolicy.normalize("", 10))
        assertNull(BoundedTextPolicy.normalize("hello", 0))
    }

    @Test fun isolatedSurrogatesAreRemoved() {
        val malformed = "before" + '\uD800' + "middle" + '\uDC00' + "after"
        assertEquals("beforemiddleafter", BoundedTextPolicy.normalize(malformed, 100))
    }

    @Test fun validSurrogatePairIsPreserved() {
        assertEquals("Ren 😀", BoundedTextPolicy.normalize("Ren 😀", 100))
    }

    @Test fun truncationNeverSplitsSurrogatePair() {
        val value = "a".repeat(9) + "😀"
        assertEquals("a".repeat(9), BoundedTextPolicy.normalize(value, 10))
    }

    @Test fun hardBoundLimitsScanAndOutput() {
        val result = BoundedTextPolicy.normalize("x".repeat(100_000), 128)
        assertEquals(128, result?.length)
    }
}
