package com.localqbank.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class BenIpcLifecycleRaceTest {
    @Test fun concurrentTerminalHasExactlyOneWinner() {
        repeat(100) {
            val state = BenIpcLifecycleState()
            val pool = Executors.newFixedThreadPool(8)
            val gate = CountDownLatch(1)
            val winners = AtomicInteger(0)
            repeat(8) { pool.execute { gate.await(); if (state.terminal()) winners.incrementAndGet() } }
            gate.countDown(); pool.shutdown()
            while (!pool.isTerminated) Thread.yield()
            assertEquals(1, winners.get())
            assertEquals(BenIpcLifecycleState.Phase.TERMINAL, state.phase())
        }
    }

    @Test fun concurrentReleaseHasExactlyOneTransition() {
        repeat(100) {
            val state = BenIpcLifecycleState()
            val pool = Executors.newFixedThreadPool(8)
            val gate = CountDownLatch(1)
            val winners = AtomicInteger(0)
            repeat(8) { pool.execute { gate.await(); if (state.released()) winners.incrementAndGet() } }
            gate.countDown(); pool.shutdown()
            while (!pool.isTerminated) Thread.yield()
            assertEquals(1, winners.get())
            assertEquals(BenIpcLifecycleState.Phase.RELEASED, state.phase())
            assertFalse(state.canRunNative())
        }
    }

    @Test fun releaseAfterTerminalIsAllowedExactlyOnce() {
        val state = BenIpcLifecycleState()
        assertTrue(state.terminal())
        assertTrue(state.released())
        assertFalse(state.terminal())
        assertFalse(state.released())
    }
}
