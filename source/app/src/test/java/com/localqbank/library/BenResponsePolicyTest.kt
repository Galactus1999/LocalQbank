package com.localqbank.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BenResponsePolicyTest {
    @Test fun blankResponseIsRejected() { assertNull(BenResponsePolicy.normalize("  \n\t")) }

    @Test fun responseIsTrimmed() { assertEquals("clinical answer", BenResponsePolicy.normalize("  clinical answer  ")) }

    @Test fun oversizedResponseIsBounded() {
        val result = BenResponsePolicy.normalize("x".repeat(BenResponsePolicy.MAX_RESPONSE_CHARS + 500))
        assertEquals(BenResponsePolicy.MAX_RESPONSE_CHARS, result?.length)
    }

    @Test fun controlCharactersAndWhitespaceAreNormalized() {
        assertEquals("first second", BenResponsePolicy.normalize("first\u0000\n\t second"))
    }

    @Test fun truncationDoesNotLeaveDanglingSurrogate() {
        val raw = "a".repeat(BenResponsePolicy.MAX_RESPONSE_CHARS - 1) + "😀"
        val result = BenResponsePolicy.normalize(raw)
        assertEquals(BenResponsePolicy.MAX_RESPONSE_CHARS - 1, result?.length)
        assertTrue(result?.endsWith("a") == true)
    }
}
