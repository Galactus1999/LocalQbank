package com.localqbank.library

import android.app.AlertDialog
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** User-facing control center for local/free/optional cloud AI providers. */
class BenAiProviderActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var manager: BenAiProviderManager
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        manager = BenAiProviderManager(this)
        SystemUi.immersive(this)
        setContentView(build())
    }

    private fun build(): ScrollView {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(28))
            background = ThemeManager.backgroundDrawable(this@BenAiProviderActivity)
        }
        root.addView(TextView(this).apply {
            text = "←  Back to Settings"
            textSize = 14f
            setTextColor(ThemeManager.accent(this@BenAiProviderActivity))
            setPadding(0, 0, 0, dp(8))
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(-1, dp(38)))
        root.addView(TextView(this).apply {
            text = "Ben AI Providers"
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ThemeManager.text(this@BenAiProviderActivity))
        })
        root.addView(TextView(this).apply {
            text = "One place to choose local Gemma, free cloud AI, GLM, Google AI Search and fallback combinations."
            textSize = 12f
            setTextColor(ThemeManager.muted(this@BenAiProviderActivity))
            setPadding(0, dp(3), 0, dp(14))
        })

        val policyCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(13), dp(14), dp(13))
            background = rounded(ThemeManager.elevated(this@BenAiProviderActivity), 18)
        }
        val freeSwitch = Switch(this).apply {
            text = "🔒 FREE-ONLY MODE"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ThemeManager.text(this@BenAiProviderActivity))
            isChecked = manager.plan().freeOnly
            setOnCheckedChangeListener { _, checked -> manager.setFreeOnly(checked); refresh() }
        }
        policyCard.addView(freeSwitch)
        policyCard.addView(TextView(this).apply {
            text = "When ON, Ben blocks providers classified as pay-as-you-go/unknown. It cannot accidentally spend money through this router."
            textSize = 10.5f
            setTextColor(ThemeManager.muted(this@BenAiProviderActivity))
            setPadding(0, dp(3), 0, dp(8))
        })
        policyCard.addView(button("Choose primary / fallback / research combination") { choosePlan() }, LinearLayout.LayoutParams(-1, dp(44)))
        root.addView(policyCard, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(10)) })

        root.addView(section("CURRENT ROUTING"))
        val planBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(planBox, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(8)) })

        root.addView(section("PROVIDERS"))
        val providerBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(providerBox)

        val googleSearch = button("Open Google AI Search / AI Mode") {
            showSearchDialog()
        }
        root.addView(googleSearch, LinearLayout.LayoutParams(-1, dp(46)).apply { setMargins(0, dp(10), 0, dp(8)) })
        root.addView(TextView(this).apply {
            text = "Google AI Mode is the browser route. It does not require a Gemini API key in Rovex. Gemini API / Firebase AI Logic are separate developer routes."
            textSize = 10.5f
            setTextColor(ThemeManager.muted(this@BenAiProviderActivity))
            setPadding(dp(3), 0, dp(3), 0)
        })

        refreshTargets = providerBox to planBox
        refreshSwitch = freeSwitch
        refresh()
        return ScrollView(this).apply { addView(root); isFillViewport = true }
    }

    private var refreshTargets: Pair<LinearLayout, LinearLayout>? = null
    private var refreshSwitch: Switch? = null

    private fun refresh() {
        val targets = refreshTargets ?: return
        val providerBox = targets.first
        val planBox = targets.second
        providerBox.removeAllViews()
        planBox.removeAllViews()
        val p = manager.plan()
        table(planBox, "Primary", label(p.primary))
        table(planBox, "Fallback", label(p.fallback))
        table(planBox, "Research", label(p.research))
        table(planBox, "Combination", if (p.combine) "Primary → fallback / research" else "Single selected provider")
        refreshSwitch?.isChecked = p.freeOnly

        manager.specs().forEach { spec ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(13), dp(12), dp(13), dp(10))
                background = rounded(ThemeManager.elevated(this@BenAiProviderActivity), 16)
            }
            val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            header.addView(TextView(this).apply {
                text = spec.label
                textSize = 14.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(ThemeManager.text(this@BenAiProviderActivity))
            }, LinearLayout.LayoutParams(0, -2, 1f))
            val cost = TextView(this).apply {
                text = when (spec.cost) {
                    BenAiProviderManager.CostTier.LOCAL -> "LOCAL"
                    BenAiProviderManager.CostTier.FREE -> "FREE"
                    BenAiProviderManager.CostTier.QUOTA -> "FREE/QUOTA"
                    BenAiProviderManager.CostTier.PAYG -> "PAID"
                    BenAiProviderManager.CostTier.UNKNOWN -> "CHECK"
                }
                textSize = 9.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(if (manager.isAllowed(spec.id)) Color.rgb(75, 210, 135) else Color.rgb(245, 160, 80))
            }
            header.addView(cost, LinearLayout.LayoutParams(dp(82), -2))
            card.addView(header)
            card.addView(TextView(this).apply {
                text = spec.note
                textSize = 10.5f
                setTextColor(ThemeManager.muted(this@BenAiProviderActivity))
                setPadding(0, dp(3), 0, dp(7))
            })
            val status = when {
                spec.id == BenAiProviderManager.ProviderId.LOCAL_GEMMA -> "On-device • no key"
                spec.id == BenAiProviderManager.ProviderId.GEMINI_FIREBASE -> "Firebase route • ${if (FirebaseAppAvailability.isConfigured(this)) "configured" else "not configured"}"
                spec.id == BenAiProviderManager.ProviderId.GOOGLE_AI_SEARCH -> "Browser • no API key"
                manager.hasKey(spec.id) -> "API key saved securely • model ${manager.model(spec.id)}"
                else -> "API key not configured"
            }
            card.addView(TextView(this).apply {
                text = status
                textSize = 10.5f
                setTextColor(ThemeManager.text(this@BenAiProviderActivity))
                setPadding(0, 0, 0, dp(7))
            })
            if (spec.requiresKey) {
                val keyButton = button(if (manager.hasKey(spec.id)) "CHANGE API KEY" else "ADD API KEY") { editKey(spec.id) }
                card.addView(keyButton, LinearLayout.LayoutParams(-1, dp(40)))
                card.addView(button("MODEL: ${manager.model(spec.id)}") { editModel(spec.id) }, LinearLayout.LayoutParams(-1, dp(38)).apply { setMargins(0, dp(4), 0, 0) })
            }
            card.addView(button("TEST CONNECTION") { test(spec.id) }, LinearLayout.LayoutParams(-1, dp(38)).apply { setMargins(0, dp(4), 0, 0) })
            card.addView(button("USE AS PRIMARY") { manager.setPrimary(spec.id); refresh() }, LinearLayout.LayoutParams(-1, dp(38)).apply { setMargins(0, dp(4), 0, 0) })
            providerBox.addView(card, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(8)) })
        }
    }

    private fun choosePlan() {
        val specs = manager.specs()
        fun choose(title: String, current: BenAiProviderManager.ProviderId, save: (BenAiProviderManager.ProviderId) -> Unit) {
            val labels = specs.map { label(it.id) }.toTypedArray()
            AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(labels, specs.indexOfFirst { it.id == current }) { d, which -> save(specs[which].id); d.dismiss(); refresh() }.show()
        }
        choose("Primary generation provider", manager.plan().primary) { manager.setPrimary(it) }
        choose("Fallback provider", manager.plan().fallback) { manager.setFallback(it) }
        choose("Research provider", manager.plan().research) { manager.setResearch(it) }
        AlertDialog.Builder(this).setTitle("Combine providers?").setMessage("When enabled, Ben may use the primary provider first and the fallback when the primary fails. Research remains a separate stage.")
            .setPositiveButton("ENABLE") { _, _ -> manager.setCombine(true); refresh() }
            .setNegativeButton("KEEP SINGLE") { _, _ -> manager.setCombine(false); refresh() }.show()
    }

    private fun editKey(id: BenAiProviderManager.ProviderId) {
        val input = EditText(this).apply { hint = "Paste API key"; inputType = 0x00000081; setSingleLine(true) }
        val message = "Stored encrypted with Android Keystore. It is not written to source control.\n\nProvider: ${label(id)}"
        AlertDialog.Builder(this).setTitle("API key").setMessage(message).setView(input)
            .setNeutralButton("REMOVE") { _, _ -> manager.clearKey(id); refresh() }
            .setPositiveButton("SAVE") { _, _ -> manager.saveKey(id, input.text.toString()); refresh() }
            .setNegativeButton("CANCEL", null).show()
    }

    private fun editModel(id: BenAiProviderManager.ProviderId) {
        val input = EditText(this).apply { setSingleLine(true); setText(manager.model(id)); selectAll() }
        AlertDialog.Builder(this).setTitle("Model ID").setMessage("Use the exact model identifier from the provider.\n\nDefault: ${manager.specs().first { it.id == id }.defaultModel}").setView(input)
            .setPositiveButton("SAVE") { _, _ -> manager.setModel(id, input.text.toString()); refresh() }
            .setNegativeButton("CANCEL", null).show()
    }

    private fun test(id: BenAiProviderManager.ProviderId) {
        if (id == BenAiProviderManager.ProviderId.GOOGLE_AI_SEARCH) { showSearchDialog(); return }
        val status = Toast.makeText(this, "Testing ${label(id)}…", Toast.LENGTH_LONG)
        status.show()
        scope.launch {
            val result = runCatching { manager.test(id) }
            result.onSuccess { r ->
                AlertDialog.Builder(this@BenAiProviderActivity).setTitle("Provider test passed").setMessage("${label(id)}\nModel: ${r.model}\nLatency: ${r.elapsedMs} ms\n\n${r.text.take(1000)}").setPositiveButton("OK", null).show()
            }.onFailure { e ->
                AlertDialog.Builder(this@BenAiProviderActivity).setTitle("Provider test failed").setMessage("${label(id)}\n\n${e.message ?: "Unknown error"}").setPositiveButton("OK", null).show()
            }
        }
    }

    private fun showSearchDialog() {
        val input = EditText(this).apply { hint = "PYQ / concept / topic"; minLines = 2 }
        AlertDialog.Builder(this).setTitle("Google AI Search")
            .setMessage("Rovex will preload a medical research prompt and open Google's AI Search experience in the browser.")
            .setView(input)
            .setPositiveButton("OPEN SEARCH") { _, _ -> openGoogleAiSearch(input.text.toString()) }
            .setNegativeButton("CANCEL", null).show()
    }

    private fun openGoogleAiSearch(query: String) {
        val q = query.trim().ifBlank { "Medical NEET-PG research: find relevant PYQs, related concepts, standard textbook explanations, exam traps and authoritative current sources. Do not invent PYQs or citations." }
        val prompt = "Analyze this medical study request for NEET-PG. Find relevant PYQs, related concepts, common differentials, high-yield exam traps, authoritative references and current evidence. Clearly distinguish verified sources from inference. Do not invent PYQs or citations.\n\nREQUEST:\n$q"
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?udm=50&q=" + Uri.encode(prompt))))
    }

    private fun label(id: BenAiProviderManager.ProviderId): String = manager.specs().first { it.id == id }.label
    private fun section(text: String) = TextView(this).apply { this.text = text; textSize = 11f; typeface = Typeface.DEFAULT_BOLD; setTextColor(ThemeManager.accent(this@BenAiProviderActivity)); setPadding(dp(2), dp(10), dp(2), dp(5)) }
    private fun table(parent: LinearLayout, key: String, value: String) { parent.addView(TextView(this).apply { text = "$key  •  $value"; textSize = 11f; setTextColor(ThemeManager.text(this@BenAiProviderActivity)); setPadding(dp(8), dp(5), dp(8), dp(5)) }) }
    private fun button(text: String, click: () -> Unit) = TextView(this).apply { this.text = text; textSize = 11f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setTextColor(ThemeManager.text(this@BenAiProviderActivity)); background = rounded(ThemeManager.panel(this@BenAiProviderActivity), 13); setPadding(dp(8), 0, dp(8), 0); setOnClickListener { click() } }
    private fun rounded(color: Int, radius: Int) = android.graphics.drawable.GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
