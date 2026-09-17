package com.localqbank.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class BenInferenceBackendTest {
    @Test
    fun unavailableBackendIsDeterministicallyOffline() {
        assertFalse(UnavailableBenInferenceBackend.isAvailable)
        assertNull(UnavailableBenInferenceBackend.answer("test"))
    }
}
