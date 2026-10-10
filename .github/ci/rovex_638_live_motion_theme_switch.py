#!/usr/bin/env python3
from pathlib import Path
import sys
P=Path(sys.argv[1]).resolve()
K=P/"app/src/main/java/com/localqbank/library"
def f(n):
 p=K/n
 if not p.is_file(): raise SystemExit("[638-motion] missing "+str(p))
 return p
def rep(p,a,b,label,count=1):
 s=p.read_text()
 if s.count(a)<count: raise SystemExit("[638-motion] missing anchor: "+label)
 p.write_text(s.replace(a,b,count))

# Persisted preference, separate from Android's accessibility animation scale and from touch sound.
(K / "RovexLiveMotionSettings.kt").write_text("""package com.localqbank.library

import android.content.Context

/** User-owned switch for animated wallpaper/background layers; tap feedback remains independent. */
object RovexLiveMotionSettings {
    private const val PREFS = "rovex_live_motion"
    private const val KEY_ENABLED = "enabled"
    fun enabled(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)
    fun setEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }
}
""")

p=f("RovexLivingBackgroundDrawable.kt")
rep(p,"if (running && AnimationPolicy.enabled(app) && profile.motionAlpha > 0f)",
    "if (running && AnimationPolicy.enabled(app) && RovexLiveMotionSettings.enabled(app) && profile.motionAlpha > 0f)",
    "native live wallpaper motion gate")

p=f("RovexHomeRevolution.kt")
rep(p,'private const val MOTION_CARD_PREFIX = "rovex_home_motion_card:"',
    'private const val MOTION_CARD_PREFIX = "rovex_home_motion_card:"\n    private const val MOTION_HEADER_TAG = "rovex_home_motion_header"',
    "header motion tag")
rep(p,"motionPolicyAllowed = AnimationPolicy.enabled(a)",
    "motionPolicyAllowed = AnimationPolicy.enabled(a) && RovexLiveMotionSettings.enabled(a)",
    "user wallpaper toggle",count=2)
rep(p,"        if (!motionPolicyAllowed) return null\n        val view = LottieAnimationView(c).apply {",
    "        val view = LottieAnimationView(c).apply {",
    "retain hidden motion views for toggle")
rep(p,"        if (tag != MOTION_BG_TAG && !tag.startsWith(MOTION_CARD_PREFIX)) return\n        view.alpha = motionAlpha(c, tag == MOTION_BG_TAG)\n        val visibleRect = android.graphics.Rect()",
"""        if (tag != MOTION_BG_TAG && !tag.startsWith(MOTION_CARD_PREFIX) && tag != MOTION_HEADER_TAG) return
        view.alpha = if (tag == MOTION_HEADER_TAG) when (ThemeManager.get(c)) {
            ThemeManager.LIGHT -> 0.54f
            ThemeManager.PASTEL -> 0.48f
            ThemeManager.MINT -> 0.42f
            ThemeManager.SUNSET -> 0.48f
            ThemeManager.LAVENDER -> 0.48f
            ThemeManager.AMOLED -> 0.28f
            ThemeManager.PANDORA -> 0.34f
            ThemeManager.SPACE -> 0.28f
            else -> 0.40f
        } else motionAlpha(c, tag == MOTION_BG_TAG)
        view.visibility = if (motionPolicyAllowed) View.VISIBLE else View.GONE
        val visibleRect = android.graphics.Rect()""",
    "theme-aware visibility for motion layers")

old="""        if (ThemeManager.get(a) == ThemeManager.LIGHT) {
            val motion= LottieAnimationView(a).apply {
                setAnimation(R.raw.rovex_header_flow)
                repeatCount=LottieDrawable.INFINITE
                repeatMode=LottieDrawable.RESTART
                speed=0.55f
                alpha=0.58f
                renderMode=RenderMode.HARDWARE
                contentDescription=null
                importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
                playAnimation()
            }
            header.addView(motion,FrameLayout.LayoutParams(-1,d(11,a),Gravity.BOTTOM).apply{leftMargin=d(18,a);rightMargin=d(18,a);bottomMargin=d(0,a)})
        }"""
new="""        val motion = LottieAnimationView(a).apply {
            tag = MOTION_HEADER_TAG
            setAnimation(R.raw.rovex_header_flow)
            repeatCount = LottieDrawable.INFINITE
            repeatMode = LottieDrawable.RESTART
            speed = 0.55f
            alpha = if (ThemeManager.isDark(a)) 0.32f else 0.48f
            renderMode = RenderMode.HARDWARE
            contentDescription = null
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) { v.post { (v as? LottieAnimationView)?.let { updateMotionView(it, a) } } }
                override fun onViewDetachedFromWindow(v: View) { runCatching { (v as? LottieAnimationView)?.cancelAnimation() } }
            })
        }
        header.addView(motion, FrameLayout.LayoutParams(-1, d(11,a), Gravity.BOTTOM).apply {
            leftMargin = d(18,a); rightMargin = d(18,a); bottomMargin = 0
        })"""
rep(p,old,new,"all-theme animated wordmark underline")

p=f("SettingsScreen.kt")
rep(p,'        root.addView(category("Background wallpaper","Choose a local image from Gallery, or remove the current wallpaper","▧",Color.rgb(100,170,235)){showWallpaperDialog()},LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(6),0,0)})',
    '        root.addView(category("Background wallpaper","Choose a local image from Gallery, or remove the current wallpaper","▧",Color.rgb(100,170,235)){showWallpaperDialog()},LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(6),0,0)})\n        root.addView(category("Live animated backgrounds","Theme-adaptive flowing colour motion • switch off for a still background","≈",ThemeManager.accent(activity)){showLiveMotionDialog()},LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(6),0,0)})',
    "live motion settings entry")
dialog="""    private fun showLiveMotionDialog() {
        val dialog = Dialog(activity)
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(16))
            background = rounded(ThemeManager.dialogBg(activity), 24)
        }
        root.addView(TextView(activity).apply {
            text = "Live animated backgrounds"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ThemeManager.text(activity))
        })
        root.addView(TextView(activity).apply {
            text = "Controls moving theme wallpapers and flowing Home background/card layers. Turning this off keeps the selected theme colours and tap feedback, but stops live background motion."
            textSize = 12.5f
            setTextColor(ThemeManager.muted(activity))
            setPadding(0, dp(5), 0, dp(12))
        })
        val state = TextView(activity).apply { textSize = 11.5f; setTextColor(ThemeManager.muted(activity)) }
        val toggle = SwitchCompat(activity).apply {
            isChecked = RovexLiveMotionSettings.enabled(activity)
            contentDescription = "Enable live animated backgrounds"
        }
        fun updateState(enabled: Boolean) { state.text = if (enabled) "Live motion is ON" else "Live motion is OFF • still backgrounds" }
        updateState(toggle.isChecked)
        toggle.setOnCheckedChangeListener { _, enabled ->
            RovexLiveMotionSettings.setEnabled(activity, enabled)
            updateState(enabled)
        }
        root.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(state, LinearLayout.LayoutParams(0, dp(44), 1f))
            addView(toggle, LinearLayout.LayoutParams(dp(56), dp(40)))
        })
        root.addView(TextView(activity).apply {
            text = "DONE"; textSize = 12f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setTextColor(ThemeManager.accent(activity)); setPadding(0, dp(12), 0, dp(2))
            setOnClickListener { dialog.dismiss() }
        })
        dialog.setContentView(root)
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout((activity.resources.displayMetrics.widthPixels * .92f).toInt(), WindowManager.LayoutParams.WRAP_CONTENT)
    }

"""
anchor="    private fun showWallpaperDialog(){"
if anchor not in p.read_text(): raise SystemExit("[638-motion] missing live motion dialog insertion anchor")
rep(p,anchor,dialog+anchor,"live motion dialog")

# Postconditions verify the toggle actually controls native + imported motion and is visible in Settings.
home=p=f("RovexHomeRevolution.kt")
s=home.read_text()
if s.count("RovexLiveMotionSettings.enabled(a)") < 2 or "MOTION_HEADER_TAG" not in s:
    raise SystemExit("[638-motion] Home Lottie motion toggle wiring incomplete")
if "RovexLiveMotionSettings.enabled(app)" not in f("RovexLivingBackgroundDrawable.kt").read_text():
    raise SystemExit("[638-motion] native background toggle wiring incomplete")
if "showLiveMotionDialog()" not in f("SettingsScreen.kt").read_text():
    raise SystemExit("[638-motion] user toggle missing")
print("[638-motion] added live wallpaper switch; all eight themes now get theme-aware animated wordmark + Lottie Home layers; motion respects the switch")
