package com.localqbank.library

import android.os.Build
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BenIpcSharedMemoryInstrumentedTest {
    @Test fun large_text_round_trips_without_inline_bundle_copy() {
        assumeTrue(Build.VERSION.SDK_INT >= 27)
        InstrumentationRegistry.getInstrumentation().targetContext
        val text = "Rovex IPC payload ".repeat(4_000)
        val bundle = Bundle()
        val memory = benIpcPutText(bundle, "payload", text)
        try {
            assertTrue(memory != null)
            assertTrue(benIpcHasSharedText(bundle, "payload"))
            assertEquals(text, benIpcGetText(bundle, "payload"))
        } finally {
            benIpcCloseSharedMemory(memory)
        }
    }
}
