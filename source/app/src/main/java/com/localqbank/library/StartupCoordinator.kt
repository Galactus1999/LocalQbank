package com.localqbank.library

import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean

/** Tiered startup: only critical managers are created synchronously; heavy work remains lazy. */
object StartupCoordinator {
    private val ready = AtomicBoolean(false)
    fun initialize(context: Context) {
        if (!ready.compareAndSet(false, true)) return
        android.os.Trace.beginSection("Rovex.StartupCoordinator")
        try {
            AppManagers.initialize(context.applicationContext)
            // Search indexing and large QBank scans are intentionally deferred until first use.
        } finally {
            android.os.Trace.endSection()
        }
    }
}
