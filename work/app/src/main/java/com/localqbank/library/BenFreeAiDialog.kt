package com.localqbank.library

import android.app.AlertDialog
import android.app.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.*
import androidx.compose.ui.platform.ComposeView
import com.localqbank.library.ui.ai.BenAiComposeSurface
import com.localqbank.library.ui.ai.composeAccentColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private class DialogComposeOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    fun attach(dialog: Dialog) {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        val decor = dialog.window?.decorView
        decor?.setViewTreeLifecycleOwner(this)
        decor?.setViewTreeViewModelStoreOwner(this)
        decor?.setViewTreeSavedStateRegistryOwner(this)
        dialog.setOnShowListener { lifecycleRegistry.currentState = Lifecycle.State.RESUMED }
        dialog.setOnDismissListener {
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
            viewModelStore.clear()
        }
    }
}

class BenFreeAiDialog(private val activity: RenActivity, private val scope: CoroutineScope) {
    private fun dp(v:Int)= (v*activity.resources.displayMetrics.density).toInt()

    fun show(){
        val gateway=AppManagers.cloudAi
        val body=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(6),dp(20),dp(10))}
        body.addView(TextView(activity).apply{text="FREE AI PROVIDERS";textSize=19f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.text(activity));setPadding(0,0,0,dp(4))})
        body.addView(TextView(activity).apply{text="Local Ben stays first. Free cloud models are optional accelerators; keys are encrypted with Android Keystore.";textSize=11.5f;setTextColor(ThemeManager.muted(activity));setPadding(0,0,0,dp(10))})
        val neuralTelemetry = BenNeuralTelemetry.snapshot()
        body.addView(ComposeView(activity).apply {
            setContent {
                BenAiComposeSurface(
                    title = "Ben AI control surface",
                    subtitle = "One context path • local evidence first • provider adapters remain optional",
                    profile = currentBenExamProfile(activity).label,
                    localEvidence = neuralTelemetry.lastEvidenceCount,
                    neuralState = if (neuralTelemetry.lastVerified) "Verified" else "Deterministic",
                    dark = ThemeManager.isDark(activity),
                    primary = composeAccentColor(ThemeManager.accent(activity)),
                    surface = composeAccentColor(ThemeManager.dialogBg(activity)),
                    onSurface = composeAccentColor(ThemeManager.text(activity)),
                    onOpenContext = { Toast.makeText(activity, "Use the question Context panel to inspect the shared ContextPack", Toast.LENGTH_SHORT).show() },
                    onOpenEvidence = { Toast.makeText(activity, "Evidence count: ${neuralTelemetry.lastEvidenceCount}", Toast.LENGTH_SHORT).show() }
                )
            }
        }.apply { setPadding(0, 0, 0, dp(8)) }, LinearLayout.LayoutParams(-1, dp(132)))
        body.addView(TextView(activity).apply{
            text="TEST CONNECTED PROVIDERS";textSize=10.5f;typeface=Typeface.DEFAULT_BOLD;gravity=Gravity.CENTER;setTextColor(Color.WHITE)
            background=UiDrawableUtils.roundedDrawable(activity,ThemeManager.accent(activity),11f)
            setPadding(dp(8),0,dp(8),0)
            setOnClickListener{
                scope.launch{
                    val connected=gateway.configs().filter{it.enabled&&it.hasKey}
                    if(connected.isEmpty()){Toast.makeText(activity,"No enabled provider is configured",Toast.LENGTH_SHORT).show();return@launch}
                    val results=connected.map{cfg->cfg.provider.label+": "+runCatching{gateway.test(cfg.provider)}.fold({"PASS"},{"FAIL • "+(it.message ?: "unknown error")})}
                    AlertDialog.Builder(activity).setTitle("AI provider diagnostics").setMessage(results.joinToString("\n\n")).setPositiveButton("DONE",null).show()
                }
            }
        },LinearLayout.LayoutParams(-1,dp(40)).apply{setMargins(0,dp(4),0,dp(10))})
        BenCloudAiGateway.Provider.values().forEach{provider->
            val cfg=gateway.configs().first{it.provider==provider}
            val row=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;setPadding(0,dp(8),0,dp(8))}
            row.addView(TextView(activity).apply{text=provider.label;textSize=15f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.text(activity))})
            row.addView(TextView(activity).apply{text=if(cfg.hasKey) "${if(cfg.enabled)"Enabled" else "Saved • off"} • ${cfg.model}" else "Not connected";textSize=10.5f;setTextColor(ThemeManager.muted(activity))})
            val actions=LinearLayout(activity).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
            actions.addView(TextView(activity).apply{text=if(cfg.hasKey)"UPDATE KEY" else "CONNECT";gravity=Gravity.CENTER;textSize=10.5f;setTextColor(ThemeManager.accent(activity));background=UiDrawableUtils.roundedDrawable(activity, ThemeManager.explanationBg(activity),10f);setOnClickListener{showProviderKeyDialog(provider)}},LinearLayout.LayoutParams(0,dp(38),1f))
            actions.addView(TextView(activity).apply{text="TEST";gravity=Gravity.CENTER;textSize=10.5f;setTextColor(ThemeManager.text(activity));background=UiDrawableUtils.roundedDrawable(activity, ThemeManager.elevated(activity),10f);setOnClickListener{scope.launch{val r=runCatching{gateway.test(provider)};Toast.makeText(activity,if(r.isSuccess)"${provider.label}: connection OK" else "${provider.label}: ${r.exceptionOrNull()?.message ?: "failed"}",Toast.LENGTH_LONG).show()}}},LinearLayout.LayoutParams(0,dp(38),1f).apply{setMargins(dp(6),0,0,0)})
            val sw=Switch(activity).apply{text="Use";isChecked=cfg.enabled&&cfg.hasKey;setTextColor(ThemeManager.text(activity));setOnCheckedChangeListener{_,v->gateway.setEnabled(provider,v)}}
            actions.addView(sw,LinearLayout.LayoutParams(dp(72),dp(40)).apply{setMargins(dp(6),0,0,0)})
            row.addView(actions);body.addView(row)
        }
        body.addView(TextView(activity).apply{text="Free-first path: OpenRouter free router; Groq has a separate free tier with rate limits. DeepSeek is paid/low-cost and remains opt-in. Rovex never enables a provider automatically.";textSize=10.5f;setTextColor(ThemeManager.muted(activity));setPadding(0,dp(6),0,0)})
        val dialog=AlertDialog.Builder(activity).setTitle("Ben • Free AI").setView(body).setPositiveButton("DONE",null).create()
        DialogComposeOwner().attach(dialog)
        dialog.show()
    }

    private fun showProviderKeyDialog(provider:BenCloudAiGateway.Provider){
        val gateway=AppManagers.cloudAi
        val layout=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(4),dp(20),0)}
        layout.addView(TextView(activity).apply{text="${provider.label} API key";textSize=16f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.text(activity));setPadding(0,0,0,dp(5))})
        layout.addView(TextView(activity).apply{text="Get the key from the provider's official page. Rovex encrypts it locally and never logs it.";textSize=11.5f;setTextColor(ThemeManager.muted(activity));setPadding(0,0,0,dp(8))})
        val key=EditText(activity).apply{hint="Paste API key";inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD;setSingleLine(true)}
        layout.addView(key,LinearLayout.LayoutParams(-1,dp(52)))
        val model=EditText(activity).apply{hint="Model";setSingleLine(true);setText(gateway.configs().first{it.provider==provider}.model)}
        layout.addView(model,LinearLayout.LayoutParams(-1,dp(52)).apply{setMargins(0,dp(5),0,0)})
        val dialog=AlertDialog.Builder(activity).setTitle("Connect ${provider.label}").setView(layout).setNegativeButton("CANCEL",null).setNeutralButton("GET KEY"){_,_->runCatching{gateway.openKeyPage(activity,provider)}.onFailure{Toast.makeText(activity,"Could not open provider page",Toast.LENGTH_SHORT).show()}}.setPositiveButton("SAVE & TEST",null).create()
        dialog.setOnShowListener{dlg->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
                val value=key.text.toString().trim()
                if(value.isBlank()){key.error="API key required";return@setOnClickListener}
                val modelValue=model.text.toString().trim()
                runCatching{
                    gateway.saveKey(provider,value)
                    gateway.setModel(provider,modelValue)
                    gateway.setEnabled(provider,true)
                }.onFailure{
                    key.error=it.message ?: "Could not save activity API key"
                    Toast.makeText(activity,"${provider.label}: ${it.message ?: "key storage failed"}",Toast.LENGTH_LONG).show()
                    return@onFailure
                }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled=false
                scope.launch{
                    val result=runCatching{gateway.test(provider)}
                    if(!activity.isFinishing && !activity.isDestroyed){
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled=true
                        if(result.isSuccess){
                            dlg.dismiss()
                            Toast.makeText(activity,"${provider.label} connected and tested successfully",Toast.LENGTH_LONG).show()
                            activity.recreate()
                        }else{
                            gateway.setEnabled(provider,false)
                            Toast.makeText(activity,
                                "${provider.label}: key saved locally, but connection test failed: ${result.exceptionOrNull()?.message ?: "unknown error"}.",
                                Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
        dialog.show()
    }

}
