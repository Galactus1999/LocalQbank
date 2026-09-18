package com.localqbank.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BenNeuralModelRegistryTest {
    @Test fun embeddingProfileIsConservative() {
        val model = BenNeuralModelRegistry.embeddingGemma300m
        assertTrue(model.estimatedModelMb <= BenAiRuntimeGate.CONSERVATIVE_MODEL_MB_LIMIT)
        assertEquals(".tflite", model.format)
        assertEquals(512, model.recommendedMaxSequenceLength)
    }

    @Test fun embeddingProfileHasMobileSafeBoundedMetadata() {
        val model = BenNeuralModelRegistry.embeddingGemma300m
        assertTrue(model.estimatedModelMb <= 384)
        assertTrue(model.estimatedRuntimeMb <= 600)
        assertEquals("Semantic retrieval / concept similarity", model.role)
    }

    @Test fun registryContainsGenerationCandidateWithoutMakingItActive() {
        assertTrue(BenNeuralModelRegistry.all().any { it.id == "gemma3_270m_it" })
        assertTrue(BenNeuralModelRegistry.all().none { it.id == "gemma3_270m_it" && it == BenNeuralModelRegistry.recommended() })
    }
}
