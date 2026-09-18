package com.localqbank.library

/** Pure, deterministic IPC lifecycle guard. No Android/native dependencies.
 *
 * Terminal and release are deliberately separate: callers may finish the client-visible
 * request before the native runtime has actually been released. The monitor makes the
 * transition exactly-once under Binder/coroutine race conditions.
 */
internal class BenIpcLifecycleState {
    enum class Phase { ACTIVE, TERMINAL, RELEASED }
    enum class ConnectionPhase { IDLE, BINDING, READY, DEAD, CLOSED }
    private val connectionPhase = java.util.concurrent.atomic.AtomicReference(ConnectionPhase.IDLE)
    fun connectionPhase(): ConnectionPhase = connectionPhase.get()
    fun beginBinding(): Boolean = connectionPhase.compareAndSet(ConnectionPhase.IDLE, ConnectionPhase.BINDING) ||
        connectionPhase.compareAndSet(ConnectionPhase.DEAD, ConnectionPhase.BINDING)
    fun markReady(): Boolean = connectionPhase.compareAndSet(ConnectionPhase.BINDING, ConnectionPhase.READY)
    fun markDead(): Boolean {
        while (true) {
            val current = connectionPhase.get()
            if (current == ConnectionPhase.CLOSED) return false
            if (current == ConnectionPhase.DEAD) return true
            if (connectionPhase.compareAndSet(current, ConnectionPhase.DEAD)) return true
        }
    }
    fun closeConnection() { connectionPhase.set(ConnectionPhase.CLOSED) }
    fun canSendRemote(): Boolean = connectionPhase.get() == ConnectionPhase.READY
    private val phase = java.util.concurrent.atomic.AtomicReference(Phase.ACTIVE)
    fun phase(): Phase = phase.get()
    fun terminal(): Boolean = phase.compareAndSet(Phase.ACTIVE, Phase.TERMINAL)
    fun released(): Boolean {
        while (true) {
            val current = phase.get()
            if (current == Phase.RELEASED) return false
            if (phase.compareAndSet(current, Phase.RELEASED)) return true
        }
    }
    fun canRunNative(): Boolean = phase.get() == Phase.ACTIVE
}
