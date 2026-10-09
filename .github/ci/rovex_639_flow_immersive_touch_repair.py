#!/usr/bin/env python3
from pathlib import Path
import sys

P = Path(sys.argv[1]).resolve()
K = P / "app/src/main/java/com/localqbank/library"

def file(name):
    path = K / name
    if not path.is_file():
        raise SystemExit(f"[639] missing expected source: {path}")
    return path

def replace(path, old, new, label, count=1):
    text = path.read_text()
    actual = text.count(old)
    if actual < count:
        raise SystemExit(f"[639] missing/changed anchor: {label} (found {actual}, expected {count})")
    path.write_text(text.replace(old, new, count))

gradle = P / "app/build.gradle.kts"
g = gradle.read_text()
if 'versionName = "8.3.638"' not in g or "versionCode = 724" not in g:
    raise SystemExit("[639] wrong version baseline; expected v8.3.638 / 724")
gradle.write_text(g.replace('versionName = "8.3.638"', 'versionName = "8.3.639"', 1).replace("versionCode = 724", "versionCode = 725", 1))

# Root cause: AdaptiveLayoutManager defaults to showing system bars and was undoing QuizActivity's hide().
p = file("QuizActivity.kt")
replace(p,
    "AdaptiveLayoutManager.install(this@QuizActivity, built, topExtraDp = 0, bottomExtraDp = 0) { _ ->",
    "AdaptiveLayoutManager.install(this@QuizActivity, built, topExtraDp = 0, bottomExtraDp = 0, keepStatusBarVisible = false) { _ ->",
    "quiz immersive inset ownership")

# Root cause: phase 638 made the footer scrim transparent, letting the last scroll row show through.
# Clip the viewport and use an opaque theme surface without adding a border.
p = file("QuizLayoutBuilder.kt")
replace(p, "            clipChildren = false", "            clipChildren = true\n            clipToPadding = true", "quiz root clipping")
replace(p, "            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS\n            setBackgroundColor(ThemeManager.panel(context))",
       "            overScrollMode = View.OVER_SCROLL_NEVER\n            clipChildren = true\n            clipToPadding = true\n            isVerticalFadingEdgeEnabled = false\n            setBackgroundColor(ThemeManager.panel(context))", "scroll edge/clip policy")
replace(p, "            background=android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)\n        }\n        val actionBar",
       "            background=android.graphics.drawable.ColorDrawable(ThemeManager.panel(context))\n        }\n        val actionBar", "opaque footer scrim")
replace(p, "        val top = LinearLayout(context).apply {\n            orientation = LinearLayout.HORIZONTAL",
       "        val top = LinearLayout(context).apply {\n            tag = \"quiz:header\"\n            orientation = LinearLayout.HORIZONTAL", "quiz header theme ownership")
replace(p, "        val infoRow = LinearLayout(context).apply {\n            orientation = LinearLayout.HORIZONTAL",
       "        val infoRow = LinearLayout(context).apply {\n            tag = \"quiz:progress\"\n            orientation = LinearLayout.HORIZONTAL", "quiz progress theme ownership")
replace(p, "        val actionWrap = FrameLayout(context).apply {\n            setPadding",
       "        val actionWrap = FrameLayout(context).apply {\n            tag = \"quiz:footer\"\n            setPadding", "quiz footer theme ownership")

# Recolour the imported licensed Lottie asset from the saved Colour Flow palette. Motion geometry is untouched.
(K / "RovexMotionAssetLoader.kt").write_text("""package com.localqbank.library

import android.content.Context
import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** Re-colours an imported licensed Lottie gradient; it never synthesizes or changes animation motion. */
object RovexMotionAssetLoader {
    private const val ASSET = "rovex/motion/gradient_animated_background.json"
    private val rawCache = ConcurrentHashMap<String, String>()
    private val themedCache = ConcurrentHashMap<String, String>()

    fun themedJson(context: Context, first: Int = RovexColorFlowTextView.colorOne(context),
                   second: Int = RovexColorFlowTextView.colorTwo(context)): String {
        val key = "$first:$second"
        return themedCache[key] ?: synchronized(this) {
            themedCache[key] ?: run {
                val raw = rawCache[ASSET] ?: context.applicationContext.assets.open(ASSET)
                    .bufferedReader().use { it.readText() }.also { rawCache[ASSET] = it }
                val json = JSONObject(raw)
                recolour(json, first, second)
                json.toString().also { themedCache[key] = it }
            }
        }
    }

    private fun recolour(node: Any?, first: Int, second: Int) {
        when (node) {
            is JSONObject -> {
                when (node.optString("ty")) {
                    "gf" -> recolourGradient(node.optJSONObject("g"), first, second)
                    "st" -> recolourSolid(node.optJSONObject("c")?.opt("k"), first)
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (key == "g" && node.optString("ty") == "gf") continue
                    if (key == "c" && node.optString("ty") == "st") continue
                    recolour(node.opt(key), first, second)
                }
            }
            is JSONArray -> for (i in 0 until node.length()) recolour(node.opt(i), first, second)
        }
    }

    private fun recolourGradient(gradient: JSONObject?, first: Int, second: Int) {
        gradient ?: return
        recolourGradientValue(gradient.opt("k"), gradient.optInt("p", 3).coerceIn(2, 8), first, second)
    }

    private fun recolourGradientValue(value: Any?, stops: Int, first: Int, second: Int) {
        when (value) {
            is JSONObject -> {
                val keys = value.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val child = value.opt(key)
                    if ((key == "s" || key == "e") && child is JSONArray) recolourStops(child, stops, first, second)
                    else recolourGradientValue(child, stops, first, second)
                }
            }
            is JSONArray -> {
                if (value.length() >= stops * 4 && value.opt(0) is Number) recolourStops(value, stops, first, second)
                else for (i in 0 until value.length()) recolourGradientValue(value.opt(i), stops, first, second)
            }
        }
    }

    private fun recolourStops(values: JSONArray, stops: Int, first: Int, second: Int) {
        val usable = minOf(stops, values.length() / 4)
        for (i in 0 until usable) {
            val base = i * 4
            val t = values.optDouble(base, i.toDouble() / (usable - 1).coerceAtLeast(1)).toFloat().coerceIn(0f, 1f)
            val colour = mix(first, second, t)
            values.put(base + 1, Color.red(colour) / 255.0)
            values.put(base + 2, Color.green(colour) / 255.0)
            values.put(base + 3, Color.blue(colour) / 255.0)
        }
    }

    private fun recolourSolid(value: Any?, colour: Int) {
        if (value !is JSONArray || value.length() < 3) return
        value.put(0, Color.red(colour) / 255.0)
        value.put(1, Color.green(colour) / 255.0)
        value.put(2, Color.blue(colour) / 255.0)
    }

    private fun mix(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt().coerceIn(0, 255),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt().coerceIn(0, 255),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt().coerceIn(0, 255)
    )
}
""")

# Surface animation has its own toggle inside Colour Flow; the existing Live Background switch remains the master switch.
p = file("RovexLiveMotionSettings.kt")
replace(p, "    private const val KEY_ENABLED = \"enabled\"",
        "    private const val KEY_ENABLED = \"enabled\"\n    private const val KEY_SURFACE_FLOW = \"surface_flow_enabled\"", "surface flow preference key")
replace(p, "    fun setEnabled(context: Context, enabled: Boolean) {\n        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply()\n    }",
        "    fun setEnabled(context: Context, enabled: Boolean) {\n        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply()\n    }\n    fun surfaceFlowEnabled(context: Context): Boolean = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_SURFACE_FLOW, true)\n    fun setSurfaceFlowEnabled(context: Context, enabled: Boolean) { context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_SURFACE_FLOW, enabled).apply() }",
        "surface flow preference accessors")
p = file("RovexColorFlowTextView.kt")
replace(p, "        fun colorOne(c:Context)=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getInt(C1,Color.rgb(255,59,48))",
        "        fun colorOne(c:Context):Int { val p=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE); val old=Color.rgb(255,59,48); val saved=p.getInt(C1,old); return if(!p.contains(C1)||saved==old) ThemeManager.accent(c) else saved }",
        "remove default red flow")
replace(p, "        fun colorTwo(c:Context)=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getInt(C2,Color.rgb(255,212,59))",
        "        fun colorTwo(c:Context):Int { val p=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE); val old=Color.rgb(255,212,59); val saved=p.getInt(C2,old); return if(!p.contains(C2)||saved==old) ThemeManager.accentSecondary(c) else saved }",
        "remove default yellow flow")
p = file("SettingsScreen.kt")
replace(p, "        var flow=viewModel.flowTextEnabled()\n        val dialog=Dialog(activity)",
        "        var flow=viewModel.flowTextEnabled()\n        var surfaceFlow=RovexLiveMotionSettings.surfaceFlowEnabled(activity)\n        val dialog=Dialog(activity)", "surface flow state")
replace(p, "        val normal=CheckBox(activity).apply{text=\"Keep colour-flow labels as normal text\";textSize=13f;isChecked=!flow;setTextColor(ThemeManager.text(activity));setOnCheckedChangeListener{_,checked->flow=!checked}}\n        root.addView(normal)",
        "        val normal=CheckBox(activity).apply{text=\"Keep colour-flow labels as normal text\";textSize=13f;isChecked=!flow;setTextColor(ThemeManager.text(activity));setOnCheckedChangeListener{_,checked->flow=!checked}}\n        root.addView(normal)\n        val surfaces=CheckBox(activity).apply{text=\"Animate pastel card surfaces using these colours\";textSize=13f;isChecked=surfaceFlow;setTextColor(ThemeManager.text(activity));setOnCheckedChangeListener{_,checked->surfaceFlow=checked}}\n        root.addView(surfaces)",
        "surface flow control in Colour Flow")
replace(p, "buttons.addView(TextView(activity).apply{text=\"RESET\";textSize=12f;typeface=Typeface.DEFAULT_BOLD;gravity=Gravity.CENTER;setTextColor(ThemeManager.accent(activity));setOnClickListener{viewModel.resetColourFlow();dialog.dismiss();activity.recreate()}},LinearLayout.LayoutParams(0,dp(46),1f))",
        "buttons.addView(TextView(activity).apply{text=\"RESET\";textSize=12f;typeface=Typeface.DEFAULT_BOLD;gravity=Gravity.CENTER;setTextColor(ThemeManager.accent(activity));setOnClickListener{viewModel.resetColourFlow();RovexLiveMotionSettings.setSurfaceFlowEnabled(activity,true);dialog.dismiss();activity.recreate()}},LinearLayout.LayoutParams(0,dp(46),1f))",
        "reset surface flow setting")
replace(p, "buttons.addView(TextView(activity).apply{text=\"SAVE\";textSize=12f;typeface=Typeface.DEFAULT_BOLD;gravity=Gravity.CENTER;setTextColor(ThemeManager.accent(activity));setOnClickListener{viewModel.saveColourFlow(c1,c2,flow,panel);dialog.dismiss();activity.recreate()}},LinearLayout.LayoutParams(0,dp(46),1f))",
        "buttons.addView(TextView(activity).apply{text=\"SAVE\";textSize=12f;typeface=Typeface.DEFAULT_BOLD;gravity=Gravity.CENTER;setTextColor(ThemeManager.accent(activity));setOnClickListener{viewModel.saveColourFlow(c1,c2,flow,panel);RovexLiveMotionSettings.setSurfaceFlowEnabled(activity,surfaceFlow);dialog.dismiss();activity.recreate()}},LinearLayout.LayoutParams(0,dp(46),1f))",
        "save surface flow setting")

# Fix the square inset overlay and colourize the imported Lottie with the actual selected palette.
p = file("RovexHomeRevolution.kt")
replace(p, "        } else motionAlpha(c, tag == MOTION_BG_TAG)", "        } else if (tag.startsWith(MOTION_CARD_PREFIX)) motionAlpha(c, false).coerceAtMost(0.12f) else motionAlpha(c, tag == MOTION_BG_TAG)", "theme-safe card alpha in refresh path")
replace(p, "        } else motionAlpha(c, tag == MOTION_BG_TAG)", "        } else if (tag.startsWith(MOTION_CARD_PREFIX)) motionAlpha(c, false).coerceAtMost(0.12f) else motionAlpha(c, tag == MOTION_BG_TAG)", "theme-safe card alpha in refresh path")
replace(p, "        view.visibility = if (motionPolicyAllowed) View.VISIBLE else View.GONE",
        "        val cardMotionAllowed = !tag.startsWith(MOTION_CARD_PREFIX) || RovexLiveMotionSettings.surfaceFlowEnabled(c)\n        view.visibility = if (motionPolicyAllowed && cardMotionAllowed) View.VISIBLE else View.GONE",
        "surface flow visibility policy")
replace(p, "            setAnimation(\"rovex/motion/gradient_animated_background.json\")",
        "            setAnimationFromJson(RovexMotionAssetLoader.themedJson(c), \"rovex_home_gradient_${RovexColorFlowTextView.colorOne(c)}_${RovexColorFlowTextView.colorTwo(c)}\")",
        "theme-colour imported Lottie")
replace(p, "            alpha = motionAlpha(c, background)",
        "            alpha = if (background) motionAlpha(c, true) else motionAlpha(c, false).coerceAtMost(0.12f)",
        "keep card flow low opacity")
replace(p, "        quote.addView(RovexWaveTextView(a).apply{text=\"“$dailyQuote”\";textSize=16.5f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.text(a));tag=\"rovex_home_daily_quote\";includeFontPadding=false},LinearLayout.LayoutParams(-1,-2).apply{topMargin=d(6,a)})",
        "        quote.addView(RovexColorFlowTextView(a).apply{text=\"“$dailyQuote”\";textSize=16.5f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.text(a));tag=\"rovex_home_daily_quote\";includeFontPadding=false},LinearLayout.LayoutParams(-1,-2).apply{topMargin=d(6,a)})",
        "motivation quote follows Colour Flow")
replace(p, "        val box=FrameLayout(a).apply{background=card(a,index);setPadding(d(14,a),d(13,a),d(10,a),d(10,a));isClickable=true;isFocusable=true;elevation=d(4,a).toFloat();setOnClickListener{target()}}",
        "        val box=FrameLayout(a).apply{background=card(a,index);setPadding(0,0,0,0);clipChildren=true;clipToPadding=true;isClickable=true;isFocusable=true;elevation=d(4,a).toFloat();setOnClickListener{target()}}",
        "feature card bounds")
replace(p, "            box.addView(clip, FrameLayout.LayoutParams(-1,-1).apply {\n                leftMargin = -d(14,a); topMargin = -d(13,a); rightMargin = -d(10,a); bottomMargin = -d(10,a)\n            })",
        "            box.addView(clip, FrameLayout.LayoutParams(-1,-1))",
        "remove negative inset margins")
replace(p, "        val col=LinearLayout(a).apply{orientation=LinearLayout.VERTICAL}",
        "        val col=LinearLayout(a).apply{orientation=LinearLayout.VERTICAL;setPadding(d(14,a),d(13,a),d(10,a),d(10,a))}",
        "keep content padding above motion")
replace(p, "            col.addView(FrankensteinLogoView(a),LinearLayout.LayoutParams(d(54,a),d(54,a)).apply{bottomMargin=d(6,a)})",
        "            col.addView(RovexHeaderCosmicView(a),LinearLayout.LayoutParams(d(54,a),d(54,a)).apply{bottomMargin=d(6,a)})",
        "use imported living Rovex mark")

helper = """
    private fun withMotionSurface(view:View, a:MainActivity, name:String, radiusDp:Float=22f, fillHeight:Boolean=false):View {
        val surface = view.background
        view.tag = "rovex_motion_wrapped_content"
        view.background = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
        val wrapper = FrameLayout(a).apply { background = surface; clipChildren = true; clipToPadding = true }
        val clip = FrameLayout(a).apply {
            background = GradientDrawable().apply { cornerRadius = d(radiusDp.toInt(),a).toFloat(); setColor(Color.WHITE) }
            clipToOutline = true; clipChildren = true; clipToPadding = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        motionBackground(a, MOTION_CARD_PREFIX + name)?.let { clip.addView(it, FrameLayout.LayoutParams(-1,-1)) }
        wrapper.addView(clip, FrameLayout.LayoutParams(-1,-1))
        val ownClick = view.isClickable || view.hasOnClickListeners()
        if (ownClick) {
            view.isClickable = false
            wrapper.isClickable = true; wrapper.isFocusable = true
            wrapper.contentDescription = view.contentDescription
            wrapper.setOnClickListener { view.performClick() }
            RovexTouchFeedback.bind(wrapper)
        }
        wrapper.addView(view, FrameLayout.LayoutParams(-1, if (fillHeight) -1 else -2))
        return wrapper
    }

"""
anchor = "    private fun feature(a:MainActivity,title:String,sub:String,icon:String,index:Int,target:()->Unit):View{"
replace(p, anchor, helper + anchor, "shared card motion wrapper")
replacements = [
("content.addView(hero,LinearLayout.LayoutParams(-1,d(126,a)).apply{bottomMargin=d(13,a)})",
 "content.addView(withMotionSurface(hero,a,\"clinical-hero\",24f,true),LinearLayout.LayoutParams(-1,d(126,a)).apply{bottomMargin=d(13,a)})", "clinical hero"),
("content.addView(search,LinearLayout.LayoutParams(-1,d(52,a)).apply{bottomMargin=d(13,a)})",
 "content.addView(withMotionSurface(search,a,\"home-search\",22f,true),LinearLayout.LayoutParams(-1,d(52,a)).apply{bottomMargin=d(13,a)})", "Home search"),
("content.addView(online,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=d(10,a)})",
 "content.addView(withMotionSurface(online,a,\"online-card\",22f),LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=d(10,a)})", "online card"),
("content.addView(quote,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=d(12,a)})",
 "content.addView(withMotionSurface(quote,a,\"daily-motivation\",22f),LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=d(12,a)})", "daily motivation"),
("content.addView(progress,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=d(13,a)})",
 "content.addView(withMotionSurface(progress,a,\"today-progress\",22f),LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=d(13,a)})", "Today progress"),
("content.addView(qbox,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=d(13,a)})",
 "content.addView(withMotionSurface(qbox,a,\"qbank-library\",22f),LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=d(13,a)})", "QBank library"),
("content.addView(bookmarksBox,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=d(10,a)})",
 "content.addView(withMotionSurface(bookmarksBox,a,\"bookmarks\",22f),LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=d(10,a)})", "Bookmarks"),
("rr.addView(b,LinearLayout.LayoutParams(0,d(66,a),1f).apply{if(cellIndex>0)setMargins(d(6,a),0,0,0)})",
 "rr.addView(withMotionSurface(b,a,\"bookmark-${key}\",16f,true),LinearLayout.LayoutParams(0,d(66,a),1f).apply{if(cellIndex>0)setMargins(d(6,a),0,0,0)})", "bookmark collection cells"),
("tools.forEachIndexed{index,p->qr.addView(tv(a,p.first,13f,ThemeManager.text(a),true).apply{gravity=Gravity.CENTER;textAlignment=View.TEXT_ALIGNMENT_CENTER;background=card(a,index);setOnClickListener{p.second()}},LinearLayout.LayoutParams(d(98,a),d(70,a)).apply{rightMargin=d(7,a)})}",
 "tools.forEachIndexed{index,p->val tool=tv(a,p.first,13f,ThemeManager.text(a),true).apply{gravity=Gravity.CENTER;textAlignment=View.TEXT_ALIGNMENT_CENTER;background=card(a,index);setOnClickListener{p.second()}};qr.addView(withMotionSurface(tool,a,\"quick-tool-$index\",18f,true),LinearLayout.LayoutParams(d(98,a),d(70,a)).apply{rightMargin=d(7,a)})}", "quick tools"),
("content.addView(goal,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=d(13,a)})",
 "content.addView(withMotionSurface(goal,a,\"weekly-goal\",22f),LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=d(13,a)})", "weekly goal"),
("hr.addView(box,LinearLayout.LayoutParams(d(170,a),d(116,a)).apply{rightMargin=d(8,a)})",
 "hr.addView(withMotionSurface(box,a,\"continue-${src.fileName}\",18f,true),LinearLayout.LayoutParams(d(170,a),d(116,a)).apply{rightMargin=d(8,a)})", "Continue Learning cards")
]
for old, new, label in replacements:
    replace(p, old, new, label)

# Use the real app logo bitmap with imported Lottie motion, removing the generated lightning paths.
(K / "RovexHeaderCosmicView.kt").write_text("""package com.localqbank.library

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable
import com.airbnb.lottie.RenderMode

/** Living Rovex mark: original app-logo bitmap plus imported, theme-coloured Lottie motion. */
class RovexHeaderCosmicView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {
    private val motion: LottieAnimationView
    init {
        clipChildren = false; clipToPadding = false
        val density = resources.displayMetrics.density
        val clip = FrameLayout(context).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.TRANSPARENT) }
            clipToOutline = true; clipChildren = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        motion = LottieAnimationView(context).apply {
            tag = "rovex_home_motion_logo"
            setAnimationFromJson(RovexMotionAssetLoader.themedJson(context),
                "rovex_logo_flow_${RovexColorFlowTextView.colorOne(context)}_${RovexColorFlowTextView.colorTwo(context)}")
            repeatCount = LottieDrawable.INFINITE; repeatMode = LottieDrawable.RESTART; speed = 0.42f
            alpha = 0.18f; renderMode = RenderMode.HARDWARE
            contentDescription = null; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            isClickable = false; isFocusable = false
        }
        clip.addView(motion, FrameLayout.LayoutParams(-1,-1))
        addView(clip, FrameLayout.LayoutParams(-1,-1))
        val logo = ImageView(context).apply {
            setImageResource(R.drawable.rovex_app_logo); scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Rovex"; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        addView(logo, FrameLayout.LayoutParams(-1,-1).apply { val inset=(5f*density).toInt(); setMargins(inset,inset,inset,inset) })
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (AnimationPolicy.enabled(context) && RovexLiveMotionSettings.enabled(context)) motion.playAnimation() else motion.pauseAnimation()
    }
    override fun onDetachedFromWindow() { runCatching { motion.cancelAnimation() }; super.onDetachedFromWindow() }
}
""")

# SoundPool used one stream and only CLICK had a direct fallback. DEEP_TOUCH/THEME_SWITCH therefore
# failed on first use or when the single stream was occupied. Keep a bounded three-stream cue pool.
p = file("RovexSoundFeedback.kt")
replace(p, "                if (cue == Cue.CLICK) {\n                    // SoundPool loads asynchronously. A short UI cue must still work on the\n                    // very first tap, so use the same PCM asset through MediaPlayer while the\n                    // pool finishes loading. Do not queue a second click or the user can hear\n                    // two sounds when onLoadComplete fires.\n                    return playDirectFallback(context)\n                }\n                pending = cue\n                return false",
        "                if (cue == Cue.CLICK || cue == Cue.DEEP_TOUCH || cue == Cue.THEME_SWITCH) {\n                    // Immediate direct fallback; do not queue a duplicate after playing it.\n                    return playDirectFallback(context, cue)\n                }\n                pending = cue\n                return false", "first-tap fallback for all touch cues")
replace(p, "            if (stream == 0 && cue == Cue.CLICK) return playDirectFallback(context)",
        "            if (stream == 0 && (cue == Cue.CLICK || cue == Cue.DEEP_TOUCH || cue == Cue.THEME_SWITCH)) return playDirectFallback(context, cue)",
        "fallback on rejected stream")
replace(p, "SoundPool.Builder().setMaxStreams(1).setAudioAttributes(attrs).build()",
        "SoundPool.Builder().setMaxStreams(3).setAudioAttributes(attrs).build()", "allow brief tap cues")
replace(p, "    private fun playDirectFallback(context: Context): Boolean {\n        return runCatching {\n            stopFallbackPlayerLocked()\n            val player = android.media.MediaPlayer.create(context.applicationContext, R.raw.rovex_button_click)",
        "    private fun playDirectFallback(context: Context, cue: Cue): Boolean {\n        return runCatching {\n            stopFallbackPlayerLocked()\n            val resource = when (cue) { Cue.DEEP_TOUCH -> R.raw.rovex_deep_touch; Cue.THEME_SWITCH -> R.raw.rovex_theme_switch; Cue.CORRECT, Cue.MILESTONE -> R.raw.rover_correct; Cue.CLICK -> R.raw.rovex_button_click }\n            val player = android.media.MediaPlayer.create(context.applicationContext, resource)",
        "cue-specific direct fallback")

p = file("RovexTouchFeedback.kt")
replace(p, "object RovexTouchFeedback {\n    fun bind",
        "object RovexTouchFeedback {\n    private var lastCueAtMs = 0L\n    @Synchronized private fun playTouchCueOnce(context: android.content.Context) {\n        val now = android.os.SystemClock.uptimeMillis()\n        if (now - lastCueAtMs < 45L) return\n        lastCueAtMs = now\n        RovexSoundFeedback.playDeepTouch(context)\n    }\n    fun bind",
        "single sound for nested click targets")
replace(p, "if (soundOnTouch) RovexSoundFeedback.playDeepTouch(v.context)",
        "if (soundOnTouch) playTouchCueOnce(v.context)", "debounced touch cue")
replace(p, "            if (!complex && (view.isClickable || view.hasOnClickListeners())) {",
        "            if (!complex && view.tag?.toString() != \"rovex_motion_wrapped_content\" && (view.isClickable || view.hasOnClickListeners())) {",
        "do not rebind wrapped click content")

p = file("RovexHomeRevolution.kt")
replace(p, "    private const val MOTION_HEADER_TAG = \"rovex_home_motion_header\"",
        "    private const val MOTION_HEADER_TAG = \"rovex_home_motion_header\"\n    private const val MOTION_LOGO_TAG = \"rovex_home_motion_logo\"",
        "logo motion tag")
replace(p, "if (tag != MOTION_BG_TAG && !tag.startsWith(MOTION_CARD_PREFIX) && tag != MOTION_HEADER_TAG) return",
        "if (tag != MOTION_BG_TAG && !tag.startsWith(MOTION_CARD_PREFIX) && tag != MOTION_HEADER_TAG && tag != MOTION_LOGO_TAG) return",
        "logo motion visibility")
replace(p, "        view.alpha = if (tag == MOTION_HEADER_TAG) when (ThemeManager.get(c)) {",
        "        view.alpha = if (tag == MOTION_LOGO_TAG) 0.18f else if (tag == MOTION_HEADER_TAG) when (ThemeManager.get(c)) {",
        "logo motion alpha")

home = file("RovexHomeRevolution.kt").read_text()
quiz = file("QuizActivity.kt").read_text()
sound = file("RovexSoundFeedback.kt").read_text()
settings = file("SettingsScreen.kt").read_text()
if "keepStatusBarVisible = false" not in quiz: raise SystemExit("[639] quiz status-bar ownership incomplete")
if "RovexMotionAssetLoader.themedJson(c)" not in home or "RovexColorFlowTextView(a)" not in home: raise SystemExit("[639] themed imported motion/quote incomplete")
if "Animate pastel card surfaces using these colours" not in settings: raise SystemExit("[639] Colour Flow surface switch missing")
if "playDirectFallback(context, cue)" not in sound or "setMaxStreams(3)" not in sound: raise SystemExit("[639] touch cue fallback/concurrency repair incomplete")
if "leftMargin = -d(14,a)" in home: raise SystemExit("[639] old inset-square layout remains")
print("[639] applied v8.3.639 / versionCode 725")
print("[639] immersive quiz insets, opaque clipped footer, palette-recoloured imported Lottie, all Home card layers, Colour Flow switch, living imported logo, and first-tap sound fallback")
