package com.localqbank.library

import org.junit.Assert.*
import org.junit.Test

class BenIpcLifecycleStateTest {
    @Test fun terminalIsExactlyOnce() { val s=BenIpcLifecycleState(); assertTrue(s.terminal()); assertFalse(s.terminal()) }
    @Test fun releaseIsExactlyOnce() { val s=BenIpcLifecycleState(); assertTrue(s.released()); assertFalse(s.released()) }
    @Test fun terminalThenReleaseStopsNative() { val s=BenIpcLifecycleState(); assertTrue(s.terminal()); assertTrue(s.released()); assertFalse(s.canRunNative()) }
    @Test fun releaseThenTerminalRejected() { val s=BenIpcLifecycleState(); assertTrue(s.released()); assertFalse(s.terminal()) }
}
