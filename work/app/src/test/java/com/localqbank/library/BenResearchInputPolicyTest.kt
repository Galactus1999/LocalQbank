package com.localqbank.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BenResearchInputPolicyTest {
    @Test fun blankInputIsRejected() {
        assertNull(BenResearchInputPolicy.normalize("   \n\t"))
    }

    @Test fun whitespaceIsCanonicalised() {
        assertEquals("acute kidney injury staging", BenResearchInputPolicy.normalize("  acute\n\tkidney   injury   staging  "))
    }

    @Test fun embeddedControlCharactersAreRemoved() {
        assertEquals("heart failure NYHA", BenResearchInputPolicy.normalize("heart\u0000failure\u0007 NYHA"))
    }

    @Test fun controlOnlyInputIsRejected() {
        assertNull(BenResearchInputPolicy.normalize("\u0000\u0007\u001b"))
    }

    @Test fun oversizedInputDoesNotExpandPastTheHardBound() {
        assertEquals("hello", BenResearchInputPolicy.normalize("  hello  "))
        val oversized = "x".repeat(BenResearchInputPolicy.MAX_QUERY_CHARS + 500)
        assertEquals(BenResearchInputPolicy.MAX_QUERY_CHARS, BenResearchInputPolicy.normalize(oversized)?.length)
    }
}
