package com.localqbank.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BenIpcFakeTransportTest {
    @Test fun processDeath_requires_rebind_before_retry() {
        val transport = FakeInferenceProcessTransport()
        assertEquals("OK", transport.request())
        val oldGeneration = transport.generation()
        transport.killProcess()
        assertEquals("INFERENCE_PROCESS_DIED", transport.request())
        assertTrue(transport.generation() > oldGeneration)
        assertTrue(transport.rebind())
        assertEquals("OK", transport.request())
    }

    @Test fun fake_transport_is_deterministic_for_jvm_race_tests() {
        val transport = FakeInferenceProcessTransport()
        repeat(100) {
            transport.killProcess()
            assertEquals("INFERENCE_PROCESS_DIED", transport.request())
            transport.rebind()
            assertEquals("OK", transport.request())
        }
    }
}
