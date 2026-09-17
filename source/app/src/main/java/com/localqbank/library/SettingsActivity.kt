package com.localqbank.library

import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider

/**
 * Thin Settings host: lifecycle, navigation, Activity Result plumbing and screen binding only.
 * Application engines remain owned by AppManagers and their existing authoritative managers.
 */
class SettingsActivity : AppCompatActivity() {
    companion object {
        const val REQUEST_EMBEDDING_MODEL = 8401
        const val REQUEST_GENERATIVE_MODEL = 8402
        const val REQUEST_EMBEDDING_TOKENIZER = 8403
    }

    internal val neuralModelManager get() = (application as LocalQBankApplication).appContainer.neuralModelManager
    private val viewModel by lazy {
        ViewModelProvider(this, (application as LocalQBankApplication).appContainer.settingsViewModelFactory())[SettingsViewModel::class.java]
    }
    private var pendingDocumentRequest: Int = -1

    private val documentPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val request = pendingDocumentRequest
        pendingDocumentRequest = -1
        if (uri == null || request < 0) return@registerForActivityResult
        handleDocumentResult(request, uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppManagers.initialize(applicationContext)
        SystemUi.immersive(this)
        val root = SettingsScreen(this, viewModel).buildRoot()
        if (intent.getStringExtra("section") == null) {
            val host = FrameLayout(this)
            host.addView(root, FrameLayout.LayoutParams(-1, -1))
            val aiButton = TextView(this).apply {
                text = "🧠  AI PROVIDERS"
                textSize = 10.5f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(ThemeManager.accent(this@SettingsActivity))
                    cornerRadius = 18f * resources.displayMetrics.density
                }
                elevation = 8f * resources.displayMetrics.density
                contentDescription = "Open Ben AI Providers"
                setOnClickListener { startActivity(Intent(this@SettingsActivity, BenAiProviderActivity::class.java)) }
            }
            val density = resources.displayMetrics.density
            host.addView(aiButton, FrameLayout.LayoutParams((142 * density).toInt(), (44 * density).toInt()).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                setMargins(0, 0, (14 * density).toInt(), (16 * density).toInt())
            })
            setContentView(host)
        } else {
            setContentView(root)
        }
        AdaptiveTypographyManager.apply(root)
    }

    internal fun openSettingsDocument(intent: Intent, requestCode: Int) {
        pendingDocumentRequest = requestCode
        documentPicker.launch(arrayOf(intent.type ?: "application/octet-stream", "*/*"))
    }

    private fun handleDocumentResult(requestCode: Int, uri: android.net.Uri) {
        val manager = neuralModelManager
        when (requestCode) {
            REQUEST_EMBEDDING_TOKENIZER -> {
                val installed = manager.importEmbeddingGemmaTokenizer(uri)
                Toast.makeText(
                    this,
                    if (installed != null) "EmbeddingGemma tokenizer installed" else "Tokenizer import failed or file is invalid",
                    Toast.LENGTH_LONG
                ).show()
            }
            REQUEST_EMBEDDING_MODEL, REQUEST_GENERATIVE_MODEL -> {
                val profile = if (requestCode == REQUEST_EMBEDDING_MODEL) {
                    BenNeuralModelRegistry.embeddingGemma300m
                } else {
                    BenNeuralModelRegistry.gemma3_270m
                }
                val installed = manager.importModel(uri, profile)
                Toast.makeText(
                    this,
                    if (installed != null) "${profile.name} installed; inference remains governor-gated" else "Model import failed or file format was invalid",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        viewModel.refresh()
        recreate()
    }
}
