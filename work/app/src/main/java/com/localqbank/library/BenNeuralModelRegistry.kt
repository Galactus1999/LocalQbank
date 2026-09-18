package com.localqbank.library

/**
 * Static registry for optional, locally installed Ben neural accelerators.
 * This class contains metadata/policy only: it never downloads, loads, or executes a model.
 */
object BenNeuralModelRegistry {
    data class ModelProfile(
        val id: String,
        val name: String,
        val role: String,
        val format: String,
        val estimatedModelMb: Int,
        val estimatedRuntimeMb: Int,
        val recommendedMaxSequenceLength: Int,
        val offline: Boolean = true
    )

    val embeddingGemma300m = ModelProfile(
        id = "embeddinggemma_300m",
        name = "EmbeddingGemma 300M",
        role = "Semantic retrieval / concept similarity",
        format = ".tflite",
        estimatedModelMb = 190,
        estimatedRuntimeMb = 320,
        recommendedMaxSequenceLength = 512
    )

    val gemma3_270m = ModelProfile(
        id = "gemma3_270m_it",
        name = "Gemma 3 270M IT",
        role = "Small local text generation",
        format = ".litertlm",
        estimatedModelMb = 304,
        estimatedRuntimeMb = 700,
        recommendedMaxSequenceLength = 512
    )

    fun recommended(): ModelProfile = embeddingGemma300m
    fun all(): List<ModelProfile> = listOf(embeddingGemma300m, gemma3_270m)
}
