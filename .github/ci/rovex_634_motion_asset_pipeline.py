#!/usr/bin/env python3
from pathlib import Path
import hashlib
import json
import re
import sys
import urllib.request

ROOT = Path(sys.argv[2] if len(sys.argv) > 2 else ".").resolve()
APP = Path(sys.argv[1]).resolve()
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

if not APP.is_dir():
    fail(f"Android project missing: {APP}")
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

# CC0/Public-Domain wallpaper imported from the Budgie Backgrounds project.
# The upstream repository explicitly states its backgrounds are CC0 and manually reviewed.
wallpaper_url = "https://raw.githubusercontent.com/BuddiesOfBudgie/budgie-backgrounds/main/backgrounds/abstract-spiral.jpg"
wallpaper_path = DRAWABLE / "rovex_cc0_abstract_spiral.jpg"
try:
    with urllib.request.urlopen(wallpaper_url, timeout=45) as response:
        data = response.read()
except Exception as exc:
    fail(f"Could not import CC0 wallpaper: {exc}")
if len(data) < 50_000:
    fail("Downloaded wallpaper is unexpectedly small; refusing to ship a bad/error response.")
if not data.startswith(b"\xff\xd8\xff"):
    fail("Downloaded wallpaper is not a JPEG.")
wallpaper_path.write_bytes(data)

license_manifest = {
    "schema": 1,
    "generatedBy": "Rovex 8.3.634 motion asset pipeline",
    "assets": [
        {
            "file": "assets/rovex/motion/theme_transition.json",
            "type": "lottie-json",
            "source": source_url,
            "license": source_license,
            "sha256": hashlib.sha256(motion).hexdigest(),
            "modified": False
        },
        {
            "file": "res/drawable-nodpi/rovex_cc0_abstract_spiral.jpg",
            "type": "wallpaper",
            "source": wallpaper_url,
            "license": "CC0-1.0",
            "sha256": hashlib.sha256(data).hexdigest(),
            "modified": False
        }
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
import java.util.concurrent.atomic.AtomicBoolean

/** Loads only verified, bundled third-party visual assets; never generates artwork. */
object RovexBundledVisualAssets {
    private const val MAX_EDGE = 1440
    @Volatile private var cachedWallpaper: Bitmap? = null
    private val loading = AtomicBoolean(false)

    fun preload(context: Context) {
        val app = context.applicationContext
        if (cachedWallpaper != null || !loading.compareAndSet(false, true)) return
        PerformanceManager.submit {
            val bitmap = decodeBounded(app)
            if (bitmap != null) cachedWallpaper = bitmap
            loading.set(false)
            if (bitmap != null) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    if (AppManagers.isReady()) AppState.changed("bundled_visual_asset_loaded")
                }
            }
        }
    }

    fun bitmapIfReady(context: Context): Bitmap? = cachedWallpaper

    private fun decodeBounded(context: Context): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(context.resources, R.drawable.rovex_cc0_abstract_spiral, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        var sample = 1
        while (bounds.outWidth / sample > MAX_EDGE * 2 || bounds.outHeight / sample > MAX_EDGE * 2) sample *= 2
        BitmapFactory.decodeResource(
            context.resources,
            R.drawable.rovex_cc0_abstract_spiral,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        )
    }.getOrNull()
}
''')

theme = PKG / "ThemeManager.kt"
ts = theme.read_text()
old = '''    fun backgroundDrawable(c:Context):Drawable {
        val base = RovexLivingBackgroundDrawable(c, profile(c))
        val bitmap = RovexWallpaperManager.bitmap(c) ?: return base
        val wallpaper = BitmapDrawable(c.resources, bitmap).apply { gravity=android.view.Gravity.FILL; alpha=if(isDark(c))70 else 10 }
        return LayerDrawable(arrayOf(base, wallpaper))
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
