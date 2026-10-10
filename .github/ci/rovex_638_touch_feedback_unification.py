#!/usr/bin/env python3
from pathlib import Path
import sys
P=Path(sys.argv[1]).resolve()
K=P/"app/src/main/java/com/localqbank/library"
def f(n):
 p=K/n
 if not p.is_file(): raise SystemExit("[638-touch] missing "+str(p))
 return p
def rep(p,a,b,label,count=1):
 s=p.read_text()
 if s.count(a)<count: raise SystemExit("[638-touch] missing anchor: "+label)
 p.write_text(s.replace(a,b,count))

p=f("RovexTouchFeedback.kt")
p.write_text("""package com.localqbank.library

import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup

/** Single touch contract: one sound cue, restrained press response and haptic per real click target. */
object RovexTouchFeedback {
    fun bind(view: View, soundOnTouch: Boolean = true) {
        if (view.getTag(R.id.rovex_touch_feedback_bound) == true) return
        view.setTag(R.id.rovex_touch_feedback_bound, true)
        view.setTag(R.id.rovexMotionInstalled, true)
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (soundOnTouch) RovexSoundFeedback.playDeepTouch(v.context)
                    if (AnimationPolicy.enabled(v.context)) {
                        v.animate().cancel()
                        v.animate().scaleX(.985f).scaleY(.985f).setDuration(70L)
                            .setInterpolator(android.view.animation.OvershootInterpolator(1.08f)).start()
                    }
                    runCatching { v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (AnimationPolicy.enabled(v.context)) {
                        v.animate().cancel()
                        v.animate().scaleX(1f).scaleY(1f).setDuration(110L)
                            .setInterpolator(android.view.animation.OvershootInterpolator(1.08f)).start()
                    }
                }
            }
            false
        }
    }

    /** Binds dynamic clickable descendants; complex gesture surfaces retain their own handlers. */
    fun bindTree(root: View) {
        fun walk(view: View) {
            val complex = view is android.webkit.WebView ||
                view is androidx.recyclerview.widget.RecyclerView ||
                view is android.widget.ScrollView ||
                view is android.widget.HorizontalScrollView ||
                view is android.widget.AbsListView ||
                view is android.widget.EditText
            if (!complex && (view.isClickable || view.hasOnClickListeners())) {
                val tag = view.tag?.toString().orEmpty()
                bind(view, soundOnTouch = !tag.startsWith("option:"))
            }
            if (view is ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i))
        }
        walk(root)
    }
}
""")

p=f("RovexMotion.kt")
old="""    fun install(view: View) {
        if (!view.isClickable || !view.isEnabled) return
        // Never replace a bespoke touch/scroll handler on complex interactive surfaces.
        // Press feedback belongs on actual click targets, not WebViews/scroll containers.
        if (view is WebView || view is RecyclerView || view is ScrollView || view is HorizontalScrollView) return
        if (view.id == com.localqbank.library.R.id.rovexPersistentBottomNavigation ||
            view.getTag()?.toString()?.startsWith("rovex_bottom_navigation") == true) return
        if (view.getTag(com.localqbank.library.R.id.rovexMotionInstalled) == true) return
        view.setTag(com.localqbank.library.R.id.rovexMotionInstalled, true)
        view.isHapticFeedbackEnabled = true
        view.setOnTouchListener { v, event ->
            if (!v.isEnabled || !v.isClickable) return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().cancel()
                    v.animate().scaleX(PRESS_SCALE).scaleY(PRESS_SCALE)
                        .setDuration(PRESS_MS).setInterpolator(OvershootInterpolator(1.15f)).start()
                    runCatching { v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().cancel()
                    v.animate().scaleX(1f).scaleY(1f)
                        .setDuration(RELEASE_MS).setInterpolator(OvershootInterpolator(1.15f)).start()
                }
            }
            false
        }
    }"""
new="""    fun install(view: View) {
        if (!view.isClickable || !view.isEnabled) return
        // Use the one shared listener; never replace bespoke WebView/scroll/drag handlers.
        if (view is WebView || view is RecyclerView || view is ScrollView || view is HorizontalScrollView) return
        if (view.id == com.localqbank.library.R.id.rovexPersistentBottomNavigation ||
            view.getTag()?.toString()?.startsWith("rovex_bottom_navigation") == true) return
        RovexTouchFeedback.bind(view, soundOnTouch = !(view.tag?.toString() ?: "").startsWith("option:"))
    }"""
rep(p,old,new,"AliveMotion listener consolidation")

for name in ["RovexBenProductionLayer.kt","RovexClinicalDayProductionLayer.kt","RovexQBankProductionLayer.kt"]:
 p=f(name)
 old="""    private fun bindPress(view: View) {
        if (!view.isClickable && !view.hasOnClickListeners()) return
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> v.animate().scaleX(.985f).scaleY(.985f).setDuration(70L).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(110L).start()
            }
            false
        }
    }"""
 rep(p,old,"    private fun bindPress(view: View) { RovexTouchFeedback.bind(view) }",name+" duplicate touch handler")

p=f("RovexThemeEngine.kt")
rep(p,"fun apply(a:Activity){val root=a.window.decorView.findViewById<ViewGroup>(android.R.id.content)?:return;root.background=ThemeManager.backgroundDrawable(a);walk(root,a,0)}",
    "fun apply(a:Activity){val root=a.window.decorView.findViewById<ViewGroup>(android.R.id.content)?:return;root.background=ThemeManager.backgroundDrawable(a);walk(root,a,0);RovexTouchFeedback.bindTree(root)}",
    "theme engine dynamic click binding")

p=f("RovexSoundFeedback.kt")
rep(p,"                val next = pending; pending = null\n","                val next = pending\n","retain pending cue")
rep(p,"                    if (nextReady && nextId != 0) {\n                        stopFallbackPlayerLocked()",
    "                    if (nextReady && nextId != 0) {\n                        pending = null\n                        stopFallbackPlayerLocked()","clear pending only when playable")

# Root cause of delayed touch audio: MediaPlayer.create() ran synchronously on the UI thread
# whenever an asynchronously-loaded SoundPool sample was not ready or play() returned stream 0.
# SoundPool is preloaded at app startup; skip a rare not-ready cue rather than decode late on tap.
p=f("RovexSoundFeedback.kt")
# SoundPool loads asynchronously; never construct MediaPlayer on the UI thread for tap cues.
# Patch the two behavior sites independently of comments/indentation so upstream formatting changes
# cannot break source discovery. Fail closed unless both known fallback call sites are present.
import re
sound_text = p.read_text()
fallback_pattern = re.compile(r"return\s+playDirectFallback\s*\(\s*context\s*,\s*cue\s*\)")
sound_text, fallback_count = fallback_pattern.subn("return false", sound_text)
# Some baseline variants already removed one or both fallbacks. Accept 0..2 matches,
# but reject unexpected growth and verify the unsafe call is absent after normalization.
if fallback_count > 2:
    raise SystemExit(f"[638-touch] unexpected number of synchronous touch fallback sites: {fallback_count}")
if fallback_pattern.search(sound_text):
    raise SystemExit("[638-touch] synchronous touch fallback remains after patch")
p.write_text(sound_text)

p=f("FlashcardStudyActivity.kt")
rep(p,"setOnClickListener{RovexSoundFeedback.playClick(this@FlashcardStudyActivity);click(this)}",
    "setOnClickListener{click(this)}","avoid duplicate flashcard button sound")
rep(p,"                MotionEvent.ACTION_DOWN->{\n                    dragging=true;moved=false;downX=event.rawX;downY=event.rawY;startX=view.x;startY=view.y",
    "                MotionEvent.ACTION_DOWN->{\n                    RovexSoundFeedback.playDeepTouch(this@FlashcardStudyActivity)\n                    dragging=true;moved=false;downX=event.rawX;downY=event.rawY;startX=view.x;startY=view.y",
    "movable control touch sound")

# Ensure the Home cards are bound even though their click targets are TextViews/FrameLayouts, not Android Buttons.
p=f("MainActivity.kt")
if "AliveMotion.installTree(it)" not in p.read_text():
    raise SystemExit("[638-touch] expected Home AliveMotion tree install missing")
print("[638-touch] unified sound + press feedback; repaired async sample race; bound clickable trees; preserved custom gestures")
