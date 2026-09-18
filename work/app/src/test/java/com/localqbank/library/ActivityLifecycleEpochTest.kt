package com.localqbank.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityLifecycleEpochTest {
    @Test fun olderStopCannotInvalidateNewerStart() {
        val epoch = ActivityLifecycleEpoch()
        val old = epoch.advance()
        val newer = epoch.advance()
        assertFalse(epoch.isCurrent(old))
        assertTrue(epoch.isCurrent(newer))
    }

    @Test fun currentEpochRemainsValidUntilAdvanced() {
        val epoch = ActivityLifecycleEpoch()
        val current = epoch.advance()
        assertTrue(epoch.isCurrent(current))
        epoch.advance()
        assertFalse(epoch.isCurrent(current))
    }
}
