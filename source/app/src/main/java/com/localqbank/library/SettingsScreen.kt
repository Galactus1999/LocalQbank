package com.localqbank.library

import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.graphics.Typeface
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle

/** Presentation-only renderer for Settings. It does not own application engines or persistence. */
class SettingsScreen(private val activity: SettingsActivity, private val viewModel: SettingsViewModel) {
    private val lifecycleScope get() = activity.lifecycleScope
    private val neuralModelManager get() = activity.neuralModelManager
    private val section: String get() = activity.intent.getStringExtra("section") ?: "all"
    private val embeddingModelRequestCode = SettingsActivity.REQUEST_EMBEDDING_MODEL
    private val generativeModelRequestCode = SettingsActivity.REQUEST_GENERATIVE_MODEL
    private val embeddingTokenizerRequestCode = SettingsActivity.REQUEST_EMBEDDING_TOKENIZER
    private fun dp(v:Int)= (v*activity.resources.displayMetrics.density).toInt()

    fun buildRoot():ScrollView = build()

    private fun build():ScrollView{
        if(section=="all") return buildSettingsMenu()
        val root=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(12),dp(18),dp(28));background=ThemeManager.backgroundDrawable(activity)}
        root.addView(TextView(activity).apply{
            text="←  Settings";textSize=14f;gravity=Gravity.CENTER_VERTICAL;setTextColor(ThemeManager.accent(activity));setPadding(0,0,0,dp(8));setOnClickListener{activity.finish()}
        },LinearLayout.LayoutParams(-1,dp(38)))
        val titleText=when(section){
            "adaptive"->"Adaptive Engines"
            "adaptive_study"->"Adaptive Study"
            "performance"->"Performance & Resources"
            "ben_safety"->"Ben Safety"
            "privacy"->"Privacy & Security"
            "diagnostics"->"Test & Diagnostics Center"
            "appearance"->"Appearance & Themes"
            "fonts"->"Quiz Typography"
            else->"Settings"
        }
        root.addView(TextView(activity).apply{text=titleText;textSize=27f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.text(activity));setPadding(0,0,0,dp(6))})
        root.addView(TextView(activity).apply{text=when(section){
            "adaptive"->"Ben runtime, cognition and neural controls"
            "adaptive_study"->"Learning behaviour, strategy and SRS signals"
            "performance"->"Device resources, thermal, battery and resilience"
            "ben_safety"->"Independent safety controls and emergency stop"
            "privacy"->"Protected data and capability boundaries"
            "diagnostics"->"Full model tests, runtime diagnostics and exportable reports"
            else->"Dedicated settings controls"
        };textSize=13f;setTextColor(ThemeManager.muted(activity));setPadding(0,0,0,dp(18))})
        when(section){
            "appearance"->addAppearance(root)
            "fonts"->addFonts(root)
            "adaptive"->addAdaptiveEngine(root)
            "adaptive_study"->addAdaptiveStudy(root)
            "performance"->addPerformance(root)
            "ben_safety"->addBenSafetyPage(root)
            "privacy"->addPrivacyPage(root)
            "diagnostics"->addDiagnosticsPage(root)
        }
        return ScrollView(activity).apply{addView(root);isFillViewport=true}
    }

    private fun buildSettingsMenu():ScrollView{
        val state = AppManagers.adaptive.state()
        val root=LinearLayout(activity).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(dp(18),dp(18),dp(18),dp(30))
            background=ThemeManager.backgroundDrawable(activity)
        }

        fun card(background:Int = ThemeManager.elevated(activity), radius:Int = 18):LinearLayout =
            LinearLayout(activity).apply{
                orientation=LinearLayout.VERTICAL
                setPadding(dp(14),dp(13),dp(14),dp(13))
                this.background=rounded(background,radius)
            }
        fun muted(t:String,size:Float=11.5f)=TextView(activity).apply{
            text=t; textSize=size; setTextColor(ThemeManager.muted(activity))
        }
        fun heading(t:String,size:Float=18f)=TextView(activity).apply{
            text=t; textSize=size; typeface=Typeface.DEFAULT_BOLD; setTextColor(ThemeManager.text(activity))
        }
        fun metric(value:String,label:String,tint:Int):LinearLayout = LinearLayout(activity).apply{
            orientation=LinearLayout.VERTICAL; setPadding(dp(11),dp(10),dp(11),dp(9))
            background=rounded(if(ThemeManager.isDark(activity))Color.rgb(20,28,37) else Color.rgb(248,249,249),13)
            addView(TextView(activity).apply{text=value;textSize=20f;typeface=Typeface.DEFAULT_BOLD;setTextColor(tint)},LinearLayout.LayoutParams(-1,dp(26)))
            addView(TextView(activity).apply{text=label;textSize=10.5f;setTextColor(ThemeManager.muted(activity))})
        }
        fun category(title:String,subtitle:String,icon:String,tint:Int,click:()->Unit)=LinearLayout(activity).apply{
            orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL
            setPadding(dp(13),dp(12),dp(10),dp(12))
            background=rounded(if(ThemeManager.isDark(activity))Color.rgb(15,22,31) else ThemeManager.elevated(activity),16)
            addView(TextView(activity).apply{text=icon;textSize=21f;gravity=Gravity.CENTER;setTextColor(tint)},LinearLayout.LayoutParams(dp(38),dp(48)))
            val copy=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(8),0,dp(5),0)}
            copy.addView(TextView(activity).apply{text=title;textSize=15.5f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.text(activity))})
            copy.addView(TextView(activity).apply{text=subtitle;textSize=10.5f;setTextColor(ThemeManager.muted(activity));setPadding(0,dp(3),0,0)})
            addView(copy,LinearLayout.LayoutParams(0,-2,1f))
            addView(TextView(activity).apply{text="›";textSize=25f;gravity=Gravity.CENTER;setTextColor(ThemeManager.muted(activity))},LinearLayout.LayoutParams(dp(28),dp(44)))
            setOnClickListener{click()}
        }
        fun sectionLabel(t:String,sub:String){
            root.addView(TextView(activity).apply{text=t.uppercase();textSize=11f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.accent(activity));setPadding(dp(2),dp(16),dp(2),dp(3))})
            root.addView(muted(sub,11f),LinearLayout.LayoutParams(-1,-2).apply{setMargins(dp(2),0,dp(2),dp(7))})
        }

        // Header / overview hero.
        val header=LinearLayout(activity).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        header.addView(TextView(activity).apply{text="←";textSize=25f;gravity=Gravity.CENTER;setTextColor(ThemeManager.text(activity));background=rounded(ThemeManager.elevated(activity),16);setOnClickListener{activity.finish()}},LinearLayout.LayoutParams(dp(50),dp(50)))
        val headerCopy=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(13),0,0,0)}
        headerCopy.addView(heading("Settings",28f))
        headerCopy.addView(muted("Rovex • Smarter Study. Brighter Tomorrow.",11.5f))
        header.addView(headerCopy,LinearLayout.LayoutParams(0,-2,1f))
        header.addView(RovexHeaderBirdView(activity).apply{
            contentDescription = "Rovex animated header bird"
        },LinearLayout.LayoutParams(dp(74),dp(42)))
        root.addView(header)

        root.addView(card().apply{
            setPadding(dp(16),dp(15),dp(16),dp(15))
            addView(heading("Settings Control Center",19f))
            addView(muted("Organized by what you want to control—not by implementation detail.",11.5f),LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(3),0,dp(10))})
            val row=LinearLayout(activity).apply{orientation=LinearLayout.HORIZONTAL}
            row.addView(metric("${state.learningScore}%","learning",ThemeManager.accent(activity)),LinearLayout.LayoutParams(0,dp(68),1f).apply{setMargins(0,0,dp(5),0)})
            row.addView(metric("${state.confidence}%","confidence",ThemeManager.text(activity)),LinearLayout.LayoutParams(0,dp(68),1f).apply{setMargins(dp(3),0,dp(3),0)})
            row.addView(metric("${state.health}%","engine health",if(state.safeMode)Color.rgb(255,170,80) else Color.rgb(70,210,130)),LinearLayout.LayoutParams(0,dp(68),1f).apply{setMargins(dp(5),0,0,0)})
            addView(row)
        },LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(12),0,dp(7))})

        // Main Settings is intentionally an index, not a duplicate of any detail dashboard.
        // Detailed Adaptive/Performance/Safety dashboards live behind their own routes.
        sectionLabel("Study & Learning","Open study modules for QBank, spaced repetition, flashcards and notes.")
        root.addView(category("Study & Learning","QBank, SRS, flashcards and notes","▰",Color.rgb(70,150,255)){Toast.makeText(activity,"Study-module controls remain in QBank, Flashcards and Notes.",Toast.LENGTH_SHORT).show()})

        sectionLabel("Appearance & UI","Visual identity and readability are intentionally separated from adaptive intelligence.")
        root.addView(category("Themes","Light, Dark, Sepia, AMOLED, Midnight, Cosmos and Pandora","●",Color.rgb(255,180,70)){showThemeDialog()})
        root.addView(category("Typography","Quiz font family and reading density","Aa",Color.rgb(100,180,255)){showFontDialog()},LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(6),0,0)})
        root.addView(category("Colour flow","Flow colors, greeting panel and normal-text mode","≈",Color.rgb(230,100,180)){showColourFlowDialog()},LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(6),0,0)})

        sectionLabel("Intelligence & Automation","Adaptive learning and Ben controls are separated so each screen has one clear purpose.")
        root.addView(category("Adaptive Study","Learning signals, study strategy and contextual SRS","✦",Color.rgb(170,100,255)){openSection("adaptive_study")})
        root.addView(category("Adaptive Engines","Ben edge, cognitive core, neural lab and runtime policy","🧠",Color.rgb(175,110,255)){openSection("adaptive")},LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(6),0,0)})
        root.addView(category("Ben Safety","Kill switch, model guardrails and emergency stop","◆",Color.rgb(90,205,150)){openSection("ben_safety")},LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(6),0,0)})

        sectionLabel("Data & Continuity","Progress protection, import/export and recovery are kept independent from cognition.")
        root.addView(category("Backups & Restore","Protect study progress and restore local backups","☁",Color.rgb(75,160,245)){activity.startActivity(Intent(activity,BackupActivity::class.java))})
        root.addView(category("Question banks & content","Imported QBank/APKG/HTML content remains local and authoritative","▤",Color.rgb(70,205,190)){Toast.makeText(activity,"QBank/content management remains in the main study modules.",Toast.LENGTH_SHORT).show()},LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(6),0,0)})

        sectionLabel("System & Data","Device policy, recovery and protected data are separate from Ben cognition.")
        root.addView(category("Performance & Resources","RAM, thermal, battery, runtime and resilience","◈",Color.rgb(245,175,65)){openSection("performance")})
        root.addView(category("Privacy & Security","Data boundaries, permissions and local-storage policy","✓",Color.rgb(85,210,135)){openSection("privacy")},LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(6),0,0)})

        sectionLabel("Advanced","Only genuinely advanced controls live here.")
        root.addView(category("Test & Diagnostics Center","Full EmbeddingGemma test, Ben IPC telemetry and exportable runtime reports","⌁",Color.rgb(120,145,255)){openSection("diagnostics")})
        root.addView(category("About Rovex","Version, architecture and release information","ⓘ",Color.rgb(120,200,230)){
            val version=runCatching{activity.packageManager.getPackageInfo(activity.packageName,0).versionName}.getOrDefault("unknown")
            AlertDialog.Builder(activity).setTitle("Rovex").setMessage("Version $version\n\nOffline-first study system with deterministic QBank intelligence, adaptive control and optional Ben neural accelerators.\n\nNeural models never replace deterministic verification.").setPositiveButton("OK",null).show()
        },LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(6),0,0)})

        root.addView(card().apply{
            setPadding(dp(15),dp(14),dp(15),dp(14))
            addView(TextView(activity).apply{text="Safety boundary";textSize=15f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.text(activity))})
            addView(muted("Adaptive and Ben systems may tune safe runtime policy only. They cannot modify executable code, source QBank content, or bypass the deterministic verifier.",11f),LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(4),0,0)})
        },LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(16),0,dp(6))})
        root.addView(TextView(activity).apply{text="Settings are applied locally. Detailed engine controls remain under Adaptive Engines.";textSize=10.5f;setTextColor(ThemeManager.muted(activity));setPadding(dp(3),dp(4),dp(3),0)})

        return ScrollView(activity).apply{addView(root);isFillViewport=true}
    }

    private fun openSection(target:String){
        activity.startActivity(Intent(activity,SettingsActivity::class.java).putExtra("section",target))
    }

    private fun showEmbeddingDiagnosticReport(report: String){
        val dialog=Dialog(activity)
        val root=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(16),dp(16),dp(12));background=rounded(ThemeManager.dialogBg(activity),22)}
        root.addView(TextView(activity).apply{text="Ben / EmbeddingGemma Diagnostic";textSize=21f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.text(activity))})
        val scroll=ScrollView(activity)
        scroll.addView(TextView(activity).apply{text=report;textSize=11f;setTextColor(ThemeManager.text(activity));setPadding(0,dp(10),0,dp(10));setTypeface(Typeface.MONOSPACE)})
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        val actions=LinearLayout(activity).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        actions.addView(TextView(activity).apply{text="SHARE";textSize=12f;typeface=Typeface.DEFAULT_BOLD;gravity=Gravity.CENTER;setTextColor(ThemeManager.accent(activity));setPadding(dp(10),dp(10),dp(10),dp(10));setOnClickListener{
            activity.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_SUBJECT,"Rovex Ben EmbeddingGemma Diagnostic");putExtra(Intent.EXTRA_TEXT,report)},"Share diagnostic report"))
        }},LinearLayout.LayoutParams(0,dp(44),1f))
        actions.addView(TextView(activity).apply{text="CLOSE";textSize=12f;typeface=Typeface.DEFAULT_BOLD;gravity=Gravity.CENTER;setTextColor(ThemeManager.accent(activity));setPadding(dp(10),dp(10),dp(10),dp(10));setOnClickListener{dialog.dismiss()}},LinearLayout.LayoutParams(0,dp(44),1f))
        root.addView(actions)
        dialog.setContentView(root);dialog.window?.setBackgroundDrawableResource(android.R.color.transparent);dialog.show()
        dialog.window?.setLayout((activity.resources.displayMetrics.widthPixels*0.94f).toInt(),(activity.resources.displayMetrics.heightPixels*0.82f).toInt())
    }

    private fun showBenAiSafetyDialog(){
        val policy = BenAiRuntimePolicy(activity)
        val dialog=Dialog(activity)
        val root=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(18),dp(20),dp(16));background=rounded(ThemeManager.dialogBg(activity),24)}
        root.addView(TextView(activity).apply{text="Ben AI Safety";textSize=23f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.text(activity))})
        root.addView(TextView(activity).apply{text="Control any future on-device model before it can consume significant memory or native resources.";textSize=12.5f;setTextColor(ThemeManager.muted(activity));setPadding(0,dp(4),0,dp(14))})
        val enabled=android.widget.Switch(activity).apply{text="Allow Ben local model";isChecked=policy.enabled;setTextColor(ThemeManager.text(activity));setOnCheckedChangeListener{_,checked->policy.setEnabled(checked)}}
        root.addView(enabled)
        val conservative=android.widget.Switch(activity).apply{text="Conservative memory guard";isChecked=policy.conservativeMode;setTextColor(ThemeManager.text(activity));setOnCheckedChangeListener{_,checked->policy.setConservativeMode(checked)}}
        root.addView(conservative)
        root.addView(TextView(activity).apply{text="When enabled, future model backends must pass the conservative 1.2 GB model-size gate before loading. Turning off the model switch is a persistent kill switch.";textSize=11.5f;setTextColor(ThemeManager.muted(activity));setPadding(0,dp(8),0,dp(12))})
        root.addView(TextView(activity).apply{text="STOP BEN MODEL";textSize=12f;typeface=Typeface.DEFAULT_BOLD;gravity=Gravity.CENTER;setTextColor(ThemeManager.accent(activity));setPadding(0,dp(10),0,dp(8));setOnClickListener{policy.forceStop();enabled.isChecked=false}})
        root.addView(TextView(activity).apply{text="DONE";textSize=12f;typeface=Typeface.DEFAULT_BOLD;gravity=Gravity.CENTER;setTextColor(ThemeManager.accent(activity));setPadding(0,dp(10),0,dp(4));setOnClickListener{dialog.dismiss()}})
        dialog.setContentView(root);dialog.window?.setBackgroundDrawableResource(android.R.color.transparent);dialog.show()
    }

    private fun showThemeDialog(){
        val keys=arrayOf(ThemeManager.LIGHT,ThemeManager.DARK,ThemeManager.SEPIA,ThemeManager.AMOLED,ThemeManager.MIDNIGHT,ThemeManager.COSMOS,ThemeManager.AVATAR)
        val labels=arrayOf("Light","Dark","Sepia","AMOLED Black","Midnight Blue","Cosmos • Galaxy","Pandora • Avatar")
        val descriptions=arrayOf("Clean daylight reading","Low-glare night reading","Warm textbook paper","True black for OLED","Deep blue, high contrast","AMOLED-black galaxy with planets and nebulae","AMOLED-indigo bioluminescent Pandora-inspired theme")
        val dialog=Dialog(activity)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val root=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(18),dp(20),dp(16));background=rounded(ThemeManager.dialogBg(activity),24)}
        root.addView(TextView(activity).apply{text="Theme";textSize=23f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.text(activity))})
        root.addView(TextView(activity).apply{text="Choose the reading environment that feels right.";textSize=12.5f;setTextColor(ThemeManager.muted(activity));setPadding(0,dp(4),0,dp(12))})
        val current=ThemeManager.get(activity)
        keys.forEachIndexed{index,key->
            val selected=current==key
            val row=LinearLayout(activity).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(12),dp(10),dp(10),dp(10));background=rounded(if(selected)ThemeManager.optionBg(activity) else ThemeManager.elevated(activity),16)}
            val swatch=TextView(activity).apply{text="  ";background=rounded(themeSwatch(key),10);setPadding(dp(5),dp(5),dp(5),dp(5))}
            row.addView(swatch,LinearLayout.LayoutParams(dp(38),dp(38)))
            val copy=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),0,dp(6),0)}
            copy.addView(TextView(activity).apply{text=labels[index];textSize=15.5f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.text(activity))})
            copy.addView(TextView(activity).apply{text=descriptions[index];textSize=11.5f;setTextColor(ThemeManager.muted(activity));setPadding(0,dp(2),0,0)})
            row.addView(copy,LinearLayout.LayoutParams(0,-2,1f))
            row.addView(TextView(activity).apply{text=if(selected)"✓" else "";textSize=20f;typeface=Typeface.DEFAULT_BOLD;gravity=Gravity.CENTER;setTextColor(ThemeManager.accent(activity))},LinearLayout.LayoutParams(dp(28),dp(38)))
            row.setOnClickListener{viewModel.setTheme(key);dialog.dismiss();activity.recreate()}
            root.addView(row,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(4),0,dp(4))})
        }
        root.addView(TextView(activity).apply{text="CANCEL";textSize=12f;typeface=Typeface.DEFAULT_BOLD;gravity=Gravity.CENTER;setTextColor(ThemeManager.accent(activity));setPadding(0,dp(10),0,dp(4));setOnClickListener{dialog.dismiss()}})
        dialog.setContentView(root)
        dialog.setOnShowListener{_ -> dialog.window?.setLayout((activity.resources.displayMetrics.widthPixels*0.90f).toInt(),WindowManager.LayoutParams.WRAP_CONTENT)}
        dialog.show()
        dialog.window?.setLayout((activity.resources.displayMetrics.widthPixels*0.90f).toInt(),WindowManager.LayoutParams.WRAP_CONTENT)
    }

    private fun showFontDialog(){
        val keys=arrayOf("sans-serif","serif","sans-serif-condensed","monospace")
        val labels=arrayOf("Modern Sans","Textbook Serif","Compact Sans","Monospace")
        val samples=arrayOf("A  B  C  •  123","Question • Explanation","Rapid revision text","A = B + C")
        val current=viewModel.quizFont()
        val dialog=Dialog(activity)
        val root=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(18),dp(20),dp(16));background=rounded(ThemeManager.dialogBg(activity),24)}
        root.addView(TextView(activity).apply{text="Fonts";textSize=23f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.text(activity))})
        root.addView(TextView(activity).apply{text="Preview how question text will feel before applying it.";textSize=12.5f;setTextColor(ThemeManager.muted(activity));setPadding(0,dp(4),0,dp(12))})
        keys.forEachIndexed{index,key->
            val selected=current==key
            val row=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(11),dp(12),dp(11));background=rounded(if(selected)ThemeManager.optionBg(activity) else ThemeManager.elevated(activity),16)}
            val top=LinearLayout(activity).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
            top.addView(TextView(activity).apply{text=labels[index];textSize=15.5f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.text(activity));typeface=Typeface.create(key,Typeface.BOLD)},LinearLayout.LayoutParams(0,-2,1f))
            top.addView(TextView(activity).apply{text=if(selected)"✓" else "";textSize=20f;setTextColor(ThemeManager.accent(activity));gravity=Gravity.CENTER},LinearLayout.LayoutParams(dp(28),dp(30)))
            row.addView(top)
            row.addView(TextView(activity).apply{text=samples[index];textSize=15f;typeface=Typeface.create(key,Typeface.NORMAL);setTextColor(ThemeManager.text(activity));setPadding(0,dp(5),0,0)})
            row.setOnClickListener{viewModel.setQuizFont(key);dialog.dismiss();activity.recreate()}
            root.addView(row,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(4),0,dp(4))})
        }
        root.addView(TextView(activity).apply{text="CANCEL";textSize=12f;typeface=Typeface.DEFAULT_BOLD;gravity=Gravity.CENTER;setTextColor(ThemeManager.accent(activity));setPadding(0,dp(10),0,dp(4));setOnClickListener{dialog.dismiss()}})
        dialog.setContentView(root)
        dialog.setOnShowListener{_ -> dialog.window?.setLayout((activity.resources.displayMetrics.widthPixels*0.90f).toInt(),WindowManager.LayoutParams.WRAP_CONTENT)}
        dialog.show()
        dialog.window?.setLayout((activity.resources.displayMetrics.widthPixels*0.90f).toInt(),WindowManager.LayoutParams.WRAP_CONTENT)
    }

    private fun showColourFlowDialog(){
        var c1=viewModel.flowColorOne()
        var c2=viewModel.flowColorTwo()
        var panel=viewModel.flowGreetingColor()
        var flow=viewModel.flowTextEnabled()
        val dialog=Dialog(activity)
        val root=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(18),dp(20),dp(18));background=rounded(ThemeManager.dialogBg(activity),24)}
        root.addView(TextView(activity).apply{text="Colour flow";textSize=23f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.text(activity))})
        root.addView(TextView(activity).apply{text="Choose two colours. They blend and travel across the flow labels.";textSize=12.5f;setTextColor(ThemeManager.muted(activity));setPadding(0,dp(4),0,dp(12))})
        val preview=TextView(activity).apply{text="Performance  •  Flashcards  •  Solved";textSize=16f;typeface=Typeface.DEFAULT_BOLD;gravity=Gravity.CENTER;setPadding(dp(8),dp(10),dp(8),dp(10))}
        root.addView(preview,LinearLayout.LayoutParams(-1,dp(58)).apply{setMargins(0,0,0,dp(10))})
        fun refresh(){
            preview.background=GradientDrawable().apply{cornerRadius=dp(12).toFloat();setColor(if(ThemeManager.get(activity)==ThemeManager.AMOLED)Color.BLACK else ThemeManager.elevated(activity));setStroke(dp(1),RovexColorFlowTextView.mix(c1,c2,.5f))}
            preview.paint.shader=android.graphics.LinearGradient(0f,0f,preview.width.coerceAtLeast(dp(220)).toFloat(),0f,intArrayOf(c1,RovexColorFlowTextView.mix(c1,c2,.5f),c2),null,android.graphics.Shader.TileMode.CLAMP)
            preview.invalidate()
        }
        fun addSpectrum(label:String,initial:Int,onChange:(Int)->Unit){
            root.addView(TextView(activity).apply{text=label;textSize=12f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.muted(activity));setPadding(dp(2),dp(3),0,dp(1))})
            val bar=RovexColorSpectrumView(activity).apply{color=initial;this.onColorChanged={onChange(it);refresh()}}
            root.addView(bar,LinearLayout.LayoutParams(-1,dp(88)).apply{setMargins(0,0,0,dp(4))})
        }
        addSpectrum("FLOW COLOUR 1",c1){c1=it;refresh()}
        addSpectrum("FLOW COLOUR 2",c2){c2=it;refresh()}
        val normal=CheckBox(activity).apply{text="Keep colour-flow labels as normal text";textSize=13f;isChecked=!flow;setTextColor(ThemeManager.text(activity));setOnCheckedChangeListener{_,checked->flow=!checked}}
        root.addView(normal)
        root.addView(TextView(activity).apply{text="Greeting panel colour";textSize=13f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.text(activity));setPadding(dp(2),dp(12),0,dp(2))})
        val panelBar=RovexColorSpectrumView(activity).apply{color=if(panel==0)Color.rgb(35,101,126) else panel;onColorChanged={panel=it}}
        root.addView(panelBar,LinearLayout.LayoutParams(-1,dp(88)).apply{setMargins(0,0,0,dp(4))})
        root.addView(TextView(activity).apply{text="The panel behind Good morning / afternoon / evening uses this colour.";textSize=11f;setTextColor(ThemeManager.muted(activity));setPadding(dp(2),0,0,dp(8))})
        val buttons=LinearLayout(activity).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        buttons.addView(TextView(activity).apply{text="RESET";textSize=12f;typeface=Typeface.DEFAULT_BOLD;gravity=Gravity.CENTER;setTextColor(ThemeManager.accent(activity));setOnClickListener{viewModel.resetColourFlow();dialog.dismiss();activity.recreate()}},LinearLayout.LayoutParams(0,dp(46),1f))
        buttons.addView(TextView(activity).apply{text="SAVE";textSize=12f;typeface=Typeface.DEFAULT_BOLD;gravity=Gravity.CENTER;setTextColor(ThemeManager.accent(activity));setOnClickListener{viewModel.saveColourFlow(c1,c2,flow,panel);dialog.dismiss();activity.recreate()}},LinearLayout.LayoutParams(0,dp(46),1f))
        root.addView(buttons)
        refresh()
        dialog.setContentView(root);dialog.setOnShowListener{dialog.window?.setLayout((activity.resources.displayMetrics.widthPixels*.92f).toInt(),WindowManager.LayoutParams.WRAP_CONTENT)};dialog.show();dialog.window?.setBackgroundDrawableResource(android.R.color.transparent);dialog.window?.setLayout((activity.resources.displayMetrics.widthPixels*.92f).toInt(),WindowManager.LayoutParams.WRAP_CONTENT)
    }

    private fun themeSwatch(key:String):Int=when(key){
        ThemeManager.LIGHT->Color.rgb(247,249,246)
        ThemeManager.DARK->Color.rgb(48,61,75)
        ThemeManager.SEPIA->Color.rgb(246,239,218)
        ThemeManager.AMOLED->Color.rgb(0,0,0)
        ThemeManager.COSMOS->Color.rgb(11,8,30)
        ThemeManager.AVATAR->Color.rgb(6,45,61)
        else->Color.rgb(24,54,92)
    }

    private fun addAppearance(root:LinearLayout){
        section(root,"APPEARANCE")
        val themes=linkedMapOf(ThemeManager.LIGHT to "Light",ThemeManager.DARK to "Dark",ThemeManager.SEPIA to "Sepia",ThemeManager.AMOLED to "AMOLED Black",ThemeManager.MIDNIGHT to "Midnight Blue",ThemeManager.COSMOS to "Cosmos • Galaxy",ThemeManager.AVATAR to "Pandora • Avatar")
        themes.forEach{(key,label)-> root.addView(button(if(ThemeManager.get(activity)==key)"✓  $label" else label){viewModel.setTheme(key);activity.recreate()},lp())}
    }

    private fun addFonts(root:LinearLayout){
        section(root,"QUIZ TYPOGRAPHY")
        val fonts=linkedMapOf("sans-serif" to "Modern Sans","serif" to "Serif / textbook","monospace" to "Monospace / coding","sans-serif-condensed" to "Condensed Sans","sans-serif-light" to "Light Sans")
        val current=viewModel.quizFont()
        fonts.forEach{(key,label)->root.addView(button(if(current==key)"✓  $label" else label){viewModel.setQuizFont(key);activity.recreate()},lp())}
    }

    private fun visualCard(root:LinearLayout, titleText:String, subtitle:String, body:LinearLayout.()->Unit){
        val dark=ThemeManager.isDark(activity)
        val card=LinearLayout(activity).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(dp(14),dp(13),dp(14),dp(13))
            background=GradientDrawable().apply{setColor(ThemeManager.elevated(activity));cornerRadius=dp(16).toFloat();setStroke(dp(1),if(dark)Color.rgb(48,61,75)else Color.rgb(220,226,230))}
        }
        card.addView(TextView(activity).apply{text=titleText;textSize=16f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.text(activity))})
        card.addView(TextView(activity).apply{text=subtitle;textSize=10.5f;setTextColor(ThemeManager.muted(activity));setPadding(0,dp(2),0,dp(8))})
        body(card)
        root.addView(card,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,0,0,dp(9))})
    }
    private fun tableRow(parent:LinearLayout, key:String, value:String, strong:Boolean=false){
        val row=LinearLayout(activity).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(8),dp(7),dp(8),dp(7))}
        row.addView(TextView(activity).apply{text=key;textSize=10f;setTextColor(ThemeManager.muted(activity))},LinearLayout.LayoutParams(0,-2,.43f))
        row.addView(TextView(activity).apply{text=value;textSize=10.5f;typeface=if(strong)Typeface.DEFAULT_BOLD else Typeface.DEFAULT;setTextColor(if(strong)ThemeManager.accent(activity)else ThemeManager.text(activity));gravity=Gravity.RIGHT},LinearLayout.LayoutParams(0,-2,.57f))
        parent.addView(row)
        parent.addView(View(activity).apply{setBackgroundColor(if(ThemeManager.isDark(activity))Color.rgb(48,60,73)else Color.rgb(225,229,232))},LinearLayout.LayoutParams(-1,dp(1)))
    }
    private fun miniBar(parent:LinearLayout,labelText:String,value:Int){
        parent.addView(TextView(activity).apply{text="$labelText  ${value.coerceIn(0,100)}%";textSize=10.5f;setTextColor(ThemeManager.text(activity));setPadding(dp(8),dp(5),dp(8),dp(2))})
        parent.addView(ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal).apply{
            max=100;progress=value.coerceIn(0,100);progressTintList=android.content.res.ColorStateList.valueOf(ThemeManager.accent(activity));backgroundTintList=android.content.res.ColorStateList.valueOf(if(ThemeManager.isDark(activity))Color.rgb(42,52,64)else Color.rgb(231,235,238))
        },LinearLayout.LayoutParams(-1,dp(7)).apply{setMargins(dp(8),0,dp(8),dp(4))})
    }
    private fun sectionBanner(root:LinearLayout, titleText:String, subtitle:String){
        root.addView(TextView(activity).apply{text=titleText.uppercase();textSize=11f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.accent(activity));setPadding(dp(2),dp(7),dp(2),dp(3))})
        root.addView(TextView(activity).apply{text=subtitle;textSize=10.5f;setTextColor(ThemeManager.muted(activity));setPadding(dp(2),0,dp(2),dp(9))})
    }

    private fun addAdaptiveEngine(root:LinearLayout){
        val state=AppManagers.adaptive.state();val telemetry=BenNeuralTelemetry.snapshot()
        sectionBanner(root,"Engine overview","One screen for Ben cognition, neural acceleration, runtime policy and adaptive orchestration.")
        visualCard(root,"Engine health","Current state from the authoritative AdaptiveEngineManager"){
            miniBar(this,"System health",state.health)
            miniBar(this,"Learning",state.learningScore)
            miniBar(this,"Confidence",state.confidence)
            tableRow(this,"Autonomy",if(AppManagers.adaptive.isAutonomyEnabled())"Enabled" else "Paused",true)
            tableRow(this,"Safety",if(state.safeMode)"Protective mode" else "Normal")
        }
        addAttentionParameters(root,listOf(
            "System health" to (state.health to "Open Performance Lab to inspect thermal, RAM and resilience signals."),
            "Learning" to (state.learningScore to "Open Adaptive Study to inspect observations, exploration and strategy."),
            "Confidence" to (state.confidence to "Low confidence increases conservative exploration and verification."),
            "Resilience" to (PerformanceManager.healthScore() to "Review Performance Lab if runtime health is falling.")
        ))
        visualCard(root,"Engine topology","Deterministic core → retrieval → specialists → verifier → optional neural accelerators"){
            val flow=LinearLayout(activity).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
            listOf("Core","RAG","Specialists","Verifier","Neural").forEachIndexed{index,name->
                val box=TextView(activity).apply{text=name;textSize=9.5f;gravity=Gravity.CENTER;setTextColor(ThemeManager.text(activity));background=rounded(if(index==4)ThemeManager.explanationBg(activity)else ThemeManager.panel(activity),10);setPadding(dp(4),dp(10),dp(4),dp(10))}
                flow.addView(box,LinearLayout.LayoutParams(0,dp(42),1f).apply{if(index>0)setMargins(dp(2),0,0,0)})
            }
            addView(flow)
            tableRow(this,"Model",telemetry.activeModel.ifBlank{"Deterministic Ben"})
            tableRow(this,"Last stage",telemetry.stage.name.replace('_',' '))
            tableRow(this,"Evidence",telemetry.lastEvidenceCount.toString())
            tableRow(this,"Last latency","${telemetry.lastElapsedMs} ms")
        }
        visualCard(root,"Ben edge efficiency","Measured/controlled optimizations—not speculative runtime switches"){
            val eff=BenInferenceEfficiencyPolicy.snapshot()
            tableRow(this,"Prompt window","${eff.promptWindowChars} chars")
            tableRow(this,"KV cache","LiteRT-LM owned")
            tableRow(this,"Speculative/MTP",if(eff.speculativeDecoding)"Enabled" else "Capability-gated")
            tableRow(this,"Zero-copy tensor IPC",if(eff.zeroCopyTensorIpc)"Enabled" else "Off")
            tableRow(this,"IPC","Single-flight + latest-request-wins")
            tableRow(this,"Thermal policy",AppManagers.resilienceHealth.snapshot().thermalStatus.toString())
        }
        visualCard(root,"Ben brain","Cognitive switches, specialist routing, knowledge and verification"){
            val bc=BenCognitiveControl(activity)
            listOf("Cognitive Core" to bc.cognitiveCoreEnabled,"Medical Knowledge Graph" to bc.knowledgeGraphEnabled,"Planner / Reasoner" to bc.plannerEnabled,"Local RAG / QBank Retrieval" to bc.retrievalEnabled,"Specialist Routing" to bc.specialistRoutingEnabled,"Learner Memory" to bc.learnerMemoryEnabled,"Answer Verifier" to bc.verifierEnabled).forEach{(name,enabled)->tableRow(this,name,if(enabled)"ON" else "OFF",enabled)}
            val fr=AppManagers.benBrain.frankenstein().stats();tableRow(this,"Routed requests","${fr.observations}");tableRow(this,"Verification rate","${fr.verificationRate}%",true);tableRow(this,"Specialist routes",BenSpecialistRegistry.entries().size.toString())
        }
        visualCard(root,"Gemini cloud collaborator","Google-account gated web research; local Ben remains the authoritative study layer"){
            val account=GoogleAccountManager(activity)
            val email=account.currentEmail()
            tableRow(this,"Google account",email ?: "Not signed in",email != null)
            tableRow(this,"Web grounding","Google Search via Gemini",true)
            addView(button(if(email==null) "Sign in with Google" else "Sign out Google account"){
                lifecycleScope.launch{
                    if(email==null){
                        val result=account.signIn()
                        Toast.makeText(activity, if(result.isSuccess) "Signed in: ${result.getOrNull()}" else (result.exceptionOrNull()?.message ?: "Google sign-in failed"), Toast.LENGTH_LONG).show()
                    } else {
                        account.signOut()
                        Toast.makeText(activity,"Google account signed out",Toast.LENGTH_SHORT).show()
                    }
                    activity.recreate()
                }
            },LinearLayout.LayoutParams(-1,dp(42)).apply{setMargins(dp(8),dp(6),dp(8),dp(2))})
            addView(button("Open Ben + Gemini Research"){
                activity.startActivity(Intent(activity,RenActivity::class.java))
            },LinearLayout.LayoutParams(-1,dp(42)).apply{setMargins(dp(8),dp(3),dp(8),dp(2))})
        }
        visualCard(root,"Neural lab","EmbeddingGemma retrieval + Gemma 3 270M generation; both remain governor-controlled"){
            val policy=BenAiRuntimePolicy(activity);val em=neuralModelManager.installed(BenNeuralModelRegistry.embeddingGemma300m);val tok=neuralModelManager.installedEmbeddingGemmaTokenizer();val gm=neuralModelManager.installed(BenNeuralModelRegistry.gemma3_270m)
            tableRow(this,"Policy",if(policy.circuitOpen)"Circuit open" else if(policy.enabled)"Armed / governor" else "Off",true)
            tableRow(this,"EmbeddingGemma",if(em!=null)"Installed • ${em.bytes/(1024*1024)} MB" else "Missing")
            tableRow(this,"Tokenizer",if(tok!=null)"Installed" else "Missing")
            tableRow(this,"Gemma 3 270M",if(gm!=null)"Installed" else "Missing")
        }
        visualCard(root,"Runtime policy","Adaptive cache, prefetch and safe execution policy"){
            tableRow(this,"Prefetch depth",state.preferredPrefetch.toString());tableRow(this,"Cache TTL","${PerformanceManager.adaptiveCacheTtlMs()/1000}s");tableRow(this,"Last action",state.lastAction);tableRow(this,"Outcome",state.lastOutcome);tableRow(this,"Rollback count",state.rollbackCount.toString())
        }
        visualCard(root,"Orchestration activity","Recent decisions and multi-engine control plane"){
            tableRow(this,"Observations",state.observations.toString());tableRow(this,"Answers","${state.answers} • correct ${state.correct} • wrong ${state.wrong}");tableRow(this,"Recent action count",state.recentActions.size.toString());tableRow(this,"Safety boundary","Adaptive policy only",true)
            addView(button("Open Ben Safety"){openSection("ben_safety")},LinearLayout.LayoutParams(-1,dp(42)).apply{setMargins(dp(8),dp(6),dp(8),dp(2))})
        }
        root.addView(button("Reset Adaptive Learning History"){viewModel.resetAdaptiveLearning();Toast.makeText(activity,"Adaptive history reset",Toast.LENGTH_SHORT).show();activity.recreate()},LinearLayout.LayoutParams(-1,dp(46)).apply{setMargins(0,dp(3),0,dp(18))})
    }

    private fun addAdaptiveStudy(root:LinearLayout){
        val state=AppManagers.adaptive.state()
        sectionBanner(root,"Learning dashboard","Study behaviour is separated from engine implementation and device policy.")
        visualCard(root,"Learning snapshot","Three signals that summarize the current adaptive learner model"){
            miniBar(this,"Learning",state.learningScore);miniBar(this,"Confidence",state.confidence);miniBar(this,"Uncertainty",state.uncertainty)
            tableRow(this,"Exploration","${state.exploration}%");tableRow(this,"User model",state.userModel);tableRow(this,"System model",state.systemModel)
        }
        visualCard(root,"Learning signals","Confidence, uncertainty and exploration presented as a decision table"){
            tableRow(this,"Confidence","${state.confidence}%",true);tableRow(this,"Uncertainty","${state.uncertainty}%");tableRow(this,"Exploration","${state.exploration}%");tableRow(this,"Answers","${state.answers}");tableRow(this,"Correct","${state.correct}");tableRow(this,"Wrong","${state.wrong}")
        }
        visualCard(root,"Study strategy","Current recommendation and the next policy action"){
            tableRow(this,"Recommendation",AppManagers.adaptive.studyStrategy(),true);val proposal=AppManagers.adaptive.currentProposal();tableRow(this,"Next action",proposal.action.key);tableRow(this,"Reason",proposal.reason);tableRow(this,"Explainability",AdaptiveExplainability.explain(state))
        }
        visualCard(root,"Contextual SRS shadow","Experimental recommendation only; authoritative scheduler is unchanged"){
            tableRow(this,"Mode","Advisory / shadow",true);tableRow(this,"Minimum observations","30");tableRow(this,"Scheduler mutation","None");tableRow(this,"Current policy","Contextual recommendation only")
        }
        visualCard(root,"Learning history","Recent observations and adaptive changes"){
            tableRow(this,"Observations",state.observations.toString());tableRow(this,"Autonomous changes",state.autonomousChanges.toString());tableRow(this,"Last action",state.lastAction);tableRow(this,"Last outcome",state.lastOutcome);tableRow(this,"Rollbacks",state.rollbackCount.toString())
            addView(button("Reset Adaptive Learning History"){viewModel.resetAdaptiveLearning();Toast.makeText(activity,"Adaptive history reset",Toast.LENGTH_SHORT).show();activity.recreate()},LinearLayout.LayoutParams(-1,dp(42)).apply{setMargins(dp(8),dp(6),dp(8),dp(2))})
        }
    }

    private fun addCircularMetricRow(parent: LinearLayout, metrics: List<Triple<String, Int, String>>) {
        val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        metrics.forEachIndexed { index, metric ->
            val view = CircularMetricView(activity).apply {
                setMetric(metric.second, metric.first, metric.third)
                contentDescription = "${metric.first}: ${metric.second} percent. ${metric.third}"
                setOnClickListener { showPerformanceMetric(metric.first, metric.second, metric.third) }
            }
            row.addView(view, LinearLayout.LayoutParams(0, dp(132), 1f).apply {
                if (index > 0) setMargins(dp(3), 0, 0, 0)
            })
        }
        parent.addView(row, LinearLayout.LayoutParams(-1, dp(140)).apply { setMargins(0, dp(4), 0, dp(5)) })
    }

    private fun showPerformanceMetric(title: String, value: Int, detail: String) {
        val state = when {
            value >= 90 -> "Healthy"
            value >= 75 -> "Watch"
            else -> "Needs attention"
        }
        AlertDialog.Builder(activity)
            .setTitle("$title • $value%")
            .setMessage("$state\n\n$detail\n\nTap outside or press Back to return to the Performance Lab.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun addAttentionParameters(root: LinearLayout, parameters: List<Pair<String, Pair<Int, String>>>) {
        val weak = parameters.filter { it.second.first < 90 }.sortedBy { it.second.first }
        if (weak.isEmpty()) {
            visualCard(root, "Parameter health", "All tracked parameters are currently at or above 90%") {
                addView(TextView(activity).apply { text="✓  ALL PARAMETERS STABLE"; textSize=12f; typeface=Typeface.DEFAULT_BOLD; gravity=Gravity.CENTER; setTextColor(ThemeManager.text(activity)); setPadding(dp(8),dp(12),dp(8),dp(12)) })
            }
            return
        }
        visualCard(root, "Parameters < 90%", "Ben turns weak signals into a prioritized action queue. Lower scores rise to the top.") {
            weak.forEach { (name, data) ->
                val row=LinearLayout(activity).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(3),dp(4),dp(3),dp(4));isClickable=true;setOnClickListener{showParameterAction(name,data.first,data.second)}}
                val ring=CircularMetricView(activity).apply{setMetric(data.first,name.take(9),data.second);contentDescription="$name ${data.first} percent. Tap for recommended action"}
                row.addView(ring,LinearLayout.LayoutParams(dp(82),dp(82)))
                val copy=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(8),0,dp(5),0)}
                copy.addView(TextView(activity).apply{text=if(data.first<60)"URGENT • $name" else if(data.first<75)"FOCUS • $name" else "WATCH • $name";textSize=12.5f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.text(activity))})
                copy.addView(TextView(activity).apply{text=data.second;textSize=10.8f;setTextColor(ThemeManager.muted(activity));setPadding(0,dp(3),0,0);setLineSpacing(0f,1.08f)})
                copy.addView(TextView(activity).apply{text="TAP FOR ACTION  ›";textSize=9.5f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.accent(activity));setPadding(0,dp(5),0,0)})
                row.addView(copy,LinearLayout.LayoutParams(0,-2,1f));addView(row)
            }
        }
    }

    private fun showParameterAction(name:String,value:Int,detail:String){
        val action=when{name.contains("RAM",true)->"Reduce neural/prefetch pressure and keep foreground study protected.";name.contains("Confidence",true)->"Ask Ben for a targeted weak-concept explanation and complete a short focused question set.";name.contains("Learning",true)->"Use the current adaptive recommendation and reinforce the weakest concepts before broad exploration.";name.contains("Resilience",true)->"Keep protective recovery enabled and avoid stressing neural inference while the score is low.";else->"Open the relevant study tool and use a focused intervention before returning to normal load."}
        AlertDialog.Builder(activity).setTitle("$name • $value%")
            .setMessage("WHY IT IS BELOW 90%\n$detail\n\nBEN ACTION\n$action\n\nThe intervention is advisory unless an existing governor explicitly permits an automatic safety change.")
            .setPositiveButton("OPEN BEN"){_,_->activity.startActivity(Intent(activity,RenActivity::class.java).putExtra("prompt","Improve $name: $detail"))}
            .setNegativeButton("CLOSE",null).show()
    }

    private fun addPerformance(root:LinearLayout){
        val snap=BenAiResourceGovernor(activity).snapshot(true);val bs=AppManagers.battery.snapshot();val bp=AppManagers.battery.policy(RovexBatteryManager.WorkClass.INTERACTIVE);val rh=AppManagers.resilienceHealth.snapshot();val telemetry=BenNeuralTelemetry.snapshot()
        val ramScore=(snap.availableMemoryMb.toFloat()/2048f*100f).toInt().coerceIn(0,100)
        sectionBanner(root,"Performance Lab","Live device health, resilience and Ben runtime signals — tap a circle for an easy explanation.")
        visualCard(root,"Live health","Progressive rings + a live signal trace. Tap a ring for the simple explanation."){
            addCircularMetricRow(this,listOf(
                Triple("RAM",ramScore,"${snap.availableMemoryMb} MB available headroom"),
                Triple("Resilience",rh.overall,"${rh.crashCount} process interruptions"),
                Triple("Health",AppManagers.adaptive.state().health,"Adaptive/system health score")
            ))
            addView(PerformancePulseView(activity), LinearLayout.LayoutParams(-1, dp(108)).apply { setMargins(dp(4), dp(2), dp(4), dp(8)) })
            addView(TextView(activity).apply { text="THERMAL ${snap.thermalStatus}  •  ${if(snap.powerSave) "POWER SAVE" else "NORMAL POWER"}  •  ${if(snap.foreground) "FOREGROUND" else "BACKGROUND"}"; textSize=10.5f; typeface=Typeface.DEFAULT_BOLD; gravity=Gravity.CENTER; setTextColor(ThemeManager.muted(activity)); setPadding(dp(6),dp(4),dp(6),dp(4)) })
        }
        addAttentionParameters(root,listOf(
            "System health" to (AppManagers.adaptive.state().health to "Protective mode or runtime pressure may be affecting the study path."),
            "Learning" to (AppManagers.adaptive.state().learningScore to "Adaptive learner needs more observations or stronger outcomes."),
            "Confidence" to (AppManagers.adaptive.state().confidence to "Ben is intentionally more conservative when confidence is low."),
            "Resilience" to (rh.overall to "Review process interruptions and recovery telemetry."),
            "RAM headroom" to (ramScore to "Low headroom can block neural work to protect foreground study.")
        ))
        visualCard(root,"Battery policy","Foreground study is protected; bulk work is governor-controlled"){
            tableRow(this,"Battery","${bs.level.name} • ${AppManagers.battery.statusLine()}",true);tableRow(this,"Prefetch depth",bp.prefetchDepth.toString());tableRow(this,"Image concurrency",bp.imageConcurrency.toString());tableRow(this,"Import batch",bp.importBatchSize.toString())
        }
        visualCard(root,"Runtime telemetry","One glance first; details stay available on demand."){
            val quick=LinearLayout(activity).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER}
            listOf("${telemetry.lastElapsedMs} ms" to "LAST","${telemetry.lastSemanticMs} ms" to "SEMANTIC","${telemetry.totalRequests}" to "REQUESTS").forEachIndexed{i,pair->
                quick.addView(TextView(activity).apply{text="${pair.first}\n${pair.second}";textSize=10.5f;typeface=Typeface.DEFAULT_BOLD;gravity=Gravity.CENTER;setTextColor(ThemeManager.text(activity));background=rounded(ThemeManager.elevated(activity),16);setPadding(dp(7),dp(8),dp(7),dp(8))},LinearLayout.LayoutParams(0,dp(54),1f).apply{if(i>0)setMargins(dp(5),0,0,0)})
            }
            addView(quick)
            addView(button("DETAILS / FALLBACKS"){showPerformanceMetric("Runtime telemetry",telemetry.lastElapsedMs.coerceIn(0L,100L).toInt(),"Stage: ${telemetry.stage.name.replace('_',' ')}\nModel: ${telemetry.activeModel.ifBlank{"None"}}\nSemantic: ${telemetry.lastSemanticMs} ms\nGeneration: ${telemetry.lastGenerationMs} ms\nRequests: ${telemetry.totalRequests} • neural ${telemetry.neuralRequests} • fallback ${telemetry.fallbackRequests}")},LinearLayout.LayoutParams(-1,dp(40)).apply{setMargins(dp(8),dp(7),dp(8),0)})
        }
        visualCard(root,"Resilience & recovery","Crash protection and protected study-session state"){
            tableRow(this,"Health","${rh.overall}%",rh.overall>=80);tableRow(this,"Mode",if(rh.safeMode)"Protective safe mode" else "Normal");tableRow(this,"Process interruptions",rh.crashCount.toString());tableRow(this,"Suspected UI stalls",ResilienceManager.suspectedStalls(activity).toString());rh.recovery?.let{tableRow(this,"Protected question","${it.position+1}")}
        }
        visualCard(root,"Companion engines","Independent study systems remain separate from Ben"){
            val fs=AppManagers.flashcardIntelligence.stats();val ks=AppManagers.knowledge.stats();tableRow(this,"Flashcard engine",if(fs.available)"Ready" else "Unavailable",fs.available);tableRow(this,"Knowledge vault","${ks.notes} notes • ${ks.tables} tables • ${ks.images} images");addView(button("Open Flashcards"){activity.startActivity(Intent(activity,FlashcardActivity::class.java))},LinearLayout.LayoutParams(-1,dp(42)).apply{setMargins(dp(8),dp(6),dp(8),dp(3))});addView(button("Open Knowledge Vault"){activity.startActivity(Intent(activity,KnowledgeVaultActivity::class.java))},LinearLayout.LayoutParams(-1,dp(42)).apply{setMargins(dp(8),dp(3),dp(8),dp(2))})
        }
    }

    private fun addBenSafetyPage(root:LinearLayout){
        val policy=BenAiRuntimePolicy(activity);sectionBanner(root,"Ben safety","Independent from Adaptive Engine cognition. These controls govern whether neural resources may run.")
        visualCard(root,"Safety status","Current model permission and circuit breaker"){
            tableRow(this,"Neural model access",if(policy.enabled)"Allowed" else "STOPPED",policy.enabled);tableRow(this,"Conservative guard",if(policy.conservativeMode)"ON" else "OFF");tableRow(this,"Circuit breaker",if(policy.circuitOpen)"OPEN" else "Closed",!policy.circuitOpen);tableRow(this,"Consecutive failures","${policy.consecutiveFailures}/${BenAiRuntimePolicy.MAX_CONSECUTIVE_FAILURES}")
            miniBar(this,"Safety margin",if(policy.circuitOpen)0 else 100)
        }
        visualCard(root,"Allowed / forbidden operations","Ben can tune safe runtime policy but cannot rewrite study truth"){
            tableRow(this,"May modify","Neural runtime policy");tableRow(this,"May modify","Cache/prefetch policy");tableRow(this,"May NOT modify","Question content / answers",true);tableRow(this,"May NOT modify","Progress records / backups",true);tableRow(this,"May NOT modify","Executable code / schema",true);tableRow(this,"Verifier authority","Deterministic verifier",true)
        }
        visualCard(root,"Emergency controls","Use only when you want neural execution stopped immediately"){
            val sw=Switch(activity).apply{text="Allow Ben local model";isChecked=policy.enabled;setTextColor(ThemeManager.text(activity));setOnCheckedChangeListener{_,v->policy.setEnabled(v)}};addView(sw)
            val conservative=Switch(activity).apply{text="Conservative memory guard";isChecked=policy.conservativeMode;setTextColor(ThemeManager.text(activity));setOnCheckedChangeListener{_,v->policy.setConservativeMode(v)}};addView(conservative)
            addView(button("STOP BEN MODEL / RESET CIRCUIT"){policy.forceStop();policy.resetCircuit();sw.isChecked=false;Toast.makeText(activity,"Ben neural execution stopped",Toast.LENGTH_LONG).show();activity.recreate()},LinearLayout.LayoutParams(-1,dp(44)).apply{setMargins(dp(8),dp(8),dp(8),dp(2))})
        }
    }

    private fun addPrivacyPage(root:LinearLayout){
        sectionBanner(root,"Privacy & security","This screen covers data boundaries—not Ben's neural safety controls.")
        visualCard(root,"Data boundary","What stays under Rovex's local study authority"){
            tableRow(this,"Study data","Local application storage",true);tableRow(this,"QBank content","Imported/local content");tableRow(this,"Backups","User-controlled local backup/restore");tableRow(this,"Ben memory","Local learner/experience memory");tableRow(this,"Online research","User initiated • Google account gated")
        }
        visualCard(root,"Capability boundaries","Network and imported-content capabilities are explicitly separated"){
            tableRow(this,"Online research","Gemini Google Search • explicit user action");tableRow(this,"Imported HTML network","Not granted by default");tableRow(this,"External model access","User-selected local model files");tableRow(this,"Executable modification","Forbidden",true);tableRow(this,"Question truth mutation","Forbidden",true)
        }
        visualCard(root,"Safety architecture","Privacy is not the same control plane as Ben AI Safety"){
            tableRow(this,"Privacy owner","Rovex data/storage policy",true);tableRow(this,"AI safety owner","Ben AI Runtime Policy");tableRow(this,"Study truth owner","Deterministic QBank/verifier");tableRow(this,"Emergency neural stop","Available in Ben Safety")
            addView(button("Open Ben Safety"){openSection("ben_safety")},LinearLayout.LayoutParams(-1,dp(42)).apply{setMargins(dp(8),dp(6),dp(8),dp(2))})
        }
    }

    private fun addDiagnosticsPage(root:LinearLayout){
        val t=BenNeuralTelemetry.snapshot();sectionBanner(root,"Test & Diagnostics Center","One permanent window for reproducible model tests, runtime telemetry and exportable reports.")
        visualCard(root,"Neural runtime","Current diagnostic snapshot"){
            tableRow(this,"Stage",t.stage.name.replace('_',' '));tableRow(this,"Stage detail",t.stageDetail);tableRow(this,"Active model",t.activeModel.ifBlank{"None"});tableRow(this,"Model kind",t.modelKind);tableRow(this,"Last latency","${t.lastElapsedMs} ms");tableRow(this,"Evidence count",t.lastEvidenceCount.toString());tableRow(this,"Verified",if(t.lastVerified)"YES" else "—",t.lastVerified);tableRow(this,"Citation",if(t.lastCitationValid)"VALID" else "—",t.lastCitationValid)
        }
        visualCard(root,"Failure/fallback telemetry","Neural failures never stop deterministic study"){
            tableRow(this,"Total requests",t.totalRequests.toString());tableRow(this,"Neural requests",t.neuralRequests.toString());tableRow(this,"Fallback requests",t.fallbackRequests.toString());tableRow(this,"Blocked requests",t.blockedRequests.toString());tableRow(this,"Last error",t.lastError ?: "None")
        }
        root.addView(button("Open Rovex Test & Diagnostics Center"){activity.startActivity(Intent(activity,RovexDiagnosticsDataCenter::class.java))},LinearLayout.LayoutParams(-1,dp(44)).apply{setMargins(0,dp(2),0,dp(8))})
    }

    // Legacy monolithic Adaptive renderer intentionally removed: each domain now owns a separate
    // dashboard so Adaptive Study, Adaptive Engines, Performance, Ben Safety and Privacy cannot
    // accidentally collapse into one window again.

    private fun sectionView(title:String, subtitle:String):View = LinearLayout(activity).apply{
        orientation=LinearLayout.VERTICAL; setPadding(dp(2),dp(10),dp(2),dp(5))
        addView(TextView(activity).apply{text=title;textSize=11.5f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.accent(activity))})
        addView(TextView(activity).apply{text=subtitle;textSize=11.5f;setTextColor(ThemeManager.muted(activity));setPadding(0,dp(3),0,dp(4))})
    }
    private fun labelBlock(t:String)=TextView(activity).apply{text=t;textSize=11.5f;setTextColor(ThemeManager.muted(activity));setPadding(dp(12),dp(4),dp(12),dp(6))}
    private fun engineCard(title:String, subtitle:String, action:String, click:()->Unit)=LinearLayout(activity).apply{
        orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(14),dp(12),dp(10),dp(12))
        background=GradientDrawable().apply{setColor(ThemeManager.elevated(activity));cornerRadius=dp(16).toFloat();setStroke(dp(1),if(ThemeManager.isDark(activity))Color.rgb(48,61,75)else Color.rgb(220,226,230))}
        val copy=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL}
        copy.addView(TextView(activity).apply{text=title;textSize=16f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.text(activity))})
        copy.addView(TextView(activity).apply{text=subtitle;textSize=11.5f;setTextColor(ThemeManager.muted(activity));setPadding(0,dp(3),0,0)})
        addView(copy,LinearLayout.LayoutParams(0,-2,1f))
        addView(TextView(activity).apply{text="OPEN";textSize=10.5f;typeface=Typeface.DEFAULT_BOLD;gravity=Gravity.CENTER;setTextColor(ThemeManager.accent(activity));background=rounded(ThemeManager.explanationBg(activity),11);setPadding(dp(10),0,dp(10),0);setOnClickListener{click()}},LinearLayout.LayoutParams(dp(78),dp(38)))
    }
    private fun section(root:LinearLayout,t:String){root.addView(TextView(activity).apply{text=t;textSize=11.5f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.accent(activity));setPadding(0,dp(16),0,dp(8))})}
    private fun lp()=LinearLayout.LayoutParams(-1,dp(48)).apply{setMargins(0,dp(4),0,dp(4))}
    private fun button(t:String,click:()->Unit)=TextView(activity).apply{text=t;textSize=14f;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(16),0,dp(16),0);setTextColor(ThemeManager.text(activity));background=rounded(ThemeManager.elevated(activity),14);setOnClickListener{click()}}
    private fun rounded(c:Int,r:Int)=GradientDrawable().apply{setColor(c);cornerRadius=dp(r).toFloat()}
}
