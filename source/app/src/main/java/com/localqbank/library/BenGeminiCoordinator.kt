package com.localqbank.library

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.Tool
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.ArrayDeque

/** Cloud research collaborator. Local Ben remains authoritative for local retrieval and study state. */
class BenGeminiCoordinator(context: Context) {
    data class Source(val title: String, val uri: String, val citationIndex: Int)
    data class Result(val answer: String, val sources: List<Source>, val grounded: Boolean, val signedInEmail: String?, val searchEntryPointHtml: String? = null)
    private val app = context.applicationContext
    private val frankensteinContext = FrankensteinContextEngine(app)

    fun auth(): FirebaseAuth? = runCatching {
        if (FirebaseAppAvailability.ensureInitialized(app) != null) FirebaseAuth.getInstance() else null
    }.getOrNull()

    suspend fun previousYearContext(question: Question): Result {
        val context = frankensteinContext.build(question)
        val prompt = buildString {
            append("Build an exam-context map for this exact medical QBank question.\n")
            append("Use the FRANKENSTEIN CONTEXT below as local evidence. Never invent exam names, years, recurrence, frequency, or PYQ status. If provenance does not establish PYQ status, call it a related local QBank/source question.\n")
            append("Group local rows by their recorded exam/source, identify recurring concepts and discriminator patterns, then add a concise current-evidence synthesis. Clearly distinguish LOCAL EVIDENCE from CURRENT WEB EVIDENCE.\n\n")
            append("CURRENT QUESTION:\n${question.text.take(4500)}\nKEYED ANSWER: ${question.correctAnswer.orEmpty()}\n\n")
            append(context.promptBlock())
        }
        return research(prompt)
    }

    suspend fun explainQuestion(question: Question): Result {
        val options = question.options.joinToString("\n") { "${it.label}. ${it.text}" }
        val prompt = buildString {
            append("Provide exam-oriented context for this exact medical QBank question.\n")
            append("Do not change or invent the QBank's keyed answer. Explain the tested concept, why the keyed answer is correct, the closest distractors and how to distinguish them, high-yield associated facts, common exam traps, and one short memory hook.\n")
            append("Prefer authoritative medical sources and current guidelines where relevant.\n\n")
            append("QUESTION:\n").append(question.text.take(6000)).append("\n\n")
            append("OPTIONS:\n").append(options.take(5000)).append("\n\n")
            append("KEYED ANSWER:\n").append(question.correctAnswer.orEmpty()).append("\n\n")
            append("EXISTING LOCAL EXPLANATION (supporting context only):\n").append(question.explanation.orEmpty().take(3000))
            append("\n\n")
            append(frankensteinContext.build(question).promptBlock())
        }
        return research(prompt)
    }

    suspend fun research(query: String): Result = withContext(Dispatchers.IO) {
        val clean = BenResearchInputPolicy.normalize(query)
            ?: return@withContext Result("Give Ben a concrete research question.", emptyList(), false, auth()?.currentUser?.email)
        val firebaseAuth = auth()
            ?: return@withContext Result("Gemini is not configured. Add Firebase configuration and enable Google sign-in.", emptyList(), false, null)
        val user = firebaseAuth.currentUser
            ?: return@withContext Result("Sign in with your Google account before using Gemini web research.", emptyList(), false, null)

        val config = BenGeminiConfig(app).snapshot()
        if (!config.enabled) {
            return@withContext Result("Gemini web research is currently disabled by Ben's remote safety configuration.", emptyList(), false, user.email)
        }
        if (!GeminiRequestLimiter.tryAcquire(user.uid, config.maxRequestsPerMinute)) {
            return@withContext Result("Gemini research rate limit reached. Try again shortly.", emptyList(), false, user.email)
        }
        val local = runCatching { AppManagers.benBrain.answer(clean) }.getOrNull()
        val localContext = buildString {
            append("Ben local context. Supporting context only; it is not current web evidence.\n")
            if (local != null) {
                append("Local intent: ").append(local.plan.intent ?: "general").append('\n')
                append("Local concepts: ").append(local.plan.concepts.joinToString(", ")).append('\n')
                append("Local specialist: ").append(local.plan.specialist).append('\n')
                append("Local confidence: ").append(local.confidence).append("/100\n")
                append("Local answer: ").append(local.answer.take(3500)).append('\n')
            }
        }
        val model = Firebase.ai(backend = GenerativeBackend.googleAI(), useLimitedUseAppCheckTokens = true).generativeModel(
            modelName = config.model,
            generationConfig = generationConfig { maxOutputTokens = config.maxOutputTokens },
            tools = listOf(Tool.googleSearch(), Tool.urlContext())
        )
        val prompt = """
You are Gemini, the web-research collaborator inside Ben (Dr. Frankenstein).
The user explicitly requested current web research.
Use Google Search when current or externally verifiable information matters. Prefer primary/official sources,
major medical organizations, peer-reviewed literature, and authoritative guidelines. Distinguish established
facts from uncertainty and conflicting evidence. Do not blindly accept Ben's local context. Return a concise, source-grounded synthesis that Ben can verify and present.
For every externally verifiable claim, cite the supporting web source using [1], [2], etc.
Keep citations adjacent to the claim they support. Never fabricate a citation number.
When a search result points to a primary/official page that needs deeper reading, use URL context.

$localContext
RESEARCH QUESTION:
$clean
""".trimIndent()
        val response = withTimeout(config.requestTimeoutMs) {
            model.generateContent(prompt)
        }
        val geminiText = response.text?.trim().orEmpty().ifBlank { "Gemini returned no textual result." }
        val metadata = response.candidates.firstOrNull()?.groundingMetadata
        val groundingChunks = metadata?.groundingChunks.orEmpty()
        // Preserve Gemini's original grounding-chunk indices. Re-numbering or de-duplicating
        // sources here can silently invalidate [n] citations generated by Gemini.
        val sources = groundingChunks.mapIndexedNotNull { index, chunk ->
            val web = chunk.web ?: return@mapIndexedNotNull null
            val uri = web.uri?.trim().orEmpty()
            val title = web.title?.trim().orEmpty().ifBlank { uri }
            if (uri.isBlank()) null else Source(title, uri, index + 1)
        }.take(24)
        val externalEvidence = metadata?.groundingSupports.orEmpty()
            .flatMap { support ->
                support.groundingChunkIndices.mapNotNull { sourceIndex ->
                    val chunk = groundingChunks.getOrNull(sourceIndex)?.web ?: return@mapNotNull null
                    val uri = chunk.uri?.trim().orEmpty()
                    val title = chunk.title?.trim().orEmpty().ifBlank { uri }
                    if (uri.isBlank()) null else BenAnswerVerifier.EvidenceItem(
                        index = sourceIndex + 1,
                        text = "${title.take(220)} — ${support.segment.text.take(900)}"
                    )
                }
            }
            .distinctBy { it.index to it.text }
            .take(24)
        // Frankenstein remains the final local gate/orchestrator: Gemini supplies current
        // external research, while grounding evidence is converted into verifier-readable
        // evidence items. Web evidence never becomes QBank truth or study-state authority.
        val benFinal = runCatching {
            AppManagers.benBrain.answer(
                clean,
                baseAnswer = geminiText,
                modelUsed = true,
                evidence = externalEvidence
            )
        }.getOrNull()
        val finalText = benFinal?.answer?.takeIf { it.isNotBlank() } ?: geminiText
        Result(finalText, sources, sources.isNotEmpty(), user.email, metadata?.searchEntryPoint?.renderedContent)
    }

    companion object {
        const val MODEL = "gemini-3.8-flash"
    }
}

/** Small in-process abuse/cost guard; Firebase App Check/auth remain the security boundary. */
private object GeminiRequestLimiter {
    private val lock = Any()
    private val requests = mutableMapOf<String, ArrayDeque<Long>>()

    fun tryAcquire(userId: String, limitPerMinute: Int): Boolean = synchronized(lock) {
        val now = System.currentTimeMillis()
        val queue = requests.getOrPut(userId) { ArrayDeque() }
        while (queue.isNotEmpty() && now - queue.first() >= 60_000L) queue.removeFirst()
        if (queue.size >= limitPerMinute) return false
        queue.addLast(now)
        if (requests.size > 64) {
            requests.entries.removeIf { (_, q) -> q.isEmpty() }
        }
        true
    }
}

object FirebaseAppAvailability {
    fun isConfigured(context: Context): Boolean = ensureInitialized(context) != null

    @Synchronized
    fun ensureInitialized(context: Context): com.google.firebase.FirebaseApp? = runCatching {
        val app = com.google.firebase.FirebaseApp.getApps(context).firstOrNull()
            ?: com.google.firebase.FirebaseApp.initializeApp(context)
            ?: return@runCatching null
        // Firebase may be initialized by FirebaseInitProvider before Ben is first used.
        // App Check therefore must not be installed only on the manual-initialization path.
        // Install once per process and keep the provider out of the application startup path.
        if (!appCheckInstalled) {
            if (com.localqbank.library.BuildConfig.DEBUG) {
                // Keep the debug-only provider out of release compilation/runtime.
                // debugImplementation is intentionally not visible to release source sets.
                val debugFactory = Class.forName(
                    "com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory"
                ).getMethod("getInstance").invoke(null) as com.google.firebase.appcheck.AppCheckProviderFactory
                FirebaseAppCheck.getInstance(app).installAppCheckProviderFactory(debugFactory)
            } else {
                FirebaseAppCheck.getInstance(app).installAppCheckProviderFactory(
                    com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory.getInstance()
                )
            }
            appCheckInstalled = true
        }
        app
    }.getOrNull()

    @Volatile private var appCheckInstalled = false
}
