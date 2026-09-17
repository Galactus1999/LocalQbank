package com.localqbank.library

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class ProgressConcurrencyRegressionTest {
    @Test fun fakeProgress_canAcceptConcurrentIndependentUpdates() {
        val repo = FakeProgressRepository()
        val pool = Executors.newFixedThreadPool(4)
        val gate = CountDownLatch(1)
        repeat(4) { index ->
            pool.execute {
                gate.await()
                repo.setBookmark("q$index", "flag")
            }
        }
        gate.countDown()
        pool.shutdown()
        while (!pool.isTerminated) Thread.yield()
        repeat(4) { assertEquals("flag", repo.bookmark("q$it")) }
    }
}
