package com.localqbank.library

import android.content.Context
import android.graphics.Bitmap
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Explicit, user-triggered Gemini visual study aid. It never replaces the text verifier. */
class BenGeminiImageGenerator(context: Context) {
    private val app = context.applicationContext

    suspend fun generate(prompt: String): Result<Bitmap> = withContext(Dispatchers.IO) {
        val auth = FirebaseAppAvailability.ensureInitialized(app)?.let { com.google.firebase.auth.FirebaseAuth.getInstance() }
            ?: return@withContext Result.failure(IllegalStateException("Firebase is not configured."))
        if (auth.currentUser == null) return@withContext Result.failure(IllegalStateException("Google sign-in is required for Gemini visuals."))
        val config = BenGeminiConfig(app).snapshot()
        if (!config.enabled) return@withContext Result.failure(IllegalStateException("Gemini is disabled by current configuration."))
        runCatching {
            val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
                modelName = "gemini-3.1-flash-image",
                generationConfig = generationConfig { responseModalities = listOf(ResponseModality.IMAGE) }
            )
            val response = withTimeout(config.requestTimeoutMs.coerceAtMost(60_000L)) { model.generateContent(prompt.take(5000)) }
            val part = response.candidates.firstOrNull()?.content?.parts?.firstOrNull { it.javaClass.simpleName == "ImagePart" }
                ?: throw IllegalStateException("Gemini returned no image.")
            val imageMethod = part.javaClass.methods.firstOrNull { it.name == "getImage" && it.parameterTypes.isEmpty() }
                ?: throw IllegalStateException("Gemini image payload is unavailable in this SDK build.")
            val bitmap = imageMethod.invoke(part) as? Bitmap ?: throw IllegalStateException("Gemini image payload could not be decoded.")
            bitmap
        }
    }
}
