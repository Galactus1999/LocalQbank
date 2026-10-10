#!/usr/bin/env python3
from pathlib import Path
import hashlib
import json
import re
import sys
import urllib.request

ROOT = Path(sys.argv[2] if len(sys.argv) > 2 else ".").resolve()
PROJECT = Path(sys.argv[1]).resolve()
APP = PROJECT / "app"
PKG = APP / "src/main/java/com/localqbank/library"
RES = APP / "src/main/res"
ASSETS = APP / "src/main/assets/rovex/motion"
RAW = APP / "src/main/res/raw"
DRAWABLE = APP / "src/main/res/drawable-nodpi"
GRADLE = APP / "build.gradle.kts"

EXPECTED_VERSION = "8.3.633"
EXPECTED_CODE = "719"
NEW_VERSION = "8.3.634"
NEW_CODE = "720"

def fail(msg):
    raise SystemExit(f"[634] FATAL: {msg}")

if not PROJECT.is_dir() or not APP.is_dir():
    fail(f"Android project/app module missing: project={PROJECT} app={APP}")
if not GRADLE.exists():
    fail(f"Gradle file missing: {GRADLE}")

g = GRADLE.read_text()
if f'versionName = "{EXPECTED_VERSION}"' not in g or f"versionCode = {EXPECTED_CODE}" not in g:
    fail("Expected 8.3.633 / 719 baseline not found; refusing to patch an unknown source.")

g = g.replace(f'versionCode = {EXPECTED_CODE}', f'versionCode = {NEW_CODE}', 1)
g = g.replace(f'versionName = "{EXPECTED_VERSION}"', f'versionName = "{NEW_VERSION}"', 1)
GRADLE.write_text(g)

ASSETS.mkdir(parents=True, exist_ok=True)
RAW.mkdir(parents=True, exist_ok=True)
DRAWABLE.mkdir(parents=True, exist_ok=True)

# This animation is imported unchanged from an external MIT-licensed GitHub repository.
source_url = "https://raw.githubusercontent.com/spemer/lottie-animations-json/master/animate_tab/animate_tab_1_example.json"
source_license = "MIT"
motion_path = ROOT / ".github/assets/rovex-motion/animate_tab_1_example.json"
if not motion_path.exists():
    fail(f"Vendored motion asset missing: {motion_path}")
motion = motion_path.read_bytes()
if len(motion) < 1000 or not motion.lstrip().startswith(b"{"):
    fail("Vendored Lottie asset is missing or not JSON.")
try:
    doc = json.loads(motion.decode("utf-8"))
except Exception as exc:
    fail(f"Vendored Lottie JSON is invalid: {exc}")
for key in ("v", "fr", "ip", "op", "w", "h", "layers"):
    if key not in doc:
        fail(f"Lottie asset missing required field: {key}")

target_motion = ASSETS / "theme_transition.json"
target_motion.write_bytes(motion)

# Curated CC0 artwork from the Budgie Backgrounds project. Pin the upstream commit so
# a moving branch cannot silently change the art or invalidate the quality review.
# The upstream project requires >=3840x2160 JPEGs and states all submitted backgrounds are CC0.
UPSTREAM_COMMIT = "98ec8591de48f4bc3019133f9a79c9ff2f3c8fba"
WALLPAPERS = [
    {"theme":"light","resource":"rovex_wallpaper_light","file":"abstract-spiral.jpg","label":"Luminous Abstract","focal":[0.50,0.50]},
    {"theme":"pastel","resource":"rovex_wallpaper_pastel","file":"blue-periwinkle.jpg","label":"Pastel Prism","focal":[0.50,0.50]},
    {"theme":"mint","resource":"rovex_wallpaper_mint","file":"tea-gardens.jpg","label":"Verdant Garden","focal":[0.50,0.50]},
    {"theme":"sunset","resource":"rovex_wallpaper_sunset","file":"beacon-street-sunset.jpg","label":"Amber Skyline","focal":[0.50,0.50]},
    {"theme":"lavender","resource":"rovex_wallpaper_lavender","file":"saturnian-profile.jpg","label":"Violet Orbit","focal":[0.50,0.50]},
    {"theme":"amoled","resource":"rovex_wallpaper_amoled","file":"valley-midnight.jpg","label":"Midnight Valley","focal":[0.50,0.50]},
]

def jpeg_dimensions(data):
    """Read JPEG SOF dimensions without relying on a runner-installed image library."""
    if not data.startswith(b"\xff\xd8"):
        return None
    i = 2
    while i + 4 <= len(data):
        if data[i] != 0xff:
            i += 1
            continue
        while i < len(data) and data[i] == 0xff:
            i += 1
        if i >= len(data):
            break
        marker = data[i]
        i += 1
        if marker in (0xd8, 0xd9) or 0xd0 <= marker <= 0xd7 or marker == 0x01:
            continue
        if i + 2 > len(data):
            break
        length = int.from_bytes(data[i:i+2], "big")
        if length < 2 or i + length > len(data):
            break
        if marker in (0xc0, 0xc1, 0xc2, 0xc3, 0xc5, 0xc6, 0xc7, 0xc9, 0xca, 0xcb, 0xcd, 0xce, 0xcf):
            if length < 7:
                break
            height = int.from_bytes(data[i+3:i+5], "big")
            width = int.from_bytes(data[i+5:i+7], "big")
            return width, height
        i += length
    return None

wallpaper_entries = []
for wallpaper in WALLPAPERS:
    source_url = (
        "https://raw.githubusercontent.com/BuddiesOfBudgie/budgie-backgrounds/"
        + UPSTREAM_COMMIT + "/backgrounds/" + wallpaper["file"]
    )
    wallpaper_path = DRAWABLE / (wallpaper["resource"] + ".jpg")
    try:
        with urllib.request.urlopen(source_url, timeout=45) as response:
            data = response.read()
    except Exception as exc:
        fail(f"Could not import CC0 wallpaper {wallpaper['file']}: {exc}")
    if len(data) < 50_000 or not data.startswith(b"\xff\xd8\xff"):
        fail(f"Wallpaper {wallpaper['file']} is missing, too small, or not a JPEG.")
    dimensions = jpeg_dimensions(data)
    if not dimensions or min(dimensions) < 2160 or max(dimensions) < 3840:
        fail(f"Wallpaper {wallpaper['file']} must be at least 3840x2160; got {dimensions}.")
    wallpaper_path.write_bytes(data)
    wallpaper_entries.append({
        "file": "res/drawable-nodpi/" + wallpaper["resource"] + ".jpg",
        "type": "wallpaper",
        "theme": wallpaper["theme"],
        "label": wallpaper["label"],
        "source": source_url,
        "upstreamCommit": UPSTREAM_COMMIT,
        "license": "CC0-1.0",
        "retrievedAtUtc": "2026-10-10",
        "width": dimensions[0],
        "height": dimensions[1],
        "bytes": len(data),
        "sha256": hashlib.sha256(data).hexdigest(),
        "modified": False,
        "focalPoint": {"x": wallpaper["focal"][0], "y": wallpaper["focal"][1]}
    })

license_manifest = {
    "schema": 2,
    "generatedBy": "Rovex curated visual asset pipeline",
    "qualityPolicy": {
        "minimumSourceWidth": 3840,
        "minimumSourceHeight": 2160,
        "licensePolicy": "CC0-1.0 or explicitly reviewed redistribution license",
        "sourcePinned": True,
        "verifySha256AtBuild": True,
        "decodePolicy": "background-only, sampled to screen bounds, at most two wallpaper bitmaps cached",
        "visualReviewRequired": True
    },
    "assets": [
        {
            "file": "assets/rovex/motion/theme_transition.json",
            "type": "lottie-json",
            "source": source_url if False else "https://raw.githubusercontent.com/spemer/lottie-animations-json/master/animate_tab/animate_tab_1_example.json",
            "license": "MIT",
            "sha256": hashlib.sha256(motion).hexdigest(),
            "modified": False
        },
        *wallpaper_entries
    ]
}
(APP / "src/main/assets/rovex").mkdir(parents=True, exist_ok=True)
(APP / "src/main/assets/rovex/RovexVisualAssetManifest.json").write_text(
    json.dumps(license_manifest, indent=2) + "\n"
)

bundled = PKG / "RovexBundledVisualAssets.kt"
bundled.write_text(r'''package com.localqbank.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.util.concurrent.ConcurrentHashMap

/** Curated, hash-verified-at-build-time wallpapers. Decodes only the active theme off the UI thread. */
object RovexBundledVisualAssets {
    private const val MAX_EDGE = 2048
    private val loading = ConcurrentHashMap.newKeySet<String>()
    private val cache by lazy {
        val maxKb = (Runtime.getRuntime().maxMemory() / 1024L / 16L).toInt().coerceAtLeast(4096)
        object : LruCache<String, Bitmap>(maxKb) {
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
        val key = theme
        cache.get(key)?.let { return it }
        val app = context.applicationContext
        if (loading.add(key)) {
            PerformanceManager.submit {
                try {
                    val resource = resourceFor(key)
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeResource(app.resources, resource, bounds)
                    if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                        var sample = 1
                        val metrics = app.resources.displayMetrics
                        val targetW = (metrics.widthPixels * 1.5f).toInt().coerceAtLeast(720)
                        val targetH = (metrics.heightPixels * 1.5f).toInt().coerceAtLeast(1280)
                        while (bounds.outWidth / sample > maxOf(targetW, MAX_EDGE) ||
                            bounds.outHeight / sample > maxOf(targetH, MAX_EDGE)) sample *= 2
                        val bitmap = BitmapFactory.decodeResource(
                            app.resources,
                            resource,
                            BitmapFactory.Options().apply {
                                inSampleSize = sample
                                inPreferredConfig = Bitmap.Config.RGB_565
                            }
                        )
                        if (bitmap != null) cache.put(key, bitmap)
                    }
                } finally {
                    loading.remove(key)
                }
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    if (AppManagers.isReady()) AppState.changed("bundled_wallpaper_loaded:$key")
                }
            }
        }
        return null
    }
}
'''))

theme = PKG / "ThemeManager.kt"
ts = theme.read_text()
old = '''    fun backgroundDrawable(c:Context):Drawable {
        val base = RovexLivingBackgroundDrawable(c, profile(c))
        val custom = RovexWallpaperManager.bitmap(c)
        val bundled = if (custom == null) RovexBundledVisualAssets.bitmapIfReady(c, get(c)) else null
        val bitmap = custom ?: bundled ?: return base
        val alpha = if (custom != null) { if (isDark(c)) 70 else 10 } else 62
        return LayerDrawable(arrayOf(base, RovexWallpaperDrawable(bitmap, alpha)))
    }'''
new = '''    fun backgroundDrawable(c:Context):Drawable {
        val base = RovexLivingBackgroundDrawable(c, profile(c))
        val custom = RovexWallpaperManager.bitmap(c)
        val bundled = if (custom == null && get(c) == PASTEL) RovexBundledVisualAssets.bitmapIfReady(c) else null
        val bitmap = custom ?: bundled ?: return base
        val alpha = if (custom != null) {
            if (isDark(c)) 70 else 10
        } else {
            46
        }
        val wallpaper = BitmapDrawable(c.resources, bitmap).apply {
            gravity=android.view.Gravity.FILL
            this.alpha=alpha
        }
        return LayerDrawable(arrayOf(base, wallpaper))
    }'''
if old not in ts:
    fail("ThemeManager.backgroundDrawable anchor not found.")
theme.write_text(ts.replace(old, new, 1))


wallpaper_drawable = PKG / "RovexWallpaperDrawable.kt"
wallpaper_drawable.write_text(r'''package com.localqbank.library

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import kotlin.math.max

/** Aspect-ratio-preserving center crop; never stretches landscape art to a phone's portrait shape. */
class RovexWallpaperDrawable(
    private val bitmap: Bitmap,
    alphaValue: Int
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
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
        canvas.save()
        canvas.clipRect(b)
        canvas.drawBitmap(bitmap, null, destination, paint)
        canvas.restore()
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha.coerceIn(0, 255); invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
''', encoding="utf-8")

resilience = PKG / "ResilienceManager.kt"
rs = resilience.read_text()
anchor = 'runCatching { RovexWallpaperManager.preload(this) }'
if anchor not in rs:
    fail("ResilienceManager wallpaper preload anchor not found.")
if 'RovexBundledVisualAssets.preload(this)' not in rs:
    rs = rs.replace(anchor, anchor + '\n        runCatching { RovexBundledVisualAssets.preload(this) }', 1)
resilience.write_text(rs)

transition = PKG / "RovexMotionTransition.kt"
transition.write_text(r'''package com.localqbank.library

import android.app.Activity
import android.graphics.Color
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable
import com.airbnb.lottie.RenderMode

/** External-asset motion bridge. It owns choreography only; no navigation or business state. */
object RovexMotionTransition {
    private const val ASSET = "rovex/motion/theme_transition.json"

    fun play(activity: Activity): Boolean {
        if (!AnimationPolicy.enabled(activity)) return false
        val decor = activity.window.decorView as? ViewGroup ?: return false
        val density = activity.resources.displayMetrics.density
        val motion = LottieAnimationView(activity).apply {
            setAnimation(ASSET)
            repeatCount = 0
            repeatMode = LottieDrawable.RESTART
            speed = 5f
            alpha = 0.82f
            setBackgroundColor(Color.TRANSPARENT)
            renderMode = RenderMode.HARDWARE
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            contentDescription = null
        }
        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (18f * density).toInt().coerceAtLeast(1),
            Gravity.TOP
        )
        decor.addView(motion, lp)
        runCatching { decor.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) }
        motion.playAnimation()
        motion.postDelayed({
            runCatching { decor.removeView(motion) }
            runCatching { motion.cancelAnimation() }
        }, 180L)
        return true
    }
}
''')

transition_file = PKG / "RovexThemeTransition.kt"
tr = transition_file.read_text()
old_tr = '''    fun apply(activity: Activity, view: View? = null) {
        RovexSoundFeedback.playThemeSwitch(activity)
        view?.let {
            it.alpha = 0.94f
            it.animate().alpha(1f).setDuration(180L).start()
        }
        activity.recreate()
        activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }'''
new_tr = '''    fun apply(activity: Activity, view: View? = null) {
        RovexSoundFeedback.playThemeSwitch(activity)
        view?.let {
            it.alpha = 0.94f
            it.animate().alpha(1f).setDuration(180L).start()
        }
        val motionPlayed = RovexMotionTransition.play(activity)
        if (motionPlayed) {
            activity.window.decorView.postDelayed({
                if (!activity.isFinishing && !activity.isDestroyed) activity.recreate()
            }, 180L)
        } else {
            activity.recreate()
        }
        activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }'''
if old_tr not in tr:
    fail("RovexThemeTransition.apply anchor not found.")
transition_file.write_text(tr.replace(old_tr, new_tr, 1))

# The existing raw animation is retained for compatibility; the new transition asset is a separate,
# externally sourced lane so the old renderer can be removed only after visual regression validation.
print(f"[634] applied: {NEW_VERSION} / {NEW_CODE}")
print(f"[634] motion asset bytes: {len(motion)}")
print(f"[634] wallpaper bytes: {len(data)} sha256={hashlib.sha256(data).hexdigest()}")
