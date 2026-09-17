package com.localqbank.library

/** Deterministic retry classification for isolated-process IPC. */
const val BEN_IPC_MAX_ATTEMPTS = 1
const val BEN_IPC_RETRY_BACKOFF_MS = 350L

fun benIpcIsRetryable(reason: String): Boolean = false
