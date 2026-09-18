package com.localqbank.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class LifecycleExecutorTest {
    @Test
    fun executeRunsBeforeClose() {
        val latch = CountDownLatch(1)
        val executor = LifecycleExecutor(Executors.newSingleThreadExecutor())
        try {
            assertTrue(executor.execute { latch.countDown() })
            assertTrue(latch.await(2, TimeUnit.SECONDS))
        } finally {
            executor.close()
        }
    }

    @Test
    fun executeBecomesNoOpAfterClose() {
        val executor = LifecycleExecutor(Executors.newSingleThreadExecutor())
        executor.close()
        assertTrue(executor.isClosed())
        assertFalse(executor.execute { error("must not run") })
        executor.close()
    }
}
