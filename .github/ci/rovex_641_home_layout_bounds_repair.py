#!/usr/bin/env python3
from pathlib import Path
import sys

P = Path(sys.argv[1]).resolve()
K = P / "app/src/main/java/com/localqbank/library"
home = K / "RovexHomeRevolution.kt"
gradle = P / "app/build.gradle.kts"
if not home.is_file() or not gradle.is_file():
    raise SystemExit("[641] expected Home source or Gradle file missing")
g = gradle.read_text()
if 'versionName = "8.3.640"' not in g or "versionCode = 726" not in g:
    raise SystemExit("[641] wrong baseline; expected v8.3.640 / versionCode 726")
gradle.write_text(g.replace('versionName = "8.3.640"', 'versionName = "8.3.641"', 1).replace("versionCode = 726", "versionCode = 727", 1))

s = home.read_text()
old = '''                val surfaceBackground = bg is GradientDrawable ||
                    bg is android.graphics.drawable.LayerDrawable ||
                    bg is RovexClinicalSurfaceDrawable
                val eligibleSize = child.width >= (120f * density).toInt() &&
                    child.height >= (52f * density).toInt()
                if (surfaceBackground && eligibleSize) {
                    val lp = child.layoutParams ?: continue
                    val index = parent.indexOfChild(child)
                    if (index < 0) continue
                    parent.removeViewAt(index)
                    val wrapper = withMotionSurface(
                        child, a, "auto-" + child.javaClass.simpleName + "-" + index,
                        22f, lp.height != ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    parent.addView(wrapper, index, lp)
                    // The original card is now owned by the wrapper; do not wrap its descendants.
                    continue
                }'''
new = '''                val surfaceBackground = bg is GradientDrawable ||
                    bg is android.graphics.drawable.LayerDrawable ||
                    bg is RovexClinicalSurfaceDrawable
                val lp = child.layoutParams
                // Only wrap bounded card-like views. A MATCH_PARENT height often belongs to
                // a page/viewport/content host; wrapping it in another MATCH_PARENT FrameLayout
                // changes the measurement chain and can stretch one pastel overlay over most
                // of the dashboard. Never auto-wrap scroll containers or web content.
                val boundedHeight = lp != null && lp.height > 0
                val viewportLike = child is ScrollView ||
                    child is HorizontalScrollView ||
                    child is android.webkit.WebView
                val eligibleSize = child.width >= (120f * density).toInt() &&
                    child.height >= (52f * density).toInt()
                if (surfaceBackground && eligibleSize && boundedHeight && !viewportLike) {
                    val index = parent.indexOfChild(child)
                    if (index < 0) continue
                    parent.removeViewAt(index)
                    val wrapper = withMotionSurface(
                        child, a, "auto-" + child.javaClass.simpleName + "-" + index,
                        22f, true
                    )
                    parent.addView(wrapper, index, lp!!)
                    // The original bounded card is now owned by the wrapper; do not wrap descendants.
                    continue
                }'''
if s.count(old) != 1:
    raise SystemExit("[641] bounded auto-wrap anchor mismatch: " + str(s.count(old)))
s = s.replace(old, new, 1)
home.write_text(s)

# Add regression checks against the actual wrapping policy, not only source token counts.
test = P / "app/src/androidTest/java/com/localqbank/library/RovexFlowSurfaceRegressionTest.kt"
if not test.is_file():
    raise SystemExit("[641] phase 640 instrumented Home regression test missing")
t = test.read_text()
anchor = '''                check(surfaces.size >= 8) { "Expected motion on at least eight Home card surfaces; found " + surfaces.size }'''
if t.count(anchor) != 1:
    raise SystemExit("[641] instrumented test anchor mismatch")
t = t.replace(anchor, '''                check(surfaces.size >= 8) { "Expected motion on at least eight Home card surfaces; found " + surfaces.size }
                check(surfaces.none { it.layoutParams?.height == ViewGroup.LayoutParams.MATCH_PARENT &&
                    it.tag?.toString()?.startsWith("rovex_motion_surface:auto-") == true }) {
                    "Auto-generated motion wrappers must not wrap MATCH_PARENT-height dashboard content"
                }''', 1)
test.write_text(t)

s = home.read_text()
if "val boundedHeight = lp != null && lp.height > 0" not in s or "&& boundedHeight && !viewportLike" not in s:
    raise SystemExit("[641] bounded surface eligibility postcondition failed")
if "lp.height != ViewGroup.LayoutParams.WRAP_CONTENT" in s:
    raise SystemExit("[641] unsafe fill-height heuristic remains")
if "Never auto-wrap scroll containers or web content." not in s:
    raise SystemExit("[641] viewport exclusion documentation missing")
print("[641] applied v8.3.641 / versionCode 727")
print("[641] auto-motion wrapping now requires a fixed positive height and excludes scroll/WebView viewports")
print("[641] fill-height behavior is restricted to bounded card surfaces; instrumented regression assertion added")
