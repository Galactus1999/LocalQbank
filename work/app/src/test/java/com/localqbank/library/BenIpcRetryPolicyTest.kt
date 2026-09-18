package com.localqbank.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BenIpcRetryPolicyTest {
    @Test fun retries_only_transport_and_admission_failures() {
        assertTrue(benIpcIsRetryable("INFERENCE_PROCESS_DIED"))
        assertTrue(benIpcIsRetryable("INFERENCE_CONNECTION_LOST"))
        assertTrue(benIpcIsRetryable("IPC_SEND_FAILED:DeadObjectException"))
        assertTrue(benIpcIsRetryable("IPC_FGS_NOT_READY_TIMEOUT"))
        assertFalse(benIpcIsRetryable("IPC_PAYLOAD_TOO_LARGE"))
        assertFalse(benIpcIsRetryable("Generation blocked by model/resource policy"))
    }

    @Test fun retry_budget_is_exactly_one_replay() {
        assertTrue(BEN_IPC_MAX_ATTEMPTS == 2)
    }
}
