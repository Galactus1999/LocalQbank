package com.localqbank.library

import kotlin.test.Test
import kotlin.test.assertIs

class BenOnlineResearchCapabilityTest {
    private val policy = BenOnlineResearchCapability.Policy(enabled = true, allowedHosts = setOf("example.org"))

    @Test fun disabledByDefault() {
        val d = BenOnlineResearchCapability.check(policy.copy(enabled = false), BenOnlineResearchCapability.Request("example.org", true, 100))
        assertIs<BenOnlineResearchCapability.Decision.Denied>(d)
    }

    @Test fun uninitiatedRequestDenied() {
        val d = BenOnlineResearchCapability.check(policy, BenOnlineResearchCapability.Request("example.org", false, 100))
        assertIs<BenOnlineResearchCapability.Decision.Denied>(d)
    }

    @Test fun subdomainAndBudgetAllowed() {
        val d = BenOnlineResearchCapability.check(policy, BenOnlineResearchCapability.Request("pubs.example.org", true, 1000))
        assertIs<BenOnlineResearchCapability.Decision.Allowed>(d)
    }

    @Test fun untrustedHostDenied() {
        val d = BenOnlineResearchCapability.check(policy, BenOnlineResearchCapability.Request("evil-example.org", true, 1000))
        assertIs<BenOnlineResearchCapability.Decision.Denied>(d)
    }
}
