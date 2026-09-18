package com.localqbank.library

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Transport-only IPC smoke test. It deliberately does not require a QBank or neural model.
 * The test proves that the isolated service can bind and answer a pure Binder ping.
 */
@RunWith(AndroidJUnit4::class)
class BenInferenceProcessIpcSmokeTest {
    @Test
    fun binderPingDoesNotRequireQBankOrModel() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        BenInferenceProcessClient(context).use { client ->
            val result = client.pingSuspend(timeoutMs = 10_000L)
            assertTrue("Ben IPC ping failed: ${result.failure}", result.ok)
        }
    }
}
