package com.localqbank.library

import org.junit.Test
import org.junit.Assert.assertTrue

class BenOnlineResearchCapabilityTest {
    private val policy = BenOnlineResearchCapability.Policy(enabled = true, allowedHosts = setOf("example.org"))

    @Test fun disabledByDefault() {
        val d = BenOnlineResearchCapability.check(policy.copy(enabled = false), BenOnlineResearchCapability.Request("example.org", true, 100))
        assertTrue(d is BenOnlineResearchCapability.Decision.Denied)
    }

    @Test fun uninitiatedRequestDenied() {
        val d = BenOnlineResearchCapability.check(policy, BenOnlineResearchCapability.Request("example.org", false, 100))
        assertTrue(d is BenOnlineResearchCapability.Decision.Denied)
    }

    @Test fun subdomainAndBudgetAllowed() {
        val d = BenOnlineResearchCapability.check(policy, BenOnlineResearchCapability.Request("pubs.example.org", true, 1000))
        assertTrue(d is BenOnlineResearchCapability.Decision.Allowed)
    }

    @Test fun untrustedHostDenied() {
        val d = BenOnlineResearchCapability.check(policy, BenOnlineResearchCapability.Request("evil-example.org", true, 1000))
        assertTrue(d is BenOnlineResearchCapability.Decision.Denied)
    }
}
