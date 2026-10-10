#!/usr/bin/env python3
from pathlib import Path
import sys

P = Path(sys.argv[1]).resolve()
K = P / "app/src/main/java/com/localqbank/library"
home = K / "RovexHomeRevolution.kt"
gradle = P / "app/build.gradle.kts"
if not home.is_file() or not gradle.is_file():
    raise SystemExit("[642] expected Home source or Gradle file missing")
g = gradle.read_text()
if 'versionName = "8.3.641"' not in g or "versionCode = 727" not in g:
    raise SystemExit("[642] wrong baseline; expected v8.3.641 / versionCode 727")
gradle.write_text(g.replace('versionName = "8.3.641"', 'versionName = "8.3.642"', 1).replace("versionCode = 727", "versionCode = 728", 1))

s = home.read_text()
start = s.index("    private fun installMotionSurfaces(root:ViewGroup, a:MainActivity) {")
end = s.index("    private fun feature(", start)
replacement = '''    private fun installMotionSurfaces(root:ViewGroup, a:MainActivity) {
        if (root.getTag(R.id.rovexMotionSurfaceScan) == true) return
        root.setTag(R.id.rovexMotionSurfaceScan, true)
        // Safety rollback: only explicitly authored Home card wrappers may animate.
        // Runtime discovery by drawable type/size cannot distinguish a card from a full-page
        // host or a large content panel reliably, even with positive-height checks. It caused
        // enormous pastel overlays and distorted Home measurement. Keep the recursive walk
        // solely for lifecycle discovery; never reparent arbitrary dashboard views here.
        fun markExisting(parent:ViewGroup) {
            for (i in 0 until parent.childCount) {
                val child = parent.getChildAt(i)
                val tag = child.tag?.toString().orEmpty()
                if (tag.startsWith("rovex_motion_surface:") ||
                    tag == "rovex_motion_clip" ||
                    tag == "rovex_motion_wrapped_content") continue
                if (child is ViewGroup) markExisting(child)
            }
        }
        markExisting(root)
    }

'''
s = s[:start] + replacement + s[end:]
home.write_text(s)

test = P / "app/src/androidTest/java/com/localqbank/library/RovexFlowSurfaceRegressionTest.kt"
if not test.is_file():
    raise SystemExit("[642] required Home regression test missing")
t = test.read_text()
# The test should distinguish intentionally authored wrappers from the removed auto-discovery.
anchor = '''                check(surfaces.size >= 8) { "Expected motion on at least eight Home card surfaces; found " + surfaces.size }'''
if t.count(anchor) != 1:
    raise SystemExit("[642] regression test anchor mismatch")
t = t.replace(anchor, '''                check(surfaces.size >= 5) { "Expected explicitly authored Home motion surfaces; found " + surfaces.size }
                check(surfaces.none { it.tag?.toString()?.startsWith("rovex_motion_surface:auto-") == true }) {
                    "Automatic view reparenting must remain disabled until screenshot geometry is validated"
                }''', 1)
# Drop phase 641 guard that is now superseded by the stronger no-auto-wrapper contract.
old = '''                check(surfaces.none { it.layoutParams?.height == ViewGroup.LayoutParams.MATCH_PARENT &&
                    it.tag?.toString()?.startsWith("rovex_motion_surface:auto-") == true }) {
                    "Auto-generated motion wrappers must not wrap MATCH_PARENT-height dashboard content"
                }
'''
t = t.replace(old, "")
test.write_text(t)

if "Safety rollback: only explicitly authored Home card wrappers may animate." not in s:
    raise SystemExit("[642] safe Home wrapper policy missing")
if '"auto-" + child.javaClass.simpleName' in s:
    raise SystemExit("[642] automatic view wrapping code remains")
if "Expected explicitly authored Home motion surfaces" not in t:
    raise SystemExit("[642] instrumented regression assertion missing")
print("[642] applied v8.3.642 / versionCode 728")
print("[642] disabled heuristic auto-wrapping/reparenting; existing explicit Home surfaces and animation lifecycle remain")
print("[642] instrumented test now rejects any automatic wrapper and verifies transparent clipping/motion layers")
