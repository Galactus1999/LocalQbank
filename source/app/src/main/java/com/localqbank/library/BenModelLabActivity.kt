package com.localqbank.library

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Direct, user-visible laboratory for proving the optional Ben models actually execute. */
class BenModelLabActivity : AppCompatActivity() {
    private val neuralModelManager get() = (application as LocalQBankApplication).appContainer.neuralModelManager
    private lateinit var status: TextView
    private lateinit var output: TextView
    private lateinit var prompt: EditText
    private lateinit var first: EditText
    private lateinit var second: EditText
    private val embeddingCode = 8501
    private val generativeCode = 8502
    private val tokenizerCode = 8503
    private var pendingRequest = -1

    private val documentPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val code = pendingRequest
        pendingRequest = -1
        if (uri != null && code >= 0) handleDocument(code, uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppManagers.initialize(applicationContext)
        SystemUi.immersive(this)
        setContentView(build())
    }

    private fun build(): ScrollView {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(28))
            background=ThemeManager.backgroundDrawable(this@BenModelLabActivity)
        }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(TextView(this).apply {
            text = "‹"; textSize = 32f; gravity = Gravity.CENTER
            setTextColor(ThemeManager.text(this@BenModelLabActivity)); setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(42), dp(48)))
        head.addView(TextView(this).apply {
            text = "Ben Model Lab"; textSize = 24f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(ThemeManager.text(this@BenModelLabActivity)); setPadding(dp(6), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(head)
        root.addView(label("Direct model access • use the permanent Test & Diagnostics Center for reproducible reports"), lpWrap(0, 10))

        val state = card()
        state.addView(title("RUNTIME"))
        status = label("")
        state.addView(status)
        state.addView(button("RUN ISOLATED BEN IPC TEST") { startActivity(Intent(this, RovexDiagnosticsDataCenter::class.java).apply { putExtra("OPEN_IPC", true) }) }, lp(0, 8))
        state.addView(button("VIEW LAST IPC DIAGNOSTIC") { showLastIpcReport() }, lp(0, 4))
        state.addView(button("OPEN ISOLATED EMBEDDINGGEMMA TEST") { startActivity(Intent(this, RovexDiagnosticsDataCenter::class.java)) }, lp(0, 4))
        root.addView(state, cardLp(0, 10))

        val gem = card()
        gem.addView(title("Gemma 3 270M • Direct Chat"))
        gem.addView(label("Generative model. Ask it directly; this does not modify Rovex data."), lpWrap(0, 6))
        gem.addView(button(if (installed(BenNeuralModelRegistry.gemma3_270m)) "REPLACE GEMMA MODEL" else "IMPORT GEMMA 3 270M") { pick(generativeCode) }, lp(0, 8))
        prompt = edit("Talk directly to Gemma…", 3)
        gem.addView(prompt, LinearLayout.LayoutParams(-1, dp(104)).apply { setMargins(0, dp(8), 0, dp(8)) })
        gem.addView(button("SEND TO GEMMA") { runGemma() }, lp(0, 4))
        output = label("Gemma output will appear here.").apply {
            textSize = 14f; setTextColor(ThemeManager.text(this@BenModelLabActivity)); setPadding(dp(12), dp(12), dp(12), dp(12))
            background = rounded(ThemeManager.elevated(this@BenModelLabActivity), 14)
        }
        gem.addView(output, lpWrap(0, 8))
        root.addView(gem, cardLp(0, 10))

        val emb = card()
        emb.addView(title("EmbeddingGemma 300M • Semantic Lab"))
        emb.addView(label("EmbeddingGemma converts text into semantic vectors for retrieval/similarity."), lpWrap(0, 6))
        emb.addView(button(if (installed(BenNeuralModelRegistry.embeddingGemma300m)) "REPLACE EMBEDDINGGEMMA" else "IMPORT EMBEDDINGGEMMA 300M") { pick(embeddingCode) }, lp(0, 8))
        emb.addView(label("Tokenizer: ${if (neuralModelManager.installedEmbeddingGemmaTokenizer() != null) "INSTALLED" else "NOT INSTALLED • required for execution"}"), lpWrap(0, 4))
        emb.addView(button(if (neuralModelManager.installedEmbeddingGemmaTokenizer() == null) "IMPORT EMBEDDINGGEMMA TOKENIZER" else "REPLACE EMBEDDINGGEMMA TOKENIZER") { pick(tokenizerCode) }, lp(0, 8))
        first = edit("First text / question", 2)
        second = edit("Second text / evidence", 2)
        emb.addView(first, lp(0, 6)); emb.addView(second, lp(0, 6))
        emb.addView(button("COMPARE SEMANTICALLY") { runEmbedding() }, lp(0, 8))
        emb.addView(button("OPEN FULL TEST & DIAGNOSTICS") { startActivity(Intent(this, RovexDiagnosticsDataCenter::class.java)) }, lp(0, 4))
        root.addView(emb, cardLp(0, 10))

        root.addView(label("Interactive inference is foreground-only and remains governed by RAM, thermal, power-save and the Ben resource policy."), lpWrap(0, 4))
        refresh()
        return ScrollView(this).apply { addView(root); isFillViewport = true }
    }

    private fun showLastIpcReport() {
        val report = BenIpcDiagnosticRecorder.latest(this)
        if (report.isNullOrBlank()) {
            AlertDialogCompat.show(this, "Ben IPC diagnostic", "No durable IPC report exists yet. Run the isolated IPC test from Test & Diagnostics.")
        } else {
            AlertDialogCompat.show(this, "Last Ben IPC diagnostic", report.take(12000))
        }
    }

    private fun runGemma() {
        val q = prompt.text.toString().trim()
        if (q.isBlank()) { prompt.error = "Enter a prompt"; return }
        output.text = "Gemma is loading/running…"
        BenNeuralTelemetry.begin("Direct Gemma chat")
        BenNeuralTelemetry.stage(BenNeuralTelemetry.Stage.GENERATION, "Direct Gemma generation", "Gemma 3 270M", "Generation")
        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching { BenInferenceProcessClient(this@BenModelLabActivity).use { it.generateSuspend(q, 160) } }
            withContext(Dispatchers.Main) {
                val text = result.getOrNull()
                if (result.isFailure || text == null) {
                    output.text = "Gemma did not run: ${result.exceptionOrNull()?.message ?: "unknown runtime error"}. Open the diagnostics center."
                } else {
                    output.text = text
                    BenNeuralTelemetry.complete(0, false, 0, 0L, true)
                }
                refresh()
            }
        }
    }

    private fun runEmbedding() {
        val a = first.text.toString().trim()
        val b = second.text.toString().trim()
        if (a.isBlank() || b.isBlank()) { Toast.makeText(this, "Enter both texts", Toast.LENGTH_SHORT).show(); return }
        BenAiRuntimePolicy(this).resetCircuit()
        Toast.makeText(this, "EmbeddingGemma running…", Toast.LENGTH_SHORT).show()
        BenNeuralTelemetry.begin("Direct EmbeddingGemma test")
        BenNeuralTelemetry.stage(BenNeuralTelemetry.Stage.SEMANTIC_RERANK, "Semantic similarity", "EmbeddingGemma 300M", "Embedding")
        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching { BenInferenceProcessClient(this@BenModelLabActivity).use { it.compareSuspend(a, b) } }.getOrNull()
            withContext(Dispatchers.Main) {
                if (result == null) {
                    Toast.makeText(this@BenModelLabActivity, "EmbeddingGemma did not run. Open Test & Diagnostics.", Toast.LENGTH_LONG).show()
                } else {
                    BenNeuralTelemetry.complete(2, true, (result.coerceIn(0.0, 1.0) * 100).toInt(), 0L, true)
                    AlertDialogCompat.show(this@BenModelLabActivity, "EmbeddingGemma result", "Cosine similarity: ${"%.4f".format(result)}")
                }
                refresh()
            }
        }
    }

    private fun pick(code: Int) {
        pendingRequest = code
        documentPicker.launch(arrayOf("application/octet-stream", "*/*"))
    }

    private fun handleDocument(code: Int, uri: android.net.Uri) {
        when (code) {
            tokenizerCode -> {
                val installed = neuralModelManager.importEmbeddingGemmaTokenizer(uri)
                Toast.makeText(this, if (installed != null) "EmbeddingGemma sentencepiece.model installed" else "Tokenizer import failed or file is invalid", Toast.LENGTH_LONG).show()
            }
            embeddingCode, generativeCode -> {
                val profile = if (code == embeddingCode) BenNeuralModelRegistry.embeddingGemma300m else BenNeuralModelRegistry.gemma3_270m
                val installed = neuralModelManager.importModel(uri, profile)
                Toast.makeText(this, if (installed != null) "${profile.name} installed" else "Import failed: incompatible artifact or size", Toast.LENGTH_LONG).show()
            }
        }
        refresh()
    }

    private fun refresh() {
        if (!::status.isInitialized) return
        val m = neuralModelManager
        val e = m.installed(BenNeuralModelRegistry.embeddingGemma300m)
        val g = m.installed(BenNeuralModelRegistry.gemma3_270m)
        val gov = BenAiResourceGovernor(this).snapshot(true)
        val policy = BenAiRuntimePolicy(this)
        status.text = buildString {
            append("Gemma 3 270M: ${if (g != null) "INSTALLED (${g.bytes / (1024 * 1024)} MB)" else "NOT INSTALLED"}\n")
            append("EmbeddingGemma 300M: ${if (e != null) "INSTALLED (${e.bytes / (1024 * 1024)} MB)" else "NOT INSTALLED"}\n")
            append("EmbeddingGemma tokenizer: ${if (m.installedEmbeddingGemmaTokenizer() != null) "INSTALLED" else "NOT INSTALLED"}\n")
            append("Policy: ${if (policy.enabled) "ARMED" else "OFF"} • circuit ${when (policy.circuitState) { BenAiRuntimePolicy.CircuitState.OPEN -> "OPEN"; BenAiRuntimePolicy.CircuitState.HALF_OPEN -> "HALF-OPEN"; else -> "closed" }}\n")
            append("RAM ${gov.availableMemoryMb} MB • thermal ${gov.thermalStatus} • power-save ${if (gov.powerSave) "ON" else "OFF"}\n")
            append("Last: ${BenNeuralTelemetry.snapshot().stageDetail}")
        }
    }

    private fun installed(profile: BenNeuralModelRegistry.ModelProfile) = neuralModelManager.installed(profile) != null
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun card() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(14), dp(14), dp(14)); background = rounded(ThemeManager.elevated(this@BenModelLabActivity), 18) }
    private fun title(t: String) = TextView(this).apply { text = t; textSize = 17f; typeface = Typeface.DEFAULT_BOLD; setTextColor(ThemeManager.text(this@BenModelLabActivity)) }
    private fun label(t: String) = TextView(this).apply { text = t; textSize = 12f; setTextColor(ThemeManager.muted(this@BenModelLabActivity)) }
    private fun edit(h: String, lines: Int) = EditText(this).apply { hint = h; textSize = 14f; setSingleLine(false); minLines = lines; setTextColor(ThemeManager.text(this@BenModelLabActivity)); setHintTextColor(ThemeManager.muted(this@BenModelLabActivity)); setPadding(dp(12), dp(9), dp(12), dp(9)); background = rounded(ThemeManager.elevated(this@BenModelLabActivity), 12) }
    private fun button(t: String, click: () -> Unit) = TextView(this).apply { text = t; textSize = 12f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setTextColor(Color.WHITE); background = rounded(ThemeManager.accent(this@BenModelLabActivity), 13); setOnClickListener { click() } }
    private fun lp(top: Int, bottom: Int) = LinearLayout.LayoutParams(-1, dp(44)).apply { setMargins(0, dp(top), 0, dp(bottom)) }
    private fun lpWrap(top: Int, bottom: Int) = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(top), 0, dp(bottom)) }
    private fun cardLp(top: Int, bottom: Int) = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(top), 0, dp(bottom)) }
    private fun rounded(c: Int, r: Int) = GradientDrawable().apply { setColor(c); cornerRadius = dp(r).toFloat() }
    override fun onResume() { super.onResume(); refresh() }
}

private object AlertDialogCompat {
    fun show(activity: Activity, title: String, message: String) = android.app.AlertDialog.Builder(activity).setTitle(title).setMessage(message).setPositiveButton("OK", null).show()
}
