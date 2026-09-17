package com.localqbank.library

import android.content.Context
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.TimeUnit

/**
 * Production-safe Gemini configuration boundary.
 *
 * Model/prompt limits are remotely adjustable so a model retirement or prompt correction does
 * not require an APK release. Defaults remain deterministic and safe when Remote Config is
 * unavailable (offline, missing Firebase config, or an old cached configuration).
 */
class BenGeminiConfig(context: Context) {
    private val app = context.applicationContext
    private val remote by lazy {
        FirebaseAppAvailability.ensureInitialized(app)?.let { FirebaseRemoteConfig.getInstance(it) }
    }

    suspend fun snapshot(): Snapshot = withContext(Dispatchers.IO) {
        cached?.takeIf { System.currentTimeMillis() - cachedAt < CACHE_MS }?.let { return@withContext it }
        val rc = remote ?: return@withContext Snapshot.DEFAULT
        runCatching {
            rc.setConfigSettingsAsync(
                remoteConfigSettings {
                    minimumFetchIntervalInSeconds = if (BuildConfig.DEBUG) 0 else 3600
                    fetchTimeoutInSeconds = 8
                }
            )
            rc.setDefaultsAsync(mapOf(
                KEY_MODEL to Snapshot.DEFAULT.model,
                KEY_MAX_OUTPUT to Snapshot.DEFAULT.maxOutputTokens.toLong(),
                KEY_TIMEOUT to Snapshot.DEFAULT.requestTimeoutMs,
                KEY_ENABLED to Snapshot.DEFAULT.enabled,
                KEY_RPM to Snapshot.DEFAULT.maxRequestsPerMinute.toLong()
            ))
            withTimeoutOrNull(9_000L) { Tasks.await(rc.fetchAndActivate(), 9, TimeUnit.SECONDS) }
            Snapshot(
                model = rc.getString(KEY_MODEL).trim().ifBlank { Snapshot.DEFAULT.model },
                maxOutputTokens = rc.getLong(KEY_MAX_OUTPUT).toInt().coerceIn(256, 8192),
                requestTimeoutMs = rc.getLong(KEY_TIMEOUT).coerceIn(8_000L, 90_000L),
                enabled = rc.getBoolean(KEY_ENABLED),
                maxRequestsPerMinute = rc.getLong(KEY_RPM).toInt().coerceIn(1, 30)
            ).also { cached = it; cachedAt = System.currentTimeMillis() }
        }.getOrElse { Snapshot.DEFAULT }
    }

    data class Snapshot(
        val model: String,
        val maxOutputTokens: Int,
        val requestTimeoutMs: Long,
        val enabled: Boolean,
        val maxRequestsPerMinute: Int
    ) {
        companion object {
            val DEFAULT = Snapshot(
                model = "gemini-3.8-flash",
                maxOutputTokens = 3072,
                requestTimeoutMs = 45_000L,
                enabled = true,
                maxRequestsPerMinute = 8
            )
        }
    }

    @Volatile private var cached: Snapshot? = null
    @Volatile private var cachedAt: Long = 0L

    companion object {
        private const val CACHE_MS = 300_000L
        const val KEY_MODEL = "ben_gemini_model"
        const val KEY_MAX_OUTPUT = "ben_gemini_max_output_tokens"
        const val KEY_TIMEOUT = "ben_gemini_request_timeout_ms"
        const val KEY_ENABLED = "ben_gemini_enabled"
        const val KEY_RPM = "ben_gemini_requests_per_minute"
    }
}

