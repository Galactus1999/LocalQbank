#!/usr/bin/env python3
"""Phase 680: remove the quiz's pale inset strip and reduce background animation cost."""
from pathlib import Path
import re, sys

root = Path(sys.argv[1]).resolve()
pkg = root / "app/src/main/java/com/localqbank/library"
gradle = root / "app/build.gradle.kts"
builder_path = pkg / "QuizLayoutBuilder.kt"
ambient_path = pkg / "RovexAmbientMotionDrawable.kt"
for p in (gradle, builder_path, ambient_path):
    if not p.is_file():
        raise SystemExit(f"[680] FATAL: required source missing: {p}")

g = gradle.read_text(encoding="utf-8")
if 'versionName = "8.3.677"' not in g or re.search(r"\bversionCode\s*=\s*763\b", g) is None:
    raise SystemExit("[680] FATAL: expected v8.3.677 / code 763 after Phase 679")

builder = builder_path.read_text(encoding="utf-8")
# The quiz root currently uses the theme wallpaper/background drawable. Since the root is
# inset below the camera cutout, that drawable leaves a pale band above the dark quiz header
# and may keep a full-screen animated drawable invalidating the quiz view tree.
old_root_bg = "background=ThemeManager.backgroundDrawable(context)"
new_root_bg = """// Quiz is a high-focus, low-motion surface. Paint the safe inset with the same
            // dark header family instead of exposing a pale wallpaper strip above the title.
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.rgb(17, 13, 43), Color.rgb(36, 24, 72))
            )"""
if builder.count(old_root_bg) != 1:
    raise SystemExit("[680] FATAL: quiz root background anchor missing/ambiguous")
if "root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))" not in builder:
    raise SystemExit("[680] FATAL: question scroll region anchor missing")
if "setBackgroundColor(ThemeManager.panel(context))" not in builder:
    raise SystemExit("[680] FATAL: question/options panel background anchor missing")
builder = builder.replace(old_root_bg, new_root_bg, 1)
if "background=ThemeManager.backgroundDrawable(context)" in builder:
    raise SystemExit("[680] FATAL: animated/theme wallpaper still attached to quiz root")
if "Color.rgb(17, 13, 43)" not in builder or "Color.rgb(36, 24, 72)" not in builder:
    raise SystemExit("[680] FATAL: static inset/header color not installed")

ambient = ambient_path.read_text(encoding="utf-8")
if "scheduleSelf(frame, SystemClock.uptimeMillis() + 125L)" not in ambient:
    raise SystemExit("[680] FATAL: expected Phase 667 animation scheduling anchor missing")
# Retain ambient motion but reduce redraw frequency from ~8fps to ~4fps normally and ~1fps
# under Battery Saver / low battery. This avoids changing user-facing animation policy.
ambient = ambient.replace(
    "import android.content.Context\n",
    "import android.content.Context\nimport android.os.BatteryManager\nimport android.os.PowerManager\n",
    1
)
density_anchor = "    private val density = context.resources.displayMetrics.density\n"
if ambient.count(density_anchor) != 1:
    raise SystemExit("[680] FATAL: ambient drawable density anchor missing/ambiguous")
ambient = ambient.replace(
    density_anchor,
    density_anchor + "    private val appContext = context.applicationContext\n",
    1
)
ambient = ambient.replace(
    "scheduleSelf(frame, SystemClock.uptimeMillis() + 125L)",
    "scheduleSelf(frame, SystemClock.uptimeMillis() + frameIntervalMs())",
    1
)
interval_anchor = "    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {"
interval_fn = """    private fun frameIntervalMs(): Long {
        val power = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val battery = appContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val level = battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        return if (power?.isPowerSaveMode == true || level in 0..20) 1000L else 250L
    }

"""
if ambient.count(interval_anchor) != 1:
    raise SystemExit("[680] FATAL: ambient visibility/lifecycle anchor missing/ambiguous")
ambient = ambient.replace(interval_anchor, interval_fn + interval_anchor, 1)
if "frameIntervalMs()" not in ambient or "+ 125L" in ambient:
    raise SystemExit("[680] FATAL: old high-frequency animation scheduler remains")
if "RovexAmbientMotionLifecycle" not in ambient or "appForeground" not in ambient:
    raise SystemExit("[680] FATAL: lifecycle-aware animation safety from Phase 667 was lost")

builder_path.write_text(builder, encoding="utf-8")
ambient_path.write_text(ambient, encoding="utf-8")
g = g.replace('versionName = "8.3.677"', 'versionName = "8.3.678"', 1).replace("versionCode = 763", "versionCode = 764", 1)
if 'versionName = "8.3.678"' not in g or re.search(r"\bversionCode\s*=\s*764\b", g) is None:
    raise SystemExit("[680] FATAL: version bump postcondition failed")
gradle.write_text(g, encoding="utf-8")
print("[680] PASS: quiz safe inset uses the dark header family; question/options panel stays theme-controlled.")
print("[680] PASS: quiz no longer attaches the animated wallpaper drawable to its root.")
print("[680] PASS: ambient motion is throttled to 4fps normally and 1fps under Battery Saver / <=20% battery.")
print("[680] PASS: lifecycle-aware visibility, quiz content hierarchy, and existing controls preserved.")
