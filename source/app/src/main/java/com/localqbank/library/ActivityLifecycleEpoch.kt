package com.localqbank.library

import java.util.concurrent.atomic.AtomicLong

/**
 * Monotonic foreground lifecycle token used to invalidate delayed background cleanup.
 * A stop callback from an older Activity instance must never be allowed to mutate
 * persistence state after a newer Activity has already started.
 */
class ActivityLifecycleEpoch {
    private val value = AtomicLong(0L)

    fun advance(): Long = value.incrementAndGet()

    fun isCurrent(epoch: Long): Boolean = value.get() == epoch

    fun current(): Long = value.get()
}
