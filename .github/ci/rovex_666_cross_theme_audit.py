#!/usr/bin/env python3
"""Phase 666: cross-theme wallpaper fidelity and surface-mode regression audit."""
from __future__ import annotations
import hashlib
import json
import re
import sys
from pathlib import Path

ROOT = Path(sys.argv[1]).resolve()
APP = ROOT / "app"
PKG = APP / "src/main/java/com/localqbank/library"
GRADLE = APP / "build.gradle.kts"
MANIFEST = APP / "src/main/assets/rovex/RovexVisualAssetManifest.json"
THEMES = ("light", "pastel", "mint", "sunset", "lavender", "amoled")
RESOURCES = {
    "light": "rovex_wallpaper_light",
    "pastel": "rovex_wallpaper_pastel",
    "mint": "rovex_wallpaper_mint",
    "sunset": "rovex_wallpaper_sunset",
    "lavender": "rovex_wallpaper_lavender",
    "amoled": "rovex_wallpaper_amoled",
}

def fail(message: str) -> None:
    raise SystemExit("[666] FATAL: " + message)

def function_body(source: str, marker: str) -> str:
    if source.count(marker) != 1:
        fail("missing/ambiguous function anchor: " + marker)
    start = source.index(marker)
    brace = source.index("{", start)
    depth = 0
    for i in range(brace, len(source)):
        if source[i] == "{":
            depth += 1
        elif source[i] == "}":
            depth -= 1
            if depth == 0:
                return source[brace:i + 1]
    fail("unclosed function body: " + marker)

for p in (GRADLE, MANIFEST, PKG / "ThemeManager.kt",
          PKG / "RovexBundledVisualAssets.kt", PKG / "RovexVisualSurfaceStyle.kt",
          PKG / "RovexSurfaceSettings.kt", PKG / "RovexHomeRevolution.kt",
          PKG / "RovexDailyStudyHub.kt", PKG / "RovexBottomNavigation.kt"):
    if not p.is_file():
        fail("missing audit input: " + str(p))

gradle = GRADLE.read_text(encoding="utf-8")
if 'versionName = "8.3.660"' not in gradle or "versionCode = 746" not in gradle:
    fail("wrong Phase 665 output; expected v8.3.660 / code 746")

theme_path = PKG / "ThemeManager.kt"
theme = theme_path.read_text(encoding="utf-8")
background = function_body(theme, "fun backgroundDrawable(c:Context):Drawable")
# Root cause: bundled default art was only 34/46/58 alpha, allowing the base layer
# (including the old decorative line pattern) to bleed through on every theme.
if re.search(r"val\s+alpha\s*=\s*if\s*\(custom\s*!=\s*null\)", background):
    old = '''        val alpha = if (custom != null) {
            255
        } else {
            when (get(c)) {
                AMOLED -> 34
                LIGHT -> 46
                else -> 58
            }
        }'''
    if background.count(old) != 1:
        fail("wallpaper alpha branch changed unexpectedly; inspect before changing it")
    replacement = '''        // All six curated wallpapers are full-opacity content, not faint texture overlays.
        // Glass cards provide readability; do not wash out the lower screen by fading the art.
        val alpha = 255'''
    theme = theme.replace(old, replacement, 1)
    theme_path.write_text(theme, encoding="utf-8")
    background = function_body(theme, "fun backgroundDrawable(c:Context):Drawable")

if not re.search(r"val\s+alpha\s*=\s*255\b", background):
    fail("wallpaper must be rendered at full opacity in every theme")
if "RovexImportedMotionBackgroundDrawable" in background:
    fail("animated Lottie drawable still drives the full-screen wallpaper base")
if "RovexBundledVisualAssets.bitmapIfReady(c, get(c))" not in background:
    fail("bundled wallpaper selection path is missing")
if "RovexWallpaperManager.isEnabled(c, get(c))" not in background:
    fail("custom wallpaper precedence is missing")

loader = (PKG / "RovexBundledVisualAssets.kt").read_text(encoding="utf-8")
for theme_name, resource in RESOURCES.items():
    if resource not in loader:
        fail(f"{theme_name}: wallpaper resource mapping missing ({resource})")
    if theme_name != "light" and f'ThemeManager.{theme_name.upper()}' not in loader:
        fail(f"{theme_name}: theme selection branch missing from wallpaper loader")

try:
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
except (OSError, json.JSONDecodeError) as exc:
    fail("visual asset manifest is invalid: " + str(exc))
assets = manifest.get("assets")
if not isinstance(assets, list):
    fail("visual asset manifest has no assets list")
wallpapers = [a for a in assets if isinstance(a, dict) and a.get("type") == "wallpaper"]
by_theme = {}
for entry in wallpapers:
    by_theme.setdefault(entry.get("theme"), []).append(entry)
if set(by_theme) != set(THEMES):
    fail("wallpaper theme coverage mismatch; expected six themes, found " + repr(sorted(by_theme)))
for theme_name in THEMES:
    entries = by_theme[theme_name]
    if len(entries) != 1:
        fail(f"{theme_name}: expected exactly one default wallpaper, found {len(entries)}")
    entry = entries[0]
    rel = entry.get("file", "")
    if not rel.startswith("res/drawable-nodpi/"):
        fail(f"{theme_name}: unsafe or unexpected wallpaper resource path {rel!r}")
    image_path = APP / "src/main" / rel
    if not image_path.is_file():
        fail(f"{theme_name}: wallpaper resource file is missing: {rel}")
    data = image_path.read_bytes()
    if hashlib.sha256(data).hexdigest() != entry.get("sha256"):
        fail(f"{theme_name}: wallpaper SHA-256 differs from manifest")
    if len(data) != entry.get("bytes"):
        fail(f"{theme_name}: wallpaper byte count differs from manifest")
    if entry.get("license") != "CC0-1.0" or entry.get("modified") is not False:
        fail(f"{theme_name}: source/license/unchanged asset provenance check failed")
    if min(int(entry.get("width", 0)), int(entry.get("height", 0))) < 2160:
        fail(f"{theme_name}: source resolution is below 2160px")

style = (PKG / "RovexVisualSurfaceStyle.kt").read_text(encoding="utf-8")
if "RovexSurfaceSettings.isGlassEnabled(context)" not in style:
    fail("Glass/Normal choice is not consumed by the shared surface primitive")
if "return LayerDrawable(arrayOf(fill, tintLayer, sheen, border))" not in style:
    fail("Glass surface lost its bounded fill/tint/sheen/rim hierarchy")
if "Color.rgb(250, 252, 255)" not in style:
    fail("Normal/Solid mode no longer has an opaque light surface fallback")
for name in ("RovexHomeRevolution.kt", "RovexDailyStudyHub.kt", "RovexBottomNavigation.kt"):
    src = (PKG / name).read_text(encoding="utf-8")
    if "RovexVisualSurfaceStyle.glass" not in src:
        fail(name + ": shared Glass/Normal surface helper is not used")
settings = (PKG / "SettingsScreen.kt").read_text(encoding="utf-8")
if "Liquid Glass" not in settings or "Normal / Solid" not in settings:
    fail("visible Glass/Normal appearance controls are missing")

gradle = gradle.replace('versionName = "8.3.660"', 'versionName = "8.3.661"', 1)
gradle = gradle.replace("versionCode = 746", "versionCode = 747", 1)
if 'versionName = "8.3.661"' not in gradle or "versionCode = 747" not in gradle:
    fail("version bump failed; expected v8.3.661 / code 747")
GRADLE.write_text(gradle, encoding="utf-8")
print("[666] PASS: six-theme wallpaper bytes/provenance/mapping; full-opacity rendering; static base; shared Glass/Normal controls; v8.3.661 / 747")
