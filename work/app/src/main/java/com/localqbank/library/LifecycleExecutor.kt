package com.localqbank.library

import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Activity-scoped executor facade.
 *
 * UI callbacks can race with Activity.onDestroy(). A raw ExecutorService throws
 * RejectedExecutionException in that window; this facade turns late work into a
 * safe no-op and makes shutdown idempotent. It does not retain the Activity.
 */
class LifecycleExecutor(private val delegate: ExecutorService) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    fun execute(task: () -> Unit): Boolean {
        if (closed.get()) return false
        return try {
            delegate.execute {
                if (!closed.get()) runCatching { task() }
            }
            true
        } catch (_: RejectedExecutionException) {
            false
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            delegate.shutdownNow()
        }
    }

    fun isClosed(): Boolean = closed.get()
}
