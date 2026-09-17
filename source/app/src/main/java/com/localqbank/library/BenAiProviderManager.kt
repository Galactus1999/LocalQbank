package com.localqbank.library

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * User-controlled multi-provider AI boundary for Ben.
 *
 * Local Gemma/EmbeddingGemma remain the default. Cloud providers are optional accelerators.
 * API keys are encrypted with an Android Keystore AES key and are never written to source.
 * FREE_ONLY is a hard policy gate: providers marked PAYG/UNKNOWN cannot be called while it is on.
 */
class BenAiProviderManager(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val secrets = BenAiSecretStore(app)

    enum class CostTier { LOCAL, FREE, QUOTA, PAYG, UNKNOWN }
    enum class Kind { LOCAL, OPENAI_COMPATIBLE, GEMINI_REST, COHERE, GOOGLE_SEARCH }
    enum class ProviderId {
        LOCAL_GEMMA,
        GEMINI_FIREBASE,
        GEMINI_API,
        GROQ,
        ZAI_GLM,
        OPENROUTER_FREE,
        CLOUDFLARE_WORKERS_AI,
        MISTRAL,
        SAMBANOVA,
        COHERE,
        GOOGLE_AI_SEARCH
    }

    data class ProviderSpec(
        val id: ProviderId,
        val label: String,
        val kind: Kind,
        val cost: CostTier,
        val defaultModel: String,
        val endpoint: String = "",
        val requiresKey: Boolean = true,
        val note: String
    )

    data class Result(val provider: ProviderId, val model: String, val text: String, val elapsedMs: Long)

    data class Plan(
        val primary: ProviderId,
        val fallback: ProviderId,
        val research: ProviderId,
        val combine: Boolean,
        val freeOnly: Boolean
    )

    fun specs(): List<ProviderSpec> = SPECS

    fun plan(): Plan = Plan(
        primary = providerPref(KEY_PRIMARY, ProviderId.LOCAL_GEMMA),
        fallback = providerPref(KEY_FALLBACK, ProviderId.GEMINI_FIREBASE),
        research = providerPref(KEY_RESEARCH, ProviderId.GOOGLE_AI_SEARCH),
        combine = prefs.getBoolean(KEY_COMBINE, false),
        freeOnly = prefs.getBoolean(KEY_FREE_ONLY, true)
    )

    fun setPrimary(id: ProviderId) = prefs.edit().putString(KEY_PRIMARY, id.name).apply()
    fun setFallback(id: ProviderId) = prefs.edit().putString(KEY_FALLBACK, id.name).apply()
    fun setResearch(id: ProviderId) = prefs.edit().putString(KEY_RESEARCH, id.name).apply()
    fun setCombine(enabled: Boolean) = prefs.edit().putBoolean(KEY_COMBINE, enabled).apply()
    fun setFreeOnly(enabled: Boolean) = prefs.edit().putBoolean(KEY_FREE_ONLY, enabled).apply()

    fun hasKey(id: ProviderId): Boolean = secrets.get(id.name)?.isNotBlank() == true
    fun saveKey(id: ProviderId, key: String) = secrets.put(id.name, key.trim())
    fun clearKey(id: ProviderId) = secrets.remove(id.name)

    fun model(id: ProviderId): String = prefs.getString("model_${id.name}", spec(id).defaultModel) ?: spec(id).defaultModel
    fun setModel(id: ProviderId, model: String) = prefs.edit().putString("model_${id.name}", model.trim()).apply()

    fun isAllowed(id: ProviderId): Boolean {
        if (id == ProviderId.LOCAL_GEMMA || id == ProviderId.GEMINI_FIREBASE || id == ProviderId.GOOGLE_AI_SEARCH) return true
        val s = spec(id)
        return !plan().freeOnly || s.cost == CostTier.FREE || s.cost == CostTier.QUOTA
    }

    suspend fun generate(prompt: String, system: String? = null, preferred: ProviderId = plan().primary): Result = withContext(Dispatchers.IO) {
        val clean = prompt.trim().take(MAX_PROMPT_CHARS)
        require(clean.isNotBlank()) { "Prompt is empty" }
        val candidates = buildList {
            add(preferred)
            val p = plan()
            if (p.combine && p.fallback != preferred) add(p.fallback)
            if (p.fallback != preferred && !p.combine) add(p.fallback)
            if (preferred != ProviderId.LOCAL_GEMMA) add(ProviderId.LOCAL_GEMMA)
        }.distinct()
        var last: Throwable? = null
        for (id in candidates) {
            if (!isAllowed(id)) continue
            try {
                return@withContext when (id) {
                    ProviderId.LOCAL_GEMMA -> Result(id, "Gemma 3 270M", localFallback(clean), 0L)
                    ProviderId.GEMINI_FIREBASE -> Result(id, model(id), "Use BenGeminiCoordinator for Firebase Gemini research.", 0L)
                    ProviderId.GOOGLE_AI_SEARCH -> Result(id, "Google AI Mode", "OPEN_GOOGLE_AI_SEARCH", 0L)
                    else -> requestRemote(id, clean, system)
                }
            } catch (t: Throwable) {
                last = t
            }
        }
        throw IllegalStateException(last?.message ?: "No configured/allowed AI provider is available")
    }

    suspend fun test(id: ProviderId): Result = withContext(Dispatchers.IO) {
        when (id) {
            ProviderId.LOCAL_GEMMA -> Result(id, "Gemma 3 270M", "LOCAL_OK", 0L)
            ProviderId.GEMINI_FIREBASE -> Result(id, model(id), "FIREBASE_GEMINI_READY", 0L)
            ProviderId.GOOGLE_AI_SEARCH -> Result(id, "Google AI Mode", "BROWSER_READY", 0L)
            else -> requestRemote(id, "Reply with exactly: BEN_PROVIDER_OK", "You are a connectivity test. Do not add anything else.")
        }
    }

    private fun requestRemote(id: ProviderId, prompt: String, system: String?): Result {
        val spec = spec(id)
        val key = secrets.get(id.name)?.trim().orEmpty()
        if (spec.requiresKey && key.isBlank()) throw IllegalStateException("${spec.label}: API key not configured")
        val started = System.currentTimeMillis()
        val body = when (spec.kind) {
            Kind.OPENAI_COMPATIBLE -> openAiBody(spec, prompt, system)
            Kind.GEMINI_REST -> geminiBody(spec, prompt, system)
            Kind.COHERE -> cohereBody(spec, prompt, system)
            else -> throw IllegalStateException("Unsupported remote provider")
        }
        val url = when (spec.kind) {
            Kind.GEMINI_REST -> spec.endpoint + URLEncoder.encode(model(id), StandardCharsets.UTF_8.name()) + ":generateContent?key=" + URLEncoder.encode(key, StandardCharsets.UTF_8.name())
            else -> spec.endpoint
        }
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12_000
            readTimeout = 45_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            if (spec.kind != Kind.GEMINI_REST) setRequestProperty("Authorization", "Bearer $key")
            if (id == ProviderId.OPENROUTER_FREE) {
                setRequestProperty("HTTP-Referer", "https://github.com/Galactus1999/LocalQbank")
                setRequestProperty("X-Title", "Rovex Ben")
            }
        }
        connection.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val raw = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
        connection.disconnect()
        if (code !in 200..299) throw IllegalStateException("${spec.label} HTTP $code: ${extractError(raw).take(500)}")
        val text = parseText(id, raw).trim()
        if (text.isBlank()) throw IllegalStateException("${spec.label} returned an empty response")
        Result(id, model(id), text, System.currentTimeMillis() - started)
    }

    private fun openAiBody(spec: ProviderSpec, prompt: String, system: String?): JSONObject = JSONObject().apply {
        put("model", model(spec.id))
        put("messages", JSONArray().apply {
            system?.takeIf { it.isNotBlank() }?.let { put(JSONObject().put("role", "system").put("content", it)) }
            put(JSONObject().put("role", "user").put("content", prompt))
        })
        put("temperature", 0.2)
        put("max_tokens", 2048)
        put("stream", false)
        if (spec.id == ProviderId.ZAI_GLM) put("thinking", JSONObject().put("type", "enabled"))
    }

    private fun geminiBody(spec: ProviderSpec, prompt: String, system: String?): JSONObject = JSONObject().apply {
        put("contents", JSONArray().apply { put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", prompt)))) })
        system?.takeIf { it.isNotBlank() }?.let { put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", it)))) }
        put("generationConfig", JSONObject().put("temperature", 0.2).put("maxOutputTokens", 2048))
    }

    private fun cohereBody(spec: ProviderSpec, prompt: String, system: String?): JSONObject = JSONObject().apply {
        put("model", model(spec.id))
        put("stream", false)
        put("messages", JSONArray().apply {
            system?.takeIf { it.isNotBlank() }?.let { put(JSONObject().put("role", "system").put("content", it)) }
            put(JSONObject().put("role", "user").put("content", prompt))
        })
        put("temperature", 0.2)
        put("max_tokens", 2048)
    }

    private fun parseText(id: ProviderId, raw: String): String {
        val json = JSONObject(raw)
        return when (id) {
            ProviderId.GEMINI_API -> json.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text").orEmpty()
            ProviderId.COHERE -> json.optJSONObject("message")?.optJSONArray("content")?.optJSONObject(0)?.optString("text").orEmpty()
            else -> {
                val choice = json.optJSONArray("choices")?.optJSONObject(0)
                choice?.optJSONObject("message")?.optString("content").orEmpty().ifBlank { choice?.optString("text").orEmpty() }
            }
        }
    }

    private fun extractError(raw: String): String = runCatching {
        val j = JSONObject(raw)
        j.optJSONObject("error")?.optString("message")?.ifBlank { raw } ?: raw
    }.getOrDefault(raw)

    private fun localFallback(prompt: String): String = runCatching { AppManagers.benBrain.answer(prompt).answer }.getOrElse { "Local Ben is unavailable: ${it.message ?: "unknown error"}" }

    private fun providerPref(key: String, default: ProviderId): ProviderId = runCatching { ProviderId.valueOf(prefs.getString(key, default.name) ?: default.name) }.getOrDefault(default)
    private fun spec(id: ProviderId): ProviderSpec = SPECS.first { it.id == id }

    companion object {
        private const val PREFS = "ben_ai_provider_manager"
        private const val KEY_PRIMARY = "primary"
        private const val KEY_FALLBACK = "fallback"
        private const val KEY_RESEARCH = "research"
        private const val KEY_COMBINE = "combine"
        private const val KEY_FREE_ONLY = "free_only"
        private const val MAX_PROMPT_CHARS = 24_000

        val SPECS = listOf(
            ProviderSpec(ProviderId.LOCAL_GEMMA, "Gemma 3 270M • on device", Kind.LOCAL, CostTier.LOCAL, "local", requiresKey = false, note = "Offline generation; governor controlled."),
            ProviderSpec(ProviderId.GEMINI_FIREBASE, "Gemini • Firebase AI Logic", Kind.GEMINI_REST, CostTier.FREE, "gemini-3.8-flash", requiresKey = false, note = "Uses the existing secure Firebase AI Logic path; no API key in the APK."),
            ProviderSpec(ProviderId.GEMINI_API, "Gemini Developer API", Kind.GEMINI_REST, CostTier.FREE, "gemini-2.5-flash-lite", "https://generativelanguage.googleapis.com/v1beta/models/", note = "BYOK direct API. Free tier depends on Google's current quota; hard free-only gate applies."),
            ProviderSpec(ProviderId.GROQ, "Groq", Kind.OPENAI_COMPATIBLE, CostTier.FREE, "openai/gpt-oss-20b", "https://api.groq.com/openai/v1/chat/completions", note = "Fast free/developer-tier inference; quota/rate limits apply."),
            ProviderSpec(ProviderId.ZAI_GLM, "Z.ai • GLM", Kind.OPENAI_COMPATIBLE, CostTier.QUOTA, "glm-5.1", "https://api.z.ai/api/paas/v4/chat/completions", note = "General API; availability/credits depend on the Z.ai account."),
            ProviderSpec(ProviderId.OPENROUTER_FREE, "OpenRouter • Free Models", Kind.OPENAI_COMPATIBLE, CostTier.FREE, "openrouter/free", "https://openrouter.ai/api/v1/chat/completions", note = "Routes among currently available free models; current free plan has rate limits."),
            ProviderSpec(ProviderId.CLOUDFLARE_WORKERS_AI, "Cloudflare Workers AI", Kind.OPENAI_COMPATIBLE, CostTier.QUOTA, "@cf/zai-org/glm-4.7-flash", "", note = "Requires Cloudflare account ID + API token; free allocation is daily and model-specific."),
            ProviderSpec(ProviderId.MISTRAL, "Mistral", Kind.OPENAI_COMPATIBLE, CostTier.QUOTA, "mistral-small-latest", "https://api.mistral.ai/v1/chat/completions", note = "Trial/free availability depends on the account and current plan."),
            ProviderSpec(ProviderId.SAMBANOVA, "SambaNova", Kind.OPENAI_COMPATIBLE, CostTier.QUOTA, "Meta-Llama-3.3-70B-Instruct", "https://api.sambanova.ai/v1/chat/completions", note = "Free account/quota availability is account dependent."),
            ProviderSpec(ProviderId.COHERE, "Cohere", Kind.COHERE, CostTier.FREE, "command-a-plus-05-2026", "https://api.cohere.com/v2/chat", note = "Trial key is free but rate limited; production limits differ."),
            ProviderSpec(ProviderId.GOOGLE_AI_SEARCH, "Google AI Mode • browser", Kind.GOOGLE_SEARCH, CostTier.FREE, "browser", requiresKey = false, note = "No Rovex API call; opens Google's user-facing AI Search experience."),
        )
    }
}

/** Small Android-Keystore-backed secret store. Keys never live in SharedPreferences plaintext. */
private class BenAiSecretStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("ben_ai_secrets", Context.MODE_PRIVATE)
    private val alias = "RovexBenAiSecretsV1"

    fun put(name: String, value: String) { if (value.isBlank()) { remove(name); return }; prefs.edit().putString(name, encrypt(value)).apply() }
    fun get(name: String): String? = prefs.getString(name, null)?.let { runCatching { decrypt(it) }.getOrNull() }
    fun remove(name: String) = prefs.edit().remove(name).apply()

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance("AES", "AndroidKeyStore")
        generator.init(android.security.keystore.KeyGenParameterSpec.Builder(alias, android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build())
        return generator.generateKey()
    }

    private fun encrypt(value: String): String {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(), GCMParameterSpec(128, iv))
        val out = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(iv + out, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        require(bytes.size > 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), StandardCharsets.UTF_8)
    }
}
