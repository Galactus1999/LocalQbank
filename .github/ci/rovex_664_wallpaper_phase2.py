#!/usr/bin/env python3
"""Phase 664: integrate the pinned, unchanged CC0 six-theme wallpaper catalogue."""
from __future__ import annotations

import hashlib
import json
import re
import sys
import urllib.request
from pathlib import Path

PROJECT = Path(sys.argv[1]).resolve()
PKG = PROJECT / "app/src/main/java/com/localqbank/library"
GRADLE = PROJECT / "app/build.gradle.kts"
DRAWABLE = PROJECT / "app/src/main/res/drawable-nodpi"
MANIFEST = PROJECT / "app/src/main/assets/rovex/RovexVisualAssetManifest.json"
UPSTREAM_COMMIT = "98ec8591de48f4bc3019133f9a79c9ff2f3c8fba"
RETRIEVED = "2026-10-11"
THEMES = ("light", "pastel", "mint", "sunset", "lavender", "amoled")

WALLPAPERS = [
    {"theme":"light","resource":"rovex_wallpaper_light","file":"abstract-spiral.jpg","gitBlobSha":"63adcf52c8809143512dd211553163605c348c1e","label":"Luminous Abstract","focal":[0.50,0.50]},
    {"theme":"pastel","resource":"rovex_wallpaper_pastel","file":"blue-periwinkle.jpg","gitBlobSha":"a22195a5dd5e8fda0b021a4b8285c812d4c375bb","label":"Pastel Prism","focal":[0.50,0.50]},
    {"theme":"mint","resource":"rovex_wallpaper_mint","file":"tea-gardens.jpg","gitBlobSha":"d34ba3f6a085e7e6f10a258aed0468a9f8fcc189","label":"Verdant Garden","focal":[0.50,0.50]},
    {"theme":"sunset","resource":"rovex_wallpaper_sunset","file":"beacon-street-sunset.jpg","gitBlobSha":"b3d34082e830bbd5abb9eace4dd7b14c28468505","label":"Amber Skyline","focal":[0.50,0.50]},
    {"theme":"lavender","resource":"rovex_wallpaper_lavender","file":"saturnian-profile.jpg","gitBlobSha":"e34bfe96702dee1ea0a632184c0860bdcb2fa1b7","label":"Violet Orbit","focal":[0.50,0.50]},
    {"theme":"amoled","resource":"rovex_wallpaper_amoled","file":"valley-midnight.jpg","gitBlobSha":"e6000ea817b869d6285bfd1a9afcee2482ce0d30","label":"Midnight Valley","focal":[0.50,0.50]},
]

def fail(message: str) -> None:
    raise SystemExit("[664] FATAL: " + message)

def jpeg_dimensions(data: bytes):
    if not data.startswith(b"\xff\xd8"):
        return None
    i = 2
    sof = {0xC0,0xC1,0xC2,0xC3,0xC5,0xC6,0xC7,0xC9,0xCA,0xCB,0xCD,0xCE,0xCF}
    while i + 4 <= len(data):
        if data[i] != 0xFF:
            i += 1
            continue
        while i < len(data) and data[i] == 0xFF:
            i += 1
        if i >= len(data):
            break
        marker = data[i]
        i += 1
        if marker in (0xD8,0xD9,0x01) or 0xD0 <= marker <= 0xD7:
            continue
        if i + 2 > len(data):
            break
        length = int.from_bytes(data[i:i+2], "big")
        if length < 2 or i + length > len(data):
            break
        if marker in sof and length >= 7:
            return int.from_bytes(data[i+5:i+7], "big"), int.from_bytes(data[i+3:i+5], "big")
        i += length
    return None

if not PROJECT.is_dir() or not GRADLE.is_file():
    fail("selected Android project or app/build.gradle.kts is missing")
gradle = GRADLE.read_text(encoding="utf-8")
if 'versionName = "8.3.658"' not in gradle or "versionCode = 744" not in gradle:
    fail("wrong baseline; expected green source baseline v8.3.658 / versionCode 744")
if not MANIFEST.is_file():
    fail("existing Rovex visual asset manifest is required; refusing to overwrite an unknown asset setup")
try:
    old_manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
except (OSError, json.JSONDecodeError) as exc:
    fail("existing asset manifest is invalid: " + str(exc))
old_assets = old_manifest.get("assets")
if not isinstance(old_assets, list):
    fail("existing manifest has no assets array")
motion_assets = [entry for entry in old_assets if isinstance(entry, dict) and "lottie" in str(entry.get("type","")).lower()]
if not motion_assets:
    fail("existing motion assets were not found; refusing to replace the manifest")
for entry in motion_assets:
    rel = entry.get("file", "")
    if not rel.startswith("assets/"):
        fail("unexpected motion asset path: " + str(rel))
    source_path = PROJECT / "app/src/main" / rel
    if not source_path.is_file():
        fail("motion asset listed in manifest is missing: " + rel)
    actual = hashlib.sha256(source_path.read_bytes()).hexdigest()
    if actual != entry.get("sha256"):
        fail("motion asset hash mismatch before wallpaper integration: " + rel)

DRAWABLE.mkdir(parents=True, exist_ok=True)
# Remove the legacy single-theme wallpaper only after verifying it is the exact manifest-tracked file.
legacy = DRAWABLE / "rovex_cc0_abstract_spiral.jpg"
if legacy.exists():
    legacy_entry = next((entry for entry in old_assets if isinstance(entry, dict) and entry.get("file") == "res/drawable-nodpi/rovex_cc0_abstract_spiral.jpg"), None)
    if legacy_entry is None or hashlib.sha256(legacy.read_bytes()).hexdigest() != legacy_entry.get("sha256"):
        fail("legacy wallpaper exists but is not the exact manifest-tracked asset; refusing to delete unknown bytes")
    legacy.unlink()
wallpaper_entries = []
for item in WALLPAPERS:
    source_url = "https://raw.githubusercontent.com/BuddiesOfBudgie/budgie-backgrounds/" + UPSTREAM_COMMIT + "/backgrounds/" + item["file"]
    try:
        request = urllib.request.Request(source_url, headers={"User-Agent":"Rovex-Phase2-Asset-Pipeline/1.0"})
        with urllib.request.urlopen(request, timeout=45) as response:
            data = response.read()
    except Exception as exc:
        fail("unable to fetch pinned CC0 asset " + item["file"] + ": " + str(exc))
    if len(data) < 50_000 or not data.startswith(b"\xff\xd8\xff"):
        fail(item["file"] + " is not a valid full-size JPEG")
    git_blob = hashlib.sha1(b"blob " + str(len(data)).encode("ascii") + b"\0" + data).hexdigest()
    if git_blob != item["gitBlobSha"]:
        fail(item["file"] + " differs from pinned upstream blob: " + git_blob)
    dimensions = jpeg_dimensions(data)
    if not dimensions or min(dimensions) < 2160 or max(dimensions) < 3840:
        fail(item["file"] + " must be at least 3840x2160; got " + repr(dimensions))
    path = DRAWABLE / (item["resource"] + ".jpg")
    path.write_bytes(data)
    wallpaper_entries.append({
        "file":"res/drawable-nodpi/" + item["resource"] + ".jpg",
        "type":"wallpaper",
        "theme":item["theme"],
        "label":item["label"],
        "source":source_url,
        "upstreamCommit":UPSTREAM_COMMIT,
        "gitBlobSha":git_blob,
        "license":"CC0-1.0",
        "retrievedAtUtc":RETRIEVED,
        "width":dimensions[0],
        "height":dimensions[1],
        "bytes":len(data),
        "sha256":hashlib.sha256(data).hexdigest(),
        "modified":False,
        "focalPoint":{"x":item["focal"][0],"y":item["focal"][1]},
    })
    print("[664] imported " + item["theme"] + " " + str(dimensions[0]) + "x" + str(dimensions[1]) + " bytes=" + str(len(data)))

# Version bump is applied only after every upstream asset passed integrity and resolution checks.
gradle = gradle.replace('versionName = "8.3.658"', 'versionName = "8.3.659"', 1)
gradle = gradle.replace("versionCode = 744", "versionCode = 745", 1)
if 'versionName = "8.3.659"' not in gradle or "versionCode = 745" not in gradle:
    fail("version bump postcondition failed; refusing to package an ambiguously versioned source")
GRADLE.write_text(gradle, encoding="utf-8")

loader = PKG / "RovexBundledVisualAssets.kt"
if not loader.is_file():
    fail("RovexBundledVisualAssets.kt is missing")
loader.write_text(r'''package com.localqbank.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * Six pinned, unchanged CC0 theme wallpapers. Decode the focal crop off-main-thread so a tall
 * phone gets near-native portrait detail without retaining six full 4K bitmaps in heap memory.
 */
object RovexBundledVisualAssets {
    private val loading = ConcurrentHashMap.newKeySet<String>()
    private fun cacheBudgetKb(): Int {
        val heapKb = Runtime.getRuntime().maxMemory() / 1024L
        return (heapKb / 12L).coerceIn(8L * 1024L, 32L * 1024L).toInt()
    }
    private val cache: LruCache<String, Bitmap> by lazy {
        object : LruCache<String, Bitmap>(cacheBudgetKb()) {
            override fun sizeOf(key: String, value: Bitmap): Int =
                (value.byteCount / 1024).coerceAtLeast(1)
        }
    }

    private fun resourceFor(theme: String): Int = when (theme) {
        ThemeManager.PASTEL -> R.drawable.rovex_wallpaper_pastel
        ThemeManager.MINT -> R.drawable.rovex_wallpaper_mint
        ThemeManager.SUNSET -> R.drawable.rovex_wallpaper_sunset
        ThemeManager.LAVENDER -> R.drawable.rovex_wallpaper_lavender
        ThemeManager.AMOLED -> R.drawable.rovex_wallpaper_amoled
        else -> R.drawable.rovex_wallpaper_light
    }

    fun preload(context: Context) {
        bitmapIfReady(context, ThemeManager.get(context))
    }

    fun bitmapIfReady(context: Context, theme: String = ThemeManager.get(context)): Bitmap? {
        cache.get(theme)?.let { return it }
        val app = context.applicationContext
        if (!loading.add(theme)) return null
        PerformanceManager.submit {
            var decoded: Bitmap? = null
            try {
                val resource = resourceFor(theme)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeResource(app.resources, resource, bounds)
                if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                    val metrics = app.resources.displayMetrics
                    val targetAspect = metrics.widthPixels.toFloat() /
                        metrics.heightPixels.coerceAtLeast(1).toFloat()
                    val sourceAspect = bounds.outWidth.toFloat() / bounds.outHeight.toFloat()
                    val cropWidth: Int
                    val cropHeight: Int
                    if (sourceAspect > targetAspect) {
                        cropHeight = bounds.outHeight
                        cropWidth = (cropHeight * targetAspect).roundToInt().coerceIn(1, bounds.outWidth)
                    } else {
                        cropWidth = bounds.outWidth
                        cropHeight = (cropWidth / targetAspect).roundToInt().coerceIn(1, bounds.outHeight)
                    }
                    val focalX = bounds.outWidth / 2
                    val focalY = bounds.outHeight / 2
                    val left = (focalX - cropWidth / 2).coerceIn(0, bounds.outWidth - cropWidth)
                    val top = (focalY - cropHeight / 2).coerceIn(0, bounds.outHeight - cropHeight)
                    val region = Rect(left, top, left + cropWidth, top + cropHeight)
                    var sample = 1
                    val targetW = (metrics.widthPixels * 1.5f).toInt().coerceAtLeast(720)
                    val targetH = (metrics.heightPixels * 1.5f).toInt().coerceAtLeast(1280)
                    while (region.width() / sample > targetW ||
                        region.height() / sample > targetH ||
                        (region.width().toLong() / sample) * (region.height().toLong() / sample) * 4L >
                            cacheBudgetKb().toLong() * 1024L) {
                        sample *= 2
                    }
                    val stream = app.resources.openRawResource(resource)
                    val decoder = try {
                        BitmapRegionDecoder.newInstance(stream, false)
                            ?: error("Android could not create a wallpaper region decoder")
                    } finally {
                        stream.close()
                    }
                    try {
                        decoded = decoder.decodeRegion(
                            region,
                            BitmapFactory.Options().apply {
                                inSampleSize = sample
                                inPreferredConfig = Bitmap.Config.ARGB_8888
                            }
                        )
                    } finally {
                        decoder.recycle()
                    }
                }
                if (decoded != null) cache.put(theme, decoded!!)
            } catch (_: OutOfMemoryError) {
                // Decorative wallpaper must never take down foreground study.
                decoded?.recycle()
                decoded = null
            } catch (_: java.io.IOException) {
                decoded?.recycle()
                decoded = null
            } catch (_: RuntimeException) {
                decoded?.recycle()
                decoded = null
            } finally {
                loading.remove(theme)
            }
            Handler(Looper.getMainLooper()).post {
                if (ThemeManager.get(app) == theme && AppManagers.isReady()) {
                    AppState.changed("bundled_wallpaper_loaded:" + theme)
                } else if (ThemeManager.get(app) != theme) {
                    bitmapIfReady(app, ThemeManager.get(app))
                }
            }
        }
        return null
    }
}
''', encoding="utf-8")

theme_path = PKG / "ThemeManager.kt"
if not theme_path.is_file():
    fail("ThemeManager.kt is missing")
theme = theme_path.read_text(encoding="utf-8")
marker = "    fun backgroundDrawable(c:Context):Drawable {"
if theme.count(marker) != 1:
    fail("ThemeManager.backgroundDrawable anchor is missing or ambiguous")
start = theme.index(marker)
brace = theme.index("{", start)
depth = 0
end = None
for i in range(brace, len(theme)):
    if theme[i] == "{":
        depth += 1
    elif theme[i] == "}":
        depth -= 1
        if depth == 0:
            end = i + 1
            break
if end is None:
    fail("could not find end of ThemeManager.backgroundDrawable")
replacement = '''    fun backgroundDrawable(c:Context):Drawable {
        val base: Drawable = if (RovexLiveMotionSettings.enabled(c) && AnimationPolicy.enabled(c)) {
            RovexImportedMotionBackgroundDrawable(c, profile(c))
        } else {
            GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(profile(c).backgroundA, profile(c).backgroundB))
        }
        // A selected user wallpaper always wins. Bundled art is only a default when none is active.
        val customSelected = RovexWallpaperManager.isEnabled(c, get(c))
        val custom = if (customSelected) RovexWallpaperManager.bitmap(c, get(c)) else null
        // Do not flash a bundled default while the user's selected wallpaper is loading.
        val bundled = if (!customSelected) RovexBundledVisualAssets.bitmapIfReady(c, get(c)) else null
        val bitmap = custom ?: bundled ?: return base
        val alpha = if (custom != null) {
            if (isDark(c)) 76 else 26
        } else {
            when (get(c)) {
                AMOLED -> 34
                LIGHT -> 46
                else -> 58
            }
        }
        return LayerDrawable(arrayOf(base, RovexWallpaperDrawable(bitmap, alpha)))
    }'''
theme = theme[:start] + replacement + theme[end:]
theme_path.write_text(theme, encoding="utf-8")

drawable_path = PKG / "RovexWallpaperDrawable.kt"
drawable_path.write_text(r'''package com.localqbank.library

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import kotlin.math.max

/** Aspect-ratio-preserving center crop with explicit bounds clipping; no extra bitmap allocation. */
class RovexWallpaperDrawable(private val bitmap: Bitmap, alphaValue: Int) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply {
        alpha = alphaValue.coerceIn(0, 255)
    }
    private val destination = RectF()

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty || bitmap.isRecycled) return
        val scale = max(b.width().toFloat() / bitmap.width, b.height().toFloat() / bitmap.height)
        val width = bitmap.width * scale
        val height = bitmap.height * scale
        val left = b.left + (b.width() - width) / 2f
        val top = b.top + (b.height() - height) / 2f
        destination.set(left, top, left + width, top + height)
        val save = canvas.save()
        canvas.clipRect(b)
        canvas.drawBitmap(bitmap, null, destination, paint)
        canvas.restoreToCount(save)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha.coerceIn(0, 255)
        invalidateSelf()
    }
    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
''', encoding="utf-8")

manifest = {
    "schema": 2,
    "generatedBy": "Rovex Phase 2 curated wallpaper integration overlay 664",
    "qualityPolicy": {
        "minimumSourceWidth": 3840,
        "minimumSourceHeight": 2160,
        "licensePolicy": "CC0-1.0",
        "sourcePinned": True,
        "verifySha256AtBuild": True,
        "decodePolicy": "active theme only, background decode, sampled display derivative, 8-32 MiB LRU",
        "visualReviewRequired": True
    },
    "assets": motion_assets + wallpaper_entries
}
MANIFEST.parent.mkdir(parents=True, exist_ok=True)
MANIFEST.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")

# Verify the runtime mapping and the generated catalogue before the next CI stage.
if set(entry["theme"] for entry in wallpaper_entries) != set(THEMES):
    fail("wallpaper theme set is incomplete or duplicated")
if "RovexWallpaperManager.isEnabled(c, get(c))" not in theme:
    fail("custom wallpaper precedence is not enforced in ThemeManager")
for item in WALLPAPERS:
    if item["resource"] not in loader.read_text(encoding="utf-8"):
        fail("theme/runtime resource mapping missing: " + item["resource"])
print("[664] PASS: v8.3.659 / 745; six pinned CC0 themes, bounded async decode/cache, custom wallpaper precedence")
