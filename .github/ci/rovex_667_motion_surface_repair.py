#!/usr/bin/env python3
"""Phase 667: restore independently controlled live motion and make appearance controls discoverable."""
from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve()
pkg = root / "app/src/main/java/com/localqbank/library"
gradle = root / "app/build.gradle.kts"
theme_path = pkg / "ThemeManager.kt"
settings_path = pkg / "SettingsScreen.kt"
for p in (gradle, theme_path, settings_path, pkg / "RovexLiveMotionSettings.kt", pkg / "AnimationPolicy.kt"):
    if not p.is_file():
        raise SystemExit(f"[667] FATAL: missing required input: {p}")

g = gradle.read_text(encoding="utf-8")
if 'versionName = "8.3.661"' not in g or "versionCode = 747" not in g:
    raise SystemExit("[667] FATAL: expected v8.3.661 / code 747")

theme = theme_path.read_text(encoding="utf-8")
old_base = '''        // Static wallpaper foundation: no full-screen Lottie strokes/lines behind UI.
        val base: Drawable = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(profile(c).backgroundA, profile(c).backgroundB)
        )'''
new_base = '''        // Live motion is opt-in through the existing user setting and Android animation policy.
        // The Lottie layer is deliberately subtle; wallpaper is kept above it as the visual anchor.
        val base: Drawable = if (RovexLiveMotionSettings.enabled(c) && AnimationPolicy.enabled(c)) {
            RovexImportedMotionBackgroundDrawable(c, profile(c))
        } else {
            GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(profile(c).backgroundA, profile(c).backgroundB))
        }'''
if theme.count(old_base) != 1:
    raise SystemExit("[667] FATAL: expected static background anchor not found exactly once")
theme = theme.replace(old_base, new_base, 1)
# The opaque wallpaper had completely hidden the animated layer beneath it. Keep the wallpaper
# visually dominant while letting the existing 18/255-alpha motion layer remain perceptible.
if not re_full_opacity(theme):
    raise SystemExit("[667] FATAL: unexpected wallpaper alpha implementation; inspect manually")
theme = theme.replace("val alpha = 255", "val alpha = if (RovexLiveMotionSettings.enabled(c) && AnimationPolicy.enabled(c)) 218 else 255", 1)
theme_path.write_text(theme, encoding="utf-8")

settings = settings_path.read_text(encoding="utf-8")
normal_anchor = '''        root.addView(button(if (!glassEnabled) "✓  Normal / Solid" else "Normal / Solid") {
            RovexSurfaceSettings.setGlassEnabled(activity, false)
            activity.recreate()
        }, lp())
'''
if settings.count(normal_anchor) != 1:
    raise SystemExit("[667] FATAL: Glass/Normal controls not found in Appearance settings")
motion_control = normal_anchor + '''        section(root, "LIVE MOTION")
        val motionEnabled = RovexLiveMotionSettings.enabled(activity) && AnimationPolicy.enabled(activity)
        root.addView(TextView(activity).apply {
            text = "Animated wallpaper is independent of the surface style. Turn it off to keep the wallpaper still; Android animation accessibility settings are respected."
            textSize = 12f
            setTextColor(ThemeManager.muted(activity))
            setPadding(dp(2), 0, dp(2), dp(8))
        }, lp())
        root.addView(button(if (motionEnabled) "✓  Live Motion On" else "Live Motion Off") {
            RovexLiveMotionSettings.setEnabled(activity, !RovexLiveMotionSettings.enabled(activity))
            activity.recreate()
        }, lp())
'''
settings = settings.replace(normal_anchor, motion_control, 1)
settings_path.write_text(settings, encoding="utf-8")

g = g.replace('versionName = "8.3.661"', 'versionName = "8.3.662"', 1).replace("versionCode = 747", "versionCode = 748", 1)
if 'versionName = "8.3.662"' not in g or "versionCode = 748" not in g:
    raise SystemExit("[667] FATAL: version bump failed")
gradle.write_text(g, encoding="utf-8")

# Structural assertions are intentionally scoped to generated final source, not strings in the overlay.
assert "RovexImportedMotionBackgroundDrawable(c, profile(c))" in theme
assert "RovexLiveMotionSettings.enabled(c) && AnimationPolicy.enabled(c)" in theme
assert "val alpha = if (RovexLiveMotionSettings.enabled(c) && AnimationPolicy.enabled(c)) 218 else 255" in theme
assert "section(root, \"LIVE MOTION\")" in settings
assert "Live Motion Off" in settings and "Live Motion On" in settings
assert "RovexLiveMotionSettings.setEnabled(activity, !RovexLiveMotionSettings.enabled(activity))" in settings
assert "Liquid Glass" in settings and "Normal / Solid" in settings
print("[667] PASS: live motion restored behind wallpaper at restrained opacity; independent Appearance control; Glass/Normal retained; v8.3.662 / 748")

def re_full_opacity(text):
    return "val alpha = 255" in text
