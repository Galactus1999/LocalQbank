package com.localqbank.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ThompsonBanditPolicyTest {

    @Test
    fun sampleBeta_alwaysWithinZeroToOne() {
        val policy = ThompsonBanditPolicy(Random(42))
        repeat(500) {
            val sample = policy.sampleBeta(alpha0 = 1.0 + it, beta0 = 5.0)
            assertTrue("sample $sample out of [0,1]", sample in 0.0..1.0)
        }
    }

    @Test
    fun sampleBeta_higherAlphaTrendsHigherOnAverage() {
        val policy = ThompsonBanditPolicy(Random(7))
        val strongAvg = (1..2000).map { policy.sampleBeta(50.0, 5.0) }.average()
        val weakAvg = (1..2000).map { policy.sampleBeta(5.0, 50.0) }.average()
        assertTrue("strong belief ($strongAvg) should sample higher than weak belief ($weakAvg)", strongAvg > weakAvg)
    }

    @Test
    fun selectAction_favorsTheCandidateWithStrongerBelief_mostOfTheTime() {
        val policy = ThompsonBanditPolicy(Random(123))
        val candidates = mapOf(
            "strong" to (80.0 to 5.0),
            "weak" to (5.0 to 80.0)
        )
        var strongWins = 0
        repeat(1000) {
            if (policy.selectAction(candidates) == "strong") strongWins++
        }
        // Not deterministic (that's the point of Thompson sampling exploring), but with this
        // large a belief gap it should win the large majority of draws.
        assertTrue("expected 'strong' to win most draws, won $strongWins/1000", strongWins > 900)
    }

    @Test
    fun selectAction_emptyCandidates_returnsNull() {
        val policy = ThompsonBanditPolicy(Random(1))
        assertNull(policy.selectAction(emptyMap<String, Pair<Double, Double>>()))
    }

    @Test
    fun updateBelief_positiveReward_increasesAlphaRelativeToBeta() {
        val policy = ThompsonBanditPolicy()
        val (newAlpha, newBeta) = policy.updateBelief(alpha = 1.0, beta = 1.0, reward = 1.0)
        assertTrue(newAlpha > newBeta)
    }

    @Test
    fun updateBelief_negativeReward_increasesBetaRelativeToAlpha() {
        val policy = ThompsonBanditPolicy()
        val (newAlpha, newBeta) = policy.updateBelief(alpha = 1.0, beta = 1.0, reward = 0.0)
        assertTrue(newBeta > newAlpha)
    }

    @Test
    fun updateBelief_matchesOriginalDecayFormula() {
        val policy = ThompsonBanditPolicy()
        val (newAlpha, newBeta) = policy.updateBelief(alpha = 2.0, beta = 3.0, reward = 0.7)
        // Original inline formula: newA = a*.995 + r; newB = b*.995 + (1-r)
        assertEquals(2.0 * 0.995 + 0.7, newAlpha, 1e-9)
        assertEquals(3.0 * 0.995 + 0.3, newBeta, 1e-9)
    }

    @Test
    fun updateBelief_rewardIsClampedToZeroOneRange() {
        val policy = ThompsonBanditPolicy()
        val (highAlpha, _) = policy.updateBelief(alpha = 1.0, beta = 1.0, reward = 5.0)
        val (lowAlpha, _) = policy.updateBelief(alpha = 1.0, beta = 1.0, reward = -5.0)
        assertEquals(1.0 * 0.995 + 1.0, highAlpha, 1e-9)
        assertEquals(1.0 * 0.995 + 0.0, lowAlpha, 1e-9)
    }
}
