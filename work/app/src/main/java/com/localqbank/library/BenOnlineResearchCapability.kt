package com.localqbank.library

/**
 * Capability policy for future online research. Offline-first remains the default.
 * This class does not perform network I/O; it only decides whether a caller is permitted to
 * request it. Network clients must enforce the returned capability and their own platform policy.
 */
object BenOnlineResearchCapability {
    data class Policy(
        val enabled: Boolean = false,
        val requireUserInitiation: Boolean = true,
        val allowedHosts: Set<String> = emptySet(),
        val maxResponseBytes: Long = 2_000_000L,
        val timeoutMs: Long = 12_000L
    )

    data class Request(val host: String, val userInitiated: Boolean, val expectedBytes: Long)

    sealed interface Decision {
        data object Allowed : Decision
        data class Denied(val reason: String) : Decision
    }

    fun check(policy: Policy, request: Request): Decision {
        if (!policy.enabled) return Decision.Denied("Online research is disabled")
        if (policy.requireUserInitiation && !request.userInitiated) return Decision.Denied("User initiation is required")
        val host = request.host.trim().lowercase().trimEnd('.')
        if (host.isBlank() || !policy.allowedHosts.any { host == it.lowercase().trimEnd('.') || host.endsWith("." + it.lowercase().trimEnd('.')) }) {
            return Decision.Denied("Host is not allowlisted")
        }
        if (request.expectedBytes < 0L || request.expectedBytes > policy.maxResponseBytes.coerceAtLeast(1L)) {
            return Decision.Denied("Response exceeds bounded research budget")
        }
        if (policy.timeoutMs !in 1L..60_000L) return Decision.Denied("Invalid bounded research timeout")
        return Decision.Allowed
    }
}
