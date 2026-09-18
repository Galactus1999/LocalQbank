package com.localqbank.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Provider-neutral cloud text gateway. It is an accelerator only: deterministic Ben/RAG/verifier
 * remain authoritative. User-owned API keys are encrypted with Android Keystore and never logged.
 *
 * Current provider defaults are deliberately free-first:
 * OpenRouter free router -> Groq GPT-OSS 20B -> DeepSeek when the user supplies a key.
 * Proprietary model providers are intentionally kept outside this provider layer.
 */
class BenCloudAiGateway(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    enum class Provider(val id: String, val label: String, val endpoint: String, val defaultModel: String, val keyUrl: String) {
        OPENROUTER("openrouter", "OpenRouter • Free", "https://openrouter.ai/api/v1/chat/completions", "openrouter/free", "https://openrouter.ai/settings/keys"),
        GROQ("groq", "Groq • Free tier", "https://api.groq.com/openai/v1/chat/completions", "openai/gpt-oss-20b", "https://console.groq.com/keys"),
        DEEPSEEK("deepseek", "DeepSeek • Low-cost", "https://api.deepseek.com/chat/completions", "deepseek-flash", "https://platform.deepseek.com/api_keys")
    }

    data class Config(val provider: Provider, val enabled: Boolean, val model: String, val hasKey: Boolean)
    data class Result(val text: String, val provider: Provider, val model: String, val fallbackUsed: Boolean)

    fun configs(): List<Config> = Provider.values().map { p ->
        Config(p, prefs.getBoolean(enabledKey(p), false), prefs.getString(modelKey(p), p.defaultModel).orEmpty().ifBlank { p.defaultModel }, keyStore.get(p) != null)
    }

    fun setEnabled(provider: Provider, enabled: Boolean) { prefs.edit().putBoolean(enabledKey(provider), enabled).apply() }
    fun setModel(provider: Provider, model: String) { prefs.edit().putString(modelKey(provider), model.trim().ifBlank { provider.defaultModel }).apply() }
    fun saveKey(provider: Provider, key: String) {
        val normalized=key.trim()
        require(normalized.length >= 12) { "API key looks too short. Paste the complete key." }
        keyStore.put(provider, normalized)
    }
    fun clearKey(provider: Provider) { keyStore.remove(provider); setEnabled(provider, false) }
    fun keyUrl(provider: Provider): String = provider.keyUrl

    fun openKeyPage(context: Context, provider: Provider) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(provider.keyUrl)))
    }

    suspend fun generate(prompt: String, system: String = DEFAULT_SYSTEM, maxTokens: Int = 900, timeoutMs: Long = 9_000L): Result? = withContext(Dispatchers.IO) {
        val priority=listOf(Provider.OPENROUTER,Provider.GROQ,Provider.DEEPSEEK)
        val ordered=priority.mapNotNull{p->configs().firstOrNull{it.provider==p&&it.enabled&&it.hasKey}}
        if (ordered.isEmpty()) return@withContext null
        var fallback = false
        for (cfg in ordered) {
            val key = keyStore.get(cfg.provider) ?: continue
            try {
                val text = withTimeout(timeoutMs) { call(cfg.provider, cfg.model, key, system, prompt, maxTokens) }
                return@withContext Result(text, cfg.provider, cfg.model, fallback)
            } catch (t: CancellationException) {
                throw t
            } catch (t: Exception) {
                fallback = true
                Log.w(TAG, "Cloud provider ${cfg.provider.id} failed: ${t.message}")
            }
        }
        null
    }

    suspend fun test(provider: Provider): Result = withContext(Dispatchers.IO) {
        val cfg = configs().first { it.provider == provider }
        val key = keyStore.getOrThrow(provider)
        val text = withTimeout(20_000L) { call(provider, cfg.model, key, "You are a connectivity test. Answer exactly: BEN CLOUD OK", "Connectivity test.", 24) }
        Result(text, provider, cfg.model, false)
    }

    private fun call(provider: Provider, model: String, apiKey: String, system: String, prompt: String, maxTokens: Int): String {
        require(provider.endpoint.startsWith("https://")) { "Cloud provider endpoint must use HTTPS." }
        val conn = (URL(provider.endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 5_000
            readTimeout = 8_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Rovex/${BuildConfig.VERSION_NAME} Android")
            if (provider == Provider.OPENROUTER) {
                setRequestProperty("HTTP-Referer", "https://github.com/Galactus1999/LocalQbank")
                setRequestProperty("X-Title", "Rovex")
            }
        }
        try {
            val body = JSONObject().apply {
                put("model", model)
                put("messages", JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", system))
                    put(JSONObject().put("role", "user").put("content", prompt))
                })
                put("max_tokens", maxTokens.coerceIn(64, 8192))
                put("temperature", 0.2)
                put("stream", false)
            }.toString()
            conn.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val raw = BufferedReader(InputStreamReader(stream ?: conn.inputStream, StandardCharsets.UTF_8)).use { it.readText() }
            if (code !in 200..299) throw IllegalStateException("${provider.label} HTTP $code: ${extractError(raw)}")
            val json = JSONObject(raw)
            val choices = json.optJSONArray("choices") ?: throw IllegalStateException("${provider.label} returned no choices.")
            val message = choices.optJSONObject(0)?.optJSONObject("message")
            val content = message?.opt("content")
            val text = when(content){
                is String -> content
                is JSONArray -> buildString { for(i in 0 until content.length()){ val part=content.optJSONObject(i); append(part?.optString("text").orEmpty()) } }
                else -> ""
            }.trim()
            if (text.isBlank()) throw IllegalStateException("${provider.label} returned empty text.")
            return text
        } finally {
            conn.disconnect()
        }
    }

    private fun extractError(raw: String): String = runCatching {
        val o = JSONObject(raw)
        o.optJSONObject("error")?.optString("message")?.ifBlank { raw.take(500) } ?: raw.take(500)
    }.getOrDefault(raw.take(500))

    private fun enabledKey(p: Provider) = "enabled_${p.id}"
    private fun modelKey(p: Provider) = "model_${p.id}"

    private val keyStore = SecretKeyStore(app)

    companion object {
        private const val TAG = "BenCloudAi"
        private const val PREFS = "ben_cloud_ai"
        private const val DEFAULT_SYSTEM = "You are a concise cloud reasoning collaborator inside Ben. Never override deterministic QBank truth. Distinguish uncertainty and avoid inventing medical evidence."
    }
}

/** Small Android Keystore-backed AES/GCM secret store for user-supplied provider API keys. */
private class SecretKeyStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("ben_cloud_secrets", Context.MODE_PRIVATE)
    private val alias = "RovexBenCloudKey"

    @Synchronized
    fun put(provider: BenCloudAiGateway.Provider, value: String) {
        require(value.isNotBlank()) { "API key cannot be blank." }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        // Android Keystore AES/GCM keys use randomized encryption: never supply an IV
        // during ENCRYPT_MODE initialization. Keystore generates the IV and cipher.iv
        // is persisted with the ciphertext for the matching DECRYPT_MODE operation.
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        require(iv.size == 12) { "Unexpected AES-GCM IV length." }
        val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        val encoded = Base64.encodeToString(iv + encrypted, Base64.NO_WRAP)

        // Use commit() here because SAVE & TEST must not report success before the
        // encrypted value is durably accepted by SharedPreferences.
        if (!prefs.edit().putString(provider.id, encoded).commit()) {
            throw IllegalStateException("Encrypted API key could not be persisted.")
        }

        // Read-back verification proves that the exact persisted ciphertext can be
        // decrypted by the current Keystore key before the UI marks the key as saved.
        val verified = getOrThrow(provider)
        if (verified != value) {
            prefs.edit().remove(provider.id).commit()
            throw IllegalStateException("Encrypted API key failed storage verification.")
        }
    }

    fun get(provider: BenCloudAiGateway.Provider): String? =
        runCatching { getOrThrow(provider) }.getOrNull()

    @Synchronized
    fun getOrThrow(provider: BenCloudAiGateway.Provider): String {
        val raw = prefs.getString(provider.id, null)
            ?: throw IllegalStateException("No API key saved for ${provider.label}.")
        return runCatching {
            val bytes = Base64.decode(raw, Base64.NO_WRAP)
            require(bytes.size > 12) { "Stored API key data is invalid." }
            val iv = bytes.copyOfRange(0, 12)
            val ciphertext = bytes.copyOfRange(12, bytes.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
        }.getOrElse {
            throw IllegalStateException(
                "Saved API key could not be decrypted. Please save the key again."
            )
        }
    }

    @Synchronized
    fun remove(provider: BenCloudAiGateway.Provider) {
        prefs.edit().remove(provider.id).commit()
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build())
        return generator.generateKey()
    }
}
