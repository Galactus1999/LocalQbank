#!/usr/bin/env python3
from pathlib import Path
import sys

P = Path(sys.argv[1]).resolve()
K = P / "app/src/main/java/com/localqbank/library"
gradle = P / "app/build.gradle.kts"
system_ui = K / "SystemUi.kt"
sound = K / "RovexSoundFeedback.kt"
profile = K / "BenExamProfile.kt"
policy_test = P / "app/src/test/java/com/localqbank/library/BenQuestionAiModePromptTest.kt"

for p in (gradle, system_ui, sound, profile, policy_test):
    if not p.is_file():
        raise SystemExit(f"[647] required source missing: {p.relative_to(P)}")

g = gradle.read_text()
if 'versionName = "8.3.646"' not in g or "versionCode = 732" not in g:
    raise SystemExit("[647] expected v8.3.646 / versionCode 732")
gradle.write_text(g.replace('versionName = "8.3.646"', 'versionName = "8.3.647"', 1).replace("versionCode = 732", "versionCode = 733", 1))

# Root cause: SystemUi.immersive attached the adaptive inset listener to android.R.id.content
# (the decor's FrameLayout), not the actual screen root. That decor padding is shared with the
# window/content transition and can shift weighted screens; put safe insets on the activity root.
s = system_ui.read_text()
old = """        val content=activity.findViewById<View>(android.R.id.content)
        if(content!=null){
            AdaptiveLayoutManager.install(
                activity, content, topExtraDp=0, bottomExtraDp=0,
                keepStatusBarVisible=false, protectDisplayCutout=false
            )
        }"""
new = """        val content=activity.findViewById<ViewGroup>(android.R.id.content)
        if(content!=null){
            // The decor content FrameLayout is not the activity's layout root. Never place
            // adaptive safe-area padding on that wrapper: it survives child replacement and
            // can create large blank offsets when a screen is entered/recreated.
            ViewCompat.setOnApplyWindowInsetsListener(content, null)
            content.setPadding(0,0,0,0)
            content.post {
                if(activity.isFinishing || activity.isDestroyed) return@post
                val screenRoot = content.getChildAt(0) ?: return@post
                AdaptiveLayoutManager.install(
                    activity, screenRoot, topExtraDp=0, bottomExtraDp=0,
                    keepStatusBarVisible=false, protectDisplayCutout=false
                )
            }
        }"""
if s.count(old) != 1:
    raise SystemExit(f"[647] expected one decor-insets owner, found {s.count(old)}")
s = s.replace(old, new, 1)
system_ui.write_text(s)

# SoundPool loads asynchronously, but the old not-ready/stream-failure path called MediaPlayer.create
# synchronously from ACTION_DOWN. This is both a delayed cue and a main-thread decode/allocation stall.
# Phase 638 must remove those fallbacks; fail closed if the generated source regresses.
import re
sound_text = sound.read_text()
# Inspect executable Kotlin, not comments/documentation that may mention the old call.
# The previous literal substring check falsely failed when a comment retained the example text.
sound_code = re.sub(r"/\*.*?\*/", "", sound_text, flags=re.S)
sound_code = re.sub(r"//[^\n]*", "", sound_code)
unsafe_touch_call = re.search(
    r"(?m)^\s*return\s+playDirectFallback\s*\(\s*context\s*,\s*cue\s*\)",
    sound_code
)
if unsafe_touch_call:
    raise SystemExit("[647] executable synchronous touch fallback remains; Phase 638 repair did not apply")
if "RovexSoundFeedback.preload(this)" not in (K / "ResilienceManager.kt").read_text():
    raise SystemExit("[647] application-level SoundPool preload is missing")
if "private fun playDirectFallback" not in sound_text:
    raise SystemExit("[647] fallback helper disappeared unexpectedly; audit sound architecture")
p = profile.read_text()
for mode in ("PYQ_CONTEXT(", "PYT_CONTEXT(", "FUTURE_RELATED(", "OTHER_OPTIONS("):
    start = p.find(mode)
    if start < 0:
        raise SystemExit(f"[647] missing Ben exam mode: {mode}")
    end = p.find("\n", start)
    # Kotlin enum prompt entries can be multiline; bound each check by the next enum entry.
    next_positions = [p.find("\n    " + name + "(", start + 1) for name in
                      ("PYQ_CONTEXT", "PYT_CONTEXT", "FUTURE_RELATED", "OTHER_OPTIONS")]
    next_positions = [x for x in next_positions if x > start]
    stop = min(next_positions) if next_positions else len(p)
    if "SOURCE POLICY (mandatory)" not in p[start:stop]:
        raise SystemExit(f"[647] source policy missing from {mode}")

t = policy_test.read_text()
if 'modes.all { it.prompt.contains("SOURCE POLICY (mandatory)") }' not in t:
    raise SystemExit("[647] regression test does not assert the mandatory source policy")

print("[647] applied v8.3.647 / versionCode 733")
print("[647] immersive insets are now attached to the actual activity root, not android.R.id.content")
print("[647] no synchronous MediaPlayer fallback on touch; app-start SoundPool preload remains required")
print("[647] all four Ben+AI modes enforce latest verifiable standard textbooks and official institutional sources only")
