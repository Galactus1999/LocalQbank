package com.localqbank.library

/**
 * Deterministic JVM-only transport model for lifecycle/race tests.
 * It deliberately models process death and rebind without pretending the JVM creates a real
 * android:process boundary. Real Binder/process behavior remains covered by androidTest.
 */
class FakeInferenceProcessTransport {
    private var alive = true
    private var generation = 1L

    fun killProcess() {
        alive = false
        generation++
    }

    fun rebind(): Boolean {
        alive = true
        return alive
    }

    fun request(): String = if (alive) "OK" else "INFERENCE_PROCESS_DIED"

    fun generation(): Long = generation
}
