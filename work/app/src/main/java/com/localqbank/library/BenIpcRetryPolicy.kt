package com.localqbank.library

/** Deterministic retry classification for isolated-process IPC. */
const val BEN_IPC_MAX_ATTEMPTS = 2
const val BEN_IPC_RETRY_BACKOFF_MS = 350L

/** Transport/admission failure prefixes that are safe to replay exactly once. */
private val BEN_IPC_RETRYABLE_PREFIXES = listOf(
    "INFERENCE_PROCESS_DIED",
    "INFERENCE_CONNECTION_LOST",
    "IPC_SEND_FAILED",
    "IPC_FGS_NOT_READY",
    "IPC_TIMEOUT"
)

fun benIpcIsRetryable(reason: String): Boolean =
    BEN_IPC_RETRYABLE_PREFIXES.any { reason.startsWith(it, ignoreCase = true) }
