#!/usr/bin/env python3
from pathlib import Path
import sys

P = Path(sys.argv[1]).resolve()
K = P / "app/src/main/java/com/localqbank/library"
home = K / "RovexHomeRevolution.kt"
gradle = P / "app/build.gradle.kts"
if not home.is_file() or not gradle.is_file():
    raise SystemExit("[643] expected Home source or Gradle file missing")
g = gradle.read_text()
if 'versionName = "8.3.642"' not in g or "versionCode = 728" not in g:
    raise SystemExit("[643] wrong baseline; expected v8.3.642 / versionCode 728")
gradle.write_text(g.replace('versionName = "8.3.642"', 'versionName = "8.3.643"', 1).replace("versionCode = 728", "versionCode = 729", 1))

s = home.read_text()
start = s.index("    private fun withMotionSurface(view:View, a:MainActivity, name:String, radiusDp:Float=22f, fillHeight:Boolean=false):View {")
end = s.index("    private fun installMotionSurfaces(", start)
replacement = '''    private fun withMotionSurface(view:View, a:MainActivity, name:String, radiusDp:Float=22f, fillHeight:Boolean=false):View {
        // Geometry safety fix: the previous wrapper replaced each original view's LayoutParams
        // with FrameLayout.LayoutParams(-1, -1/-2), and then layered a full-size Lottie viewport
        // over it. That extra measurement parent can expand cards or stretch pastel surfaces,
        // especially in nested LinearLayout/GridLayout/weighted rows. Return the original view
        // untouched until each motion surface can be implemented without changing layout geometry.
        // Preserve its background, LayoutParams, click target, elevation, tags and accessibility.
        return view
    }

'''
s = s[:start] + replacement + s[end:]
# Phase 642 is expected to disable heuristic reparenting. If running against a 641/640-derived
# source, disable it here as a second line of defense.
start = s.index("    private fun installMotionSurfaces(root:ViewGroup, a:MainActivity) {")
end = s.index("    private fun feature(", start)
replacement = '''    private fun installMotionSurfaces(root:ViewGroup, a:MainActivity) {
        // No heuristic view reparenting on Home. Motion must not alter card geometry.
        root.setTag(R.id.rovexMotionSurfaceScan, true)
    }

'''
s = s[:start] + replacement + s[end:]
home.write_text(s)

# Replace the old motion-wrapper structural test with a source/runtime invariant: the safety
# rollback must not manufacture any motion wrapper or change child layout params.
test = P / "app/src/androidTest/java/com/localqbank/library/RovexFlowSurfaceRegressionTest.kt"
test.parent.mkdir(parents=True, exist_ok=True)
test.write_text("""package com.localqbank.library

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RovexFlowSurfaceRegressionTest {
    @Test
    fun homeLayoutUsesOriginalCardGeometryWithoutSyntheticMotionWrappers() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.findViewById<ViewGroup>(R.id.dashboardRoot)
                    ?: error("Home dashboard root missing")
                val synthetic = mutableListOf<View>()
                fun walk(view: View) {
                    val tag = view.tag?.toString().orEmpty()
                    if (tag.startsWith("rovex_motion_surface:") || tag == "rovex_motion_clip") synthetic.add(view)
                    if (view is ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i))
                }
                walk(root)
                check(synthetic.isEmpty()) {
                    "Synthetic Home motion wrappers must remain disabled; found " + synthetic.size
                }
            }
        }
    }
}
""")

s = home.read_text()
if "return view" not in s[s.index("private fun withMotionSurface"):s.index("private fun installMotionSurfaces")]:
    raise SystemExit("[643] original-view geometry fallback missing")
if "No heuristic view reparenting on Home." not in s:
    raise SystemExit("[643] auto-reparenting disablement missing")
if "FrameLayout.LayoutParams(-1, if (fillHeight) -1 else -2)" in s:
    raise SystemExit("[643] layout-mutating wrapper implementation remains")
if test.is_file() and "synthetic Home motion wrappers are disabled" not in test.read_text():
    raise SystemExit("[643] runtime regression test missing")
print("[643] applied v8.3.643 / versionCode 729")
print("[643] root cause fixed: Home card wrapper no longer replaces LayoutParams or adds full-size Lottie viewport")
print("[643] original Home view geometry, backgrounds, click targets and accessibility are preserved")
print("[643] instrumented test rejects synthetic motion wrappers on the Home hierarchy")
