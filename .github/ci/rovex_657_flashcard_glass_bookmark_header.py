#!/usr/bin/env python3
"""Phase 657: compact bookmark collection header and thin translucent flashcard rating dock."""
from pathlib import Path
import sys

P = Path(sys.argv[1]).resolve()
gpath = P / "app/build.gradle.kts"
flash = P / "app/src/main/java/com/localqbank/library/FlashcardStudyActivity.kt"
collection = P / "app/src/main/java/com/localqbank/library/CollectionActivity.kt"
home = P / "app/src/main/java/com/localqbank/library/RovexHomeRevolution.kt"
for f in (gpath, flash, collection, home):
    if not f.is_file(): raise SystemExit(f"[657] required file missing: {f}")
g = gpath.read_text(encoding="utf-8")
if 'versionName = "8.3.654"' not in g or "versionCode = 740" not in g:
    raise SystemExit("[657] expected v8.3.654 / versionCode 740 baseline")

# Flashcard review dock: preserve rating actions/interval labels but remove the heavy,
# opaque colored slab. Use a translucent glass tray and low-alpha tinted controls.
s = flash.read_text(encoding="utf-8")
old = 'ratingRow=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(10),0,dp(10),dp(7));visibility=LinearLayout.GONE}'
new = '''ratingRow=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(dp(8),dp(3),dp(8),dp(4))
            background=RovexVisualSurfaceStyle.glass(this@FlashcardStudyActivity,16f,true)
            visibility=LinearLayout.GONE
        }'''
if s.count(old)==1: s=s.replace(old,new,1)
elif "RovexVisualSurfaceStyle.glass(this@FlashcardStudyActivity,16f,true)" not in s:
    raise SystemExit("[657] flashcard rating dock anchor missing")
old = 'val fills=intArrayOf(Color.rgb(178,86,86),Color.rgb(166,130,64),Color.rgb(55,119,135),Color.rgb(108,88,153))'
new = '''val fills=intArrayOf(
            Color.argb(112,178,86,86), Color.argb(112,166,130,64),
            Color.argb(112,55,119,135), Color.argb(112,108,88,153)
        )'''
if s.count(old)==1: s=s.replace(old,new,1)
elif "Color.argb(112,178,86,86)" not in s:
    raise SystemExit("[657] rating button fill anchor missing")
old = 'ratingButtons.addView(r,LinearLayout.LayoutParams(0,dp(42),1f).apply{if(i>0)setMargins(dp(4),0,0,0)})'
new = 'ratingButtons.addView(r,LinearLayout.LayoutParams(0,dp(36),1f).apply{if(i>0)setMargins(dp(4),0,0,0)})'
if s.count(old)==1: s=s.replace(old,new,1)
elif 'ratingButtons.addView(r,LinearLayout.LayoutParams(0,dp(36),1f)' not in s:
    raise SystemExit("[657] rating button height anchor missing")
old = 'ratingRow.addView(ratingButtons,LinearLayout.LayoutParams(-1,dp(43)))'
new = 'ratingRow.addView(ratingButtons,LinearLayout.LayoutParams(-1,dp(37)))'
if s.count(old)==1: s=s.replace(old,new,1)
elif 'ratingRow.addView(ratingButtons,LinearLayout.LayoutParams(-1,dp(37)))' not in s:
    raise SystemExit("[657] rating row height anchor missing")
if "RovexVisualSurfaceStyle.glass(this@FlashcardStudyActivity,16f,true)" not in s or "Color.argb(112,178,86,86)" not in s:
    raise SystemExit("[657] translucent flashcard dock postconditions missing")
flash.write_text(s,encoding="utf-8")

# Bookmark collection: the previous bar inherited the animated wallpaper as an opaque
# background and had wrap-content height coupled to the parent's measured height. Fix the
# root contract explicitly: top-aligned content, fixed compact header, transparent bar,
# list gets all remaining height. No spacer/weight is allowed above the title.
s = collection.read_text(encoding="utf-8")
s = s.replace('''        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background=ThemeManager.backgroundDrawable(this@CollectionActivity)
        }''','''        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.TOP
            setPadding(0, 0, 0, 0)
            background=ThemeManager.backgroundDrawable(this@CollectionActivity)
        }''',1)
old = 'val barRow = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=android.view.Gravity.CENTER_VERTICAL; setPadding(dp(8), dp(5), dp(8), dp(5)); background=ThemeManager.backgroundDrawable(this@CollectionActivity) }'
new = 'val barRow = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=android.view.Gravity.CENTER_VERTICAL; setPadding(dp(8), dp(4), dp(8), dp(4)); background=android.graphics.drawable.ColorDrawable(Color.TRANSPARENT) }'
if s.count(old)==1: s=s.replace(old,new,1)
elif 'background=android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)' not in s:
    raise SystemExit("[657] collection header background anchor missing")
old = 'barRow.addView(bar, LinearLayout.LayoutParams(0,-1,1f)); root.addView(barRow)'
new = '''barRow.addView(bar, LinearLayout.LayoutParams(0,dp(42),1f))
        root.addView(barRow, LinearLayout.LayoutParams(-1,dp(52)))'''
if s.count(old)==1: s=s.replace(old,new,1)
elif 'root.addView(barRow, LinearLayout.LayoutParams(-1,dp(52)))' not in s:
    raise SystemExit("[657] collection header height anchor missing")
old = 'background=ThemeManager.backgroundDrawable(this@CollectionActivity)\n        }\n\n        barRow.addView'
new = 'background=android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)\n        }\n\n        barRow.addView'
# Keep title text untouched; only enforce its own background transparent.
if 'background=ThemeManager.backgroundDrawable(this@CollectionActivity)\n        }\n\n        barRow.addView' in s:
    s=s.replace('background=ThemeManager.backgroundDrawable(this@CollectionActivity)\n        }\n\n        barRow.addView','background=android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)\n        }\n\n        barRow.addView',1)
if 'root.addView(barRow, LinearLayout.LayoutParams(-1,dp(52)))' not in s or 'gravity = android.view.Gravity.TOP' not in s:
    raise SystemExit("[657] compact top-aligned collection header postconditions missing")
collection.write_text(s,encoding="utf-8")

# Guard that Home keeps the shared all-theme glass card implementation from Phase 656.
hs = home.read_text(encoding="utf-8")
if 'RovexVisualSurfaceStyle.glass(c,22f,true)' not in hs or 'return android.graphics.drawable.LayerDrawable(arrayOf(glass,colorRefraction,sheen,rim))' not in hs:
    raise SystemExit("[657] shared all-theme Home glass surface regressed")
gpath.write_text(g.replace('versionName = "8.3.654"','versionName = "8.3.655"',1).replace("versionCode = 740","versionCode = 741",1),encoding="utf-8")
print("[657] Flashcard rating dock is thin translucent glass; rating actions and intervals preserved")
print("[657] bookmark collection root explicitly top-aligned; fixed 52dp header; list owns remaining height")
print("[657] Home all-theme glass implementation regression guard passed")
print("[657] applied v8.3.655 / versionCode 741")


# Phase 658: theme-scoped original wallpaper imports, bounded display previews and lean glass cards.
g = gpath.read_text(encoding="utf-8")
if 'versionName = "8.3.655"' not in g or "versionCode = 741" not in g:
    raise SystemExit("[658] expected v8.3.655 / versionCode 741 baseline")
wall_path = P / "app/src/main/java/com/localqbank/library/RovexWallpaperManager.kt"
wallpaper_drawable_path = P / "app/src/main/java/com/localqbank/library/RovexWallpaperDrawable.kt"
theme_path = P / "app/src/main/java/com/localqbank/library/ThemeManager.kt"
activity_path = P / "app/src/main/java/com/localqbank/library/SettingsActivity.kt"
screen_path = P / "app/src/main/java/com/localqbank/library/SettingsScreen.kt"
home_path = P / "app/src/main/java/com/localqbank/library/RovexHomeRevolution.kt"
for f in (wall_path, theme_path, activity_path, screen_path, home_path):
    if not f.is_file(): raise SystemExit("[658] required file missing: " + str(f))

wallpaper_drawable = r'''package com.localqbank.library

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import kotlin.math.max

/** Center-crop a bounded wallpaper preview without a second bitmap or GPU blur. */
class RovexWallpaperDrawable(private val bitmap: Bitmap, alpha: Int) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply { this.alpha = alpha.coerceIn(0, 255) }
    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty || bitmap.isRecycled) return
        val scale = max(b.width().toFloat() / bitmap.width, b.height().toFloat() / bitmap.height)
        val w = bitmap.width * scale
        val h = bitmap.height * scale
        val cx = b.exactCenterX()
        val cy = b.exactCenterY()
        canvas.drawBitmap(bitmap, null, RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f), paint)
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha.coerceIn(0, 255); invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
'''
wallpaper_drawable_path.write_text(wallpaper_drawable, encoding="utf-8")

wall = r'''package com.localqbank.library
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

/** Theme-scoped wallpaper store. Preserve original bytes; decode only bounded previews. */
object RovexWallpaperManager {
    private const val PREFS = "rovex_wallpaper"
    private const val MAX_IMPORT_BYTES = 100L * 1024L * 1024L
    private const val MAX_SOURCE_EDGE = 16384
    private const val PREVIEW_EDGE = 2048
    private const val ORIGINAL_SUFFIX = ".original"
    private const val PREVIEW_SUFFIX = ".preview.jpg"
    @Volatile private var cachedTheme: String? = null
    @Volatile private var cachedBitmap: Bitmap? = null
    private val loadingThemes = ConcurrentHashMap.newKeySet<String>()
    private fun app(c: Context) = c.applicationContext
    private fun slug(t: String) = t.lowercase().replace(Regex("[^a-z0-9_-]"), "_")
    private fun key(t: String) = "enabled_" + slug(t)
    private fun original(c: Context, t: String) = File(app(c).filesDir, "rovex_wallpaper_" + slug(t) + ORIGINAL_SUFFIX)
    private fun preview(c: Context, t: String) = File(app(c).filesDir, "rovex_wallpaper_" + slug(t) + PREVIEW_SUFFIX)

    fun isEnabled(context: Context, theme: String = ThemeManager.get(context)): Boolean {
        val c = app(context)
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(key(theme), false) &&
            original(c, theme).isFile && preview(c, theme).isFile
    }

    /** Cache-only on caller thread; no file IO or decode from backgroundDrawable. */
    fun bitmap(context: Context, theme: String = ThemeManager.get(context)): Bitmap? {
        if (!isEnabled(context, theme)) return null
        if (cachedTheme == theme) return cachedBitmap
        preloadTheme(context, theme)
        return null
    }

    fun preload(context: Context) = preloadTheme(context, ThemeManager.get(context))
    private fun preloadTheme(context: Context, theme: String) {
        val c = app(context)
        if (!isEnabled(c, theme) || (cachedTheme == theme && cachedBitmap != null) || loadingThemes.contains(theme) || !loadingThemes.add("decode:active")) return
        loadingThemes.add(theme)
        PerformanceManager.submit {
            val decoded = runCatching {
                val f = preview(c, theme)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(f.absolutePath, bounds)
                require(bounds.outWidth > 0 && bounds.outHeight > 0)
                var sample = 1
                while (bounds.outWidth / sample > PREVIEW_EDGE || bounds.outHeight / sample > PREVIEW_EDGE) sample *= 2
                BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                })
            }.getOrNull()
            synchronized(this) {
                if (decoded != null && ThemeManager.get(c) == theme) {
                    cachedBitmap = decoded
                    cachedTheme = theme
                }
            }
            loadingThemes.remove(theme)
            loadingThemes.remove("decode:active")
            if (ThemeManager.get(c) != theme) preloadTheme(c, ThemeManager.get(c))
            if (decoded != null && AppManagers.isReady()) android.os.Handler(android.os.Looper.getMainLooper()).post {
                if (ThemeManager.get(c) == theme) AppState.changed("wallpaper_loaded:" + theme)
            }
        }
    }

    /** Copy original bytes unchanged; generate a <=2048px JPEG preview off the UI thread. */
    fun setFromUri(context: Context, uri: Uri, theme: String = ThemeManager.get(context), onComplete: (Boolean) -> Unit) {
        val c = app(context)
        val safe = slug(theme)
        if (!loadingThemes.add("import:active")) { onComplete(false); return }
        loadingThemes.add("import:" + safe)
        PerformanceManager.submit {
            val ok = runCatching {
                val sourceTmp = File(c.filesDir, "rovex_wallpaper_" + safe + ORIGINAL_SUFFIX + ".tmp")
                var bytes = 0L
                c.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(sourceTmp).use { out ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            bytes += n
                            require(bytes <= MAX_IMPORT_BYTES) { "Wallpaper exceeds 100 MB limit" }
                            out.write(buffer, 0, n)
                        }
                        out.fd.sync()
                    }
                } ?: error("Unable to read selected image")
                require(bytes > 0)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(sourceTmp.absolutePath, bounds)
                require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outMimeType?.startsWith("image/") == true)
                require(bounds.outWidth <= MAX_SOURCE_EDGE && bounds.outHeight <= MAX_SOURCE_EDGE)
                var sample = 1
                while (bounds.outWidth / sample > PREVIEW_EDGE || bounds.outHeight / sample > PREVIEW_EDGE) sample *= 2
                val decoded = BitmapFactory.decodeFile(sourceTmp.absolutePath, BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }) ?: error("Unsupported image")
                val previewTmp = File(c.filesDir, "rovex_wallpaper_" + safe + PREVIEW_SUFFIX + ".tmp")
                try {
                    FileOutputStream(previewTmp).use { out ->
                        check(decoded.compress(Bitmap.CompressFormat.JPEG, 90, out))
                        out.fd.sync()
                    }
                } finally { decoded.recycle() }
                val orig = original(c, theme); val prev = preview(c, theme)
                val ob = File(c.filesDir, "rovex_wallpaper_" + safe + ORIGINAL_SUFFIX + ".bak")
                val pb = File(c.filesDir, "rovex_wallpaper_" + safe + PREVIEW_SUFFIX + ".bak")
                ob.delete(); pb.delete()
                val hadO = orig.exists(); val hadP = prev.exists()
                if (hadO) check(orig.renameTo(ob))
                if (hadP) check(prev.renameTo(pb)) { if (hadO) ob.renameTo(orig); "Cannot stage old preview" }
                try {
                    check(previewTmp.renameTo(prev))
                    check(sourceTmp.renameTo(orig))
                    check(c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(key(theme), true).commit())
                    ob.delete(); pb.delete()
                } catch (failure: Throwable) {
                    prev.delete(); orig.delete()
                    if (hadO) ob.renameTo(orig)
                    if (hadP) pb.renameTo(prev)
                    throw failure
                }
                synchronized(this) { if (cachedTheme == theme) { cachedBitmap = null; cachedTheme = null } }
                true
            }.getOrDefault(false)
            loadingThemes.remove("import:" + safe)
            loadingThemes.remove("import:active")
            File(c.filesDir, "rovex_wallpaper_" + safe + ORIGINAL_SUFFIX + ".tmp").delete()
            File(c.filesDir, "rovex_wallpaper_" + safe + PREVIEW_SUFFIX + ".tmp").delete()
            if (ok) preloadTheme(c, theme)
            android.os.Handler(android.os.Looper.getMainLooper()).post { onComplete(ok) }
        }
    }

    fun clear(context: Context, theme: String = ThemeManager.get(context)) {
        val c = app(context)
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(key(theme), false).apply()
        original(c, theme).delete(); preview(c, theme).delete()
        synchronized(this) { if (cachedTheme == theme) { cachedBitmap = null; cachedTheme = null } }
    }
    fun originalBytes(context: Context, theme: String = ThemeManager.get(context)): Long =
        original(context, theme).takeIf { it.isFile }?.length() ?: 0L
}
'''
wall_path = P / "app/src/main/java/com/localqbank/library/RovexWallpaperManager.kt"
wall_path.write_text(wall, encoding="utf-8")

theme_path = P / "app/src/main/java/com/localqbank/library/ThemeManager.kt"
ts = theme_path.read_text(encoding="utf-8")
old = '''        val custom = RovexWallpaperManager.bitmap(c)
        val bundled = if (custom == null && get(c) == PASTEL) RovexBundledVisualAssets.bitmapIfReady(c) else null
        val bitmap = custom ?: bundled ?: return base
        val alpha = if (custom != null) { if (isDark(c)) 70 else 12 } else 36
        val wallpaper = BitmapDrawable(c.resources, bitmap).apply {
            gravity=android.view.Gravity.FILL
            this.alpha=alpha
        }
        return LayerDrawable(arrayOf(base, wallpaper))'''
new = '''        val custom = RovexWallpaperManager.bitmap(c, get(c))
        val bundled = if (custom == null && get(c) == PASTEL) RovexBundledVisualAssets.bitmapIfReady(c) else null
        val bitmap = custom ?: bundled ?: return base
        val alpha = if (custom != null) { if (isDark(c)) 76 else 26 } else 30
        val wallpaper = RovexWallpaperDrawable(bitmap, alpha)
        return LayerDrawable(arrayOf(base, wallpaper))'''
if ts.count(old) != 1: raise SystemExit("[658] ThemeManager wallpaper anchor mismatch")
theme_path.write_text(ts.replace(old, new, 1), encoding="utf-8")

activity_path = P / "app/src/main/java/com/localqbank/library/SettingsActivity.kt"
a = activity_path.read_text(encoding="utf-8")
old = '''    private val wallpaperPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@registerForActivityResult
        RovexWallpaperManager.setFromUri(this, uri) { ok ->
            if (ok) recreate() else android.widget.Toast.makeText(this, "Wallpaper could not be imported", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    internal fun openWallpaperPicker() { wallpaperPicker.launch("image/*") }

    internal fun clearRovexWallpaper() {
        RovexWallpaperManager.clear(this)
        recreate()
    }'''
new = '''    private var pendingWallpaperTheme: String = ThemeManager.LIGHT
    private val wallpaperPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@registerForActivityResult
        val targetTheme = pendingWallpaperTheme
        RovexWallpaperManager.setFromUri(this, uri, targetTheme) { ok ->
            if (ok) recreate() else android.widget.Toast.makeText(this, "Wallpaper import failed. Supported images up to 100 MB and 16384 px per edge.", android.widget.Toast.LENGTH_LONG).show()
        }
    }
    internal fun openWallpaperPicker(theme: String = ThemeManager.get(this)) {
        pendingWallpaperTheme = theme
        wallpaperPicker.launch("image/*")
    }
    internal fun clearRovexWallpaper(theme: String = ThemeManager.get(this)) {
        RovexWallpaperManager.clear(this, theme)
        recreate()
    }'''
if a.count(old) != 1: raise SystemExit("[658] SettingsActivity wallpaper picker anchor mismatch")
activity_path.write_text(a.replace(old, new, 1), encoding="utf-8")

screen_path = P / "app/src/main/java/com/localqbank/library/SettingsScreen.kt"
ss = screen_path.read_text(encoding="utf-8")
start = ss.index("    private fun showWallpaperDialog(){")
end = ss.index("\n    private fun showFontDialog(){", start)
dialog = '''    private fun showWallpaperDialog(){
        val dialog=Dialog(activity)
        val root=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(18),dp(20),dp(16));background=rounded(ThemeManager.dialogBg(activity),24)}
        root.addView(TextView(activity).apply{text="Theme wallpapers";textSize=22f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.text(activity))})
        root.addView(TextView(activity).apply{text="Choose a theme, then import its own full-resolution wallpaper. Rovex retains the original locally and renders a smaller preview for smooth scrolling; nothing is uploaded.";textSize=12.5f;setTextColor(ThemeManager.muted(activity));setPadding(0,dp(5),0,dp(12))})
        val themes=arrayOf(ThemeManager.LIGHT,ThemeManager.PASTEL,ThemeManager.MINT,ThemeManager.SUNSET,ThemeManager.LAVENDER,ThemeManager.AMOLED,ThemeManager.PANDORA,ThemeManager.SPACE)
        val labels=arrayOf("Light","Pastel","Mint","Sunset","Lavender","AMOLED","Pandora","Space")
        val spinner=Spinner(activity).apply{
            adapter=android.widget.ArrayAdapter(activity,android.R.layout.simple_spinner_dropdown_item,labels)
            setSelection(themes.indexOf(ThemeManager.get(activity)).coerceAtLeast(0))
        }
        root.addView(TextView(activity).apply{text="Wallpaper target";textSize=12f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.text(activity));setPadding(0,0,0,dp(4))})
        root.addView(spinner,LinearLayout.LayoutParams(-1,dp(44)))
        val status=TextView(activity).apply{textSize=11.5f;setTextColor(ThemeManager.muted(activity));setPadding(0,dp(10),0,dp(12))}
        fun refreshStatus(){
            val i=spinner.selectedItemPosition.coerceIn(themes.indices)
            val t=themes[i]
            val enabled=RovexWallpaperManager.isEnabled(activity,t)
            val bytes=RovexWallpaperManager.originalBytes(activity,t)
            val size=if(bytes>0) "Original retained: "+String.format("%.1f",bytes/1048576.0)+" MB." else "No custom image saved for this theme."
            status.text=(if(enabled) "Custom wallpaper active for "+labels[i]+". " else "Built-in background for "+labels[i]+". ")+size
        }
        spinner.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener{
            override fun onItemSelected(parent:android.widget.AdapterView<*>?,view:View?,position:Int,id:Long){refreshStatus()}
            override fun onNothingSelected(parent:android.widget.AdapterView<*>?){}
        }
        root.addView(status)
        val buttons=LinearLayout(activity).apply{orientation=LinearLayout.HORIZONTAL}
        fun button(label:String,click:()->Unit)=TextView(activity).apply{text=label;textSize=11.5f;typeface=Typeface.DEFAULT_BOLD;gravity=Gravity.CENTER;setTextColor(ThemeManager.accent(activity));background=RovexVisualSurfaceStyle.glass(activity,14f,false);setOnClickListener{click()}}
        fun targetTheme()=themes[spinner.selectedItemPosition.coerceIn(themes.indices)]
        buttons.addView(button("IMPORT IMAGE"){val target=targetTheme();dialog.dismiss();(activity as? SettingsActivity)?.openWallpaperPicker(target)},LinearLayout.LayoutParams(0,dp(46),1f))
        buttons.addView(button("REMOVE"){val target=targetTheme();(activity as? SettingsActivity)?.clearRovexWallpaper(target);dialog.dismiss()},LinearLayout.LayoutParams(0,dp(46),1f).apply{leftMargin=dp(6)})
        root.addView(buttons)
        root.addView(TextView(activity).apply{text="CANCEL";textSize=11.5f;typeface=Typeface.DEFAULT_BOLD;gravity=Gravity.CENTER;setTextColor(ThemeManager.muted(activity));setPadding(0,dp(12),0,0);setOnClickListener{dialog.dismiss()}})
        dialog.setContentView(root);dialog.show();dialog.window?.setBackgroundDrawableResource(android.R.color.transparent);dialog.window?.setLayout((activity.resources.displayMetrics.widthPixels*.92f).toInt(),WindowManager.LayoutParams.WRAP_CONTENT)
    }
'''
ss = ss[:start] + dialog + ss[end:]
screen_path.write_text(ss, encoding="utf-8")

home_path = P / "app/src/main/java/com/localqbank/library/RovexHomeRevolution.kt"
hs = home_path.read_text(encoding="utf-8")
start = hs.index("    private fun card(c:Context,index:Int=0):android.graphics.drawable.Drawable {")
end = hs.index("\n    private fun withMotionSurface(", start)
card = '''    private fun card(c:Context,index:Int=0):android.graphics.drawable.Drawable {
        // Shared glass already supplies base/highlight/shadow; use one subtle semantic wash and fine rim.
        val accent = ThemeManager.accent(c)
        val secondary = ThemeManager.accentSecondary(c)
        val dark = ThemeManager.isDark(c)
        val radius = d(22,c).toFloat()
        val glass = RovexVisualSurfaceStyle.glass(c,22f,true)
        val washAlpha = if (dark) 20 else 18
        val colorWash = GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(
            Color.argb(washAlpha+5,Color.red(accent),Color.green(accent),Color.blue(accent)),
            Color.argb(washAlpha,Color.red(secondary),Color.green(secondary),Color.blue(secondary)),
            Color.TRANSPARENT
        )).apply { cornerRadius=radius }
        val rim = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            cornerRadius=radius
            setStroke(d(1,c).coerceAtLeast(1),Color.argb(if(dark)64 else 58,Color.red(accent),Color.green(accent),Color.blue(accent)))
        }
        return android.graphics.drawable.LayerDrawable(arrayOf(glass,colorWash,rim))
    }
'''
hs = hs[:start] + card + hs[end:]
if "return android.graphics.drawable.LayerDrawable(arrayOf(glass,colorWash,rim))" not in hs:
    raise SystemExit("[658] Home glass postcondition missing")
for marker in ("PREVIEW_EDGE = 2048", "MAX_IMPORT_BYTES = 100L * 1024L * 1024L", "PerformanceManager.submit", "cachedTheme == theme"):
    if marker not in wall: raise SystemExit("[658] wallpaper invariant missing: "+marker)
if "Color.rgb(255, 78, 164)" in hs:
    raise SystemExit("[658] fixed neon Home palette remains")
# Root-cause Home repair: these text rows are children of a VERTICAL LinearLayout, so width=0/weight=1
# applied weight to height and collapsed the two cockpit copy rows to zero width.
hs2 = home_path.read_text(encoding="utf-8")
hero_start = hs2.index('copy.addView(tv(a,"TODAY • STUDY COCKPIT"')
hero_end = hs2.index('content.addView(withMotionSurface(hero', hero_start)
hero = hs2[hero_start:hero_end]
hero_old = 'LinearLayout.LayoutParams(0,-2,1f).apply{topMargin=d(5,a)}'
if hero.count(hero_old) != 1: raise SystemExit("[658] hero title/body width-collapse anchor mismatch")
hero = hero.replace(hero_old, 'LinearLayout.LayoutParams(-1,-2).apply{topMargin=d(5,a)}', 1)
hero_old2 = 'LinearLayout.LayoutParams(0,-2,1f).apply{topMargin=d(4,a)}'
if hero.count(hero_old2) != 1: raise SystemExit("[658] hero description width-collapse anchor mismatch")
hero = hero.replace(hero_old2, 'LinearLayout.LayoutParams(-1,-2).apply{topMargin=d(4,a)}', 1)
hs2 = hs2[:hero_start] + hero + hs2[hero_end:]
if 'LinearLayout.LayoutParams(0,-2,1f).apply{topMargin=d(5,a)}' in hero or 'LinearLayout.LayoutParams(0,-2,1f).apply{topMargin=d(4,a)}' in hero:
    raise SystemExit("[658] hero copy remains zero-width")
old_graph = 'progress.addView(progressTitle);progress.addView(progressValue,LinearLayout.LayoutParams(-1,-2).apply{topMargin=d(2,a)});progress.addView(progressGraph,LinearLayout.LayoutParams(-1,d(168,a)).apply{topMargin=d(7,a)})'
new_graph = 'progress.addView(progressTitle);progress.addView(progressValue,LinearLayout.LayoutParams(-1,-2).apply{topMargin=d(2,a)});progress.addView(progressGraph,LinearLayout.LayoutParams(-1,d(120,a)).apply{topMargin=d(7,a)})'
if hs2.count(old_graph) != 1: raise SystemExit("[658] progress graph height anchor mismatch")
hs2 = hs2.replace(old_graph,new_graph,1)
home_path.write_text(hs2, encoding="utf-8")

g = g.replace('versionName = "8.3.655"', 'versionName = "8.3.656"', 1).replace("versionCode = 741", "versionCode = 742", 1)
gpath.write_text(g, encoding="utf-8")
print("[658] each theme retains its own original image; display preview bounded to 2048px")
print("[658] import validates dimensions/size, runs off UI thread and transactionally preserves prior wallpaper")
print("[658] Settings exposes independent wallpaper target for all eight themes")
print("[658] Home glass uses theme semantic colors and fewer redundant translucent layers")
print("[658] applied v8.3.656 / versionCode 742")


# Phase 659: source-verified imported Lottie, theme-token palette, no text-flow coupling on cards.
import hashlib as _hashlib, json as _json, shutil as _shutil
_src_asset = Path(__file__).resolve().parents[2] / ".github/assets/rovex-motion/gradient_animated_background.json"
_dst_asset = P / "app/src/main/assets/rovex/motion/gradient_animated_background.json"
_manifest = P / "app/src/main/assets/rovex/RovexVisualAssetManifest.json"
_home_path = P / "app/src/main/java/com/localqbank/library/RovexHomeRevolution.kt"
if not all(x.is_file() for x in (_src_asset, _dst_asset, _manifest, _home_path)):
    raise SystemExit("[659] required upstream asset, manifest, or Home source missing")
_expected_sha = "45c82306570e1ab4670c5637e80fc0232d57c6cde8ba39fb16732188d5ab1056"
_asset_bytes = _src_asset.read_bytes()
if _hashlib.sha256(_asset_bytes).hexdigest() != _expected_sha:
    raise SystemExit("[659] upstream Lottie asset hash mismatch")
_asset_json = _json.loads(_asset_bytes.decode("utf-8"))
if not all(k in _asset_json for k in ("v", "fr", "ip", "op", "w", "h", "layers")) or not _asset_json["layers"]:
    raise SystemExit("[659] upstream Lottie asset structure is invalid")
_shutil.copyfile(_src_asset, _dst_asset)

_home = _home_path.read_text(encoding="utf-8")
_old_alpha = '''            ThemeManager.LIGHT -> 0.22f
            ThemeManager.PASTEL -> 0.27f
            ThemeManager.MINT -> 0.18f
            ThemeManager.SUNSET -> 0.20f
            ThemeManager.LAVENDER -> 0.20f
            ThemeManager.AMOLED -> 0.12f
            ThemeManager.PANDORA -> 0.14f
            ThemeManager.SPACE -> 0.12f
            else -> 0.16f'''
_new_alpha = '''            ThemeManager.LIGHT -> 0.14f
            ThemeManager.PASTEL -> 0.12f // compatibility theme; not the design target
            ThemeManager.MINT -> 0.13f
            ThemeManager.SUNSET -> 0.14f
            ThemeManager.LAVENDER -> 0.13f
            ThemeManager.AMOLED -> 0.09f
            ThemeManager.PANDORA -> 0.12f
            ThemeManager.SPACE -> 0.10f
            else -> 0.12f'''
if _home.count(_old_alpha) != 1: raise SystemExit("[659] Home motion alpha anchor mismatch")
_home = _home.replace(_old_alpha, _new_alpha, 1)
_dollar = chr(36)
_old_anim = '            setAnimationFromJson(RovexMotionAssetLoader.themedJson(c), "rovex_home_gradient_' + _dollar + '{RovexColorFlowTextView.colorOne(c)}_' + _dollar + '{RovexColorFlowTextView.colorTwo(c)}")'
_new_anim = '''            // Card animation follows theme tokens; text-flow controls affect text only.
            val first = if (tagValue.startsWith(MOTION_CARD_PREFIX)) ThemeManager.accent(c) else RovexColorFlowTextView.colorOne(c)
            val second = if (tagValue.startsWith(MOTION_CARD_PREFIX)) ThemeManager.accentSecondary(c) else RovexColorFlowTextView.colorTwo(c)
            setAnimationFromJson(RovexMotionAssetLoader.themedJson(c, first, second), "rovex_home_gradient_" + ThemeManager.get(c) + "_" + first + "_" + second)'''
if _home.count(_old_anim) != 1: raise SystemExit("[659] initial Home motion palette anchor mismatch")
_home = _home.replace(_old_anim, _new_anim, 1)
_old_palette = '''            val first = RovexColorFlowTextView.colorOne(c)
            val second = RovexColorFlowTextView.colorTwo(c)
            val paletteKey = "$first:$second"
            if (view.getTag(R.id.rovexMotionPaletteKey) != paletteKey) {
                view.setTag(R.id.rovexMotionPaletteKey, paletteKey)
                view.setAnimationFromJson(RovexMotionAssetLoader.themedJson(c, first, second), "rovex_home_gradient_''' + _dollar + '''{first}_''' + _dollar + '''{second}")
            }'''
_new_palette = '''            val cardSurface = tag.startsWith(MOTION_CARD_PREFIX)
            val first = if (cardSurface) ThemeManager.accent(c) else RovexColorFlowTextView.colorOne(c)
            val second = if (cardSurface) ThemeManager.accentSecondary(c) else RovexColorFlowTextView.colorTwo(c)
            val paletteKey = ThemeManager.get(c) + ":" + first + ":" + second + ":" + (if (cardSurface) "theme-card" else "flow")
            if (view.getTag(R.id.rovexMotionPaletteKey) != paletteKey) {
                view.setTag(R.id.rovexMotionPaletteKey, paletteKey)
                view.setAnimationFromJson(RovexMotionAssetLoader.themedJson(c, first, second), "rovex_home_gradient_" + ThemeManager.get(c) + "_" + first + "_" + second)
            }'''
if _home.count(_old_palette) != 1: raise SystemExit("[659] Home motion theme-switch palette anchor mismatch")
_home = _home.replace(_old_palette, _new_palette, 1)
_feature_start = _home.index("    private fun feature(a:MainActivity,title:String,sub:String,icon:String,index:Int,target:()->Unit):View{")
_feature_end = _home.index("\n    private fun openSection(", _feature_start)
_feature_source = _home[_feature_start:_feature_end]
if "RovexColorFlowTextView" in _feature_source: raise SystemExit("[659] Home feature card directly instantiates flowing text")
if "motionBackground(a, MOTION_CARD_PREFIX + title)" not in _feature_source: raise SystemExit("[659] imported Lottie card layer missing")
_home_path.write_text(_home, encoding="utf-8")

_manifest_obj = _json.loads(_manifest.read_text(encoding="utf-8"))
_manifest_found = False
for _item in _manifest_obj.get("assets", []):
    if _item.get("file") == "assets/rovex/motion/gradient_animated_background.json":
        _item.update({"source": "https://raw.githubusercontent.com/xvrh/lottie-flutter/master/example/assets/lottiefiles/gradient_animated_background.json",
                      "sourcePage": "https://lottiefiles.com/free-animation/gradient-animated-background-Gyh6Lr3KGK",
                      "creator": "LottieFiles community", "license": "Lottie Simple License",
                      "sha256": _expected_sha, "modified": False})
        _manifest_found = True
if not _manifest_found: raise SystemExit("[659] Lottie provenance missing from asset manifest")
_manifest.write_text(_json.dumps(_manifest_obj, indent=2) + "\n", encoding="utf-8")
_license_note = P / "app/src/main/assets/rovex/motion/THIRD_PARTY_ASSETS.md"
if not _license_note.is_file() or "Lottie Simple License" not in _license_note.read_text(encoding="utf-8"):
    raise SystemExit("[659] Lottie license notice missing")
if _hashlib.sha256(_dst_asset.read_bytes()).hexdigest() != _expected_sha:
    raise SystemExit("[659] copied Lottie asset differs from upstream")
g = gpath.read_text(encoding="utf-8")
if 'versionName = "8.3.656"' not in g or "versionCode = 742" not in g:
    raise SystemExit("[659] expected Phase 658 version 8.3.656 / 742")
g = g.replace('versionName = "8.3.656"', 'versionName = "8.3.657"', 1).replace("versionCode = 742", "versionCode = 743", 1)
gpath.write_text(g, encoding="utf-8")
print("[659] upstream Lottie asset hash and JSON schema verified; original asset copied unchanged")
print("[659] Home card palette follows theme semantic accents, independent of text-flow settings")
print("[659] reduced over-bright card motion alpha across themes; Pastel is not the design target")
print("[659] license/provenance manifest checked; applied v8.3.657 / versionCode 743")


# Final Phase 659 regression contract: text-flow preference changes must not recolour card motion;
# theme changes must. Keep a real instrumented test so this cannot regress silently.
_test = P / "app/src/androidTest/java/com/localqbank/library/RovexHomeCardMotionPaletteRegressionTest.kt"
_test.parent.mkdir(parents=True, exist_ok=True)
_test.write_text(r'''package com.localqbank.library

import android.content.Intent
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.airbnb.lottie.LottieAnimationView
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RovexHomeCardMotionPaletteRegressionTest {
    @Test
    fun cardMotionUsesThemeTokensNotTextFlowPreferences() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val oldTheme = ThemeManager.get(context)
        val prefs = context.getSharedPreferences("ui", android.content.Context.MODE_PRIVATE)
        val hadOne = prefs.contains("flow_text_color_1")
        val hadTwo = prefs.contains("flow_text_color_2")
        val oldOne = prefs.getInt("flow_text_color_1", 0)
        val oldTwo = prefs.getInt("flow_text_color_2", 0)
        try {
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                instrumentation.waitForIdleSync()
                scenario.onActivity { activity ->
                    val root = activity.findViewById<ViewGroup>(R.id.dashboardRoot)
                        ?: error("Home dashboard root missing")
                    val motions = mutableListOf<LottieAnimationView>()
                    fun walk(view: View) {
                        if (view is LottieAnimationView && view.tag?.toString()?.startsWith("rovex_home_motion_card:") == true) motions.add(view)
                        if (view is ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i))
                    }
                    walk(root)
                    check(motions.size >= 5) { "Expected at least five Home card motion layers; found " + motions.size }
                    val before = motions.map { it.getTag(R.id.rovexMotionPaletteKey)?.toString() }
                    check(before.all { !it.isNullOrBlank() }) { "Home card theme palette keys were not initialized" }
                    prefs.edit().putInt("flow_text_color_1", Color.RED).putInt("flow_text_color_2", Color.YELLOW).commit()
                    RovexHomeRevolution.refreshTheme(activity)
                    val afterFlowChange = motions.map { it.getTag(R.id.rovexMotionPaletteKey)?.toString() }
                    check(before == afterFlowChange) { "Changing text-flow colours changed Home card motion palette" }
                    check(motions.all { it.alpha in 0.08f..0.15f }) { "Home card motion opacity is too strong or too faint" }
                    val alternate = if (oldTheme == ThemeManager.SPACE) ThemeManager.LIGHT else ThemeManager.SPACE
                    ThemeManager.set(activity, alternate)
                    RovexHomeRevolution.refreshTheme(activity)
                    val afterThemeChange = motions.map { it.getTag(R.id.rovexMotionPaletteKey)?.toString() }
                    check(afterThemeChange != afterFlowChange) { "Home card motion palette did not adapt to theme changes" }
                }
            }
        } finally {
            prefs.edit().apply {
                if (hadOne) putInt("flow_text_color_1", oldOne) else remove("flow_text_color_1")
                if (hadTwo) putInt("flow_text_color_2", oldTwo) else remove("flow_text_color_2")
            }.commit()
            ThemeManager.set(context, oldTheme)
        }
    }
}
''', encoding="utf-8")
print("[659] instrumented regression test added: flow colors must not affect card motion; theme must")

_settings = P / "app/src/main/java/com/localqbank/library/SettingsScreen.kt"
if not _settings.is_file():
    raise SystemExit("[659] SettingsScreen.kt missing during wording audit")
_settings_text = _settings.read_text(encoding="utf-8")
_old_label = 'text="Flow these colours across pastel cards (keep text readable)"'
_new_label = 'text="Theme-adaptive glass card motion (independent of text colours)"'
if _settings_text.count(_old_label) == 1:
    _settings_text = _settings_text.replace(_old_label, _new_label, 1)
elif _new_label not in _settings_text:
    raise SystemExit("[659] card motion settings label anchor mismatch")
_settings.write_text(_settings_text, encoding="utf-8")
print("[659] settings terminology corrected: card motion is independent of text flow")



# Architecture hardening: bound the themed-JSON cache. A user can change text-flow colours
# repeatedly; an unbounded cache would retain every generated JSON string for the process lifetime.
_loader_path = P / "app/src/main/java/com/localqbank/library/RovexMotionAssetLoader.kt"
if not _loader_path.is_file():
    raise SystemExit("[659] RovexMotionAssetLoader.kt missing during architecture audit")
_loader = _loader_path.read_text(encoding="utf-8")
_cache_decl = "    private val themedCache = ConcurrentHashMap<String, String>()"
_cache_decl_new = _cache_decl + "\n    private const val MAX_THEMED_CACHE_ENTRIES = 8"
if _loader.count(_cache_decl) == 1 and "MAX_THEMED_CACHE_ENTRIES" not in _loader:
    _loader = _loader.replace(_cache_decl, _cache_decl_new, 1)
_cache_put = 'json.toString().also { themedCache[key] = it }'
_cache_put_new = '''json.toString().also {
                    if (themedCache.size >= MAX_THEMED_CACHE_ENTRIES) {
                        themedCache.keys.firstOrNull()?.let { stale -> themedCache.remove(stale) }
                    }
                    themedCache[key] = it
                }'''
if _loader.count(_cache_put) == 1:
    _loader = _loader.replace(_cache_put, _cache_put_new, 1)
elif "MAX_THEMED_CACHE_ENTRIES" not in _loader:
    raise SystemExit("[659] themed Lottie cache insertion anchor mismatch")
if "MAX_THEMED_CACHE_ENTRIES = 8" not in _loader or "themedCache.size >= MAX_THEMED_CACHE_ENTRIES" not in _loader:
    raise SystemExit("[659] bounded Lottie cache postcondition failed")
_loader_path.write_text(_loader, encoding="utf-8")
print("[659] architecture audit: capped recoloured Lottie JSON cache at eight entries")


# Phase 659 CI root-cause repair: unique-work migration must be atomic.
# cancelUniqueWork() is asynchronous. Cancelling and immediately enqueueing with KEEP can
# race and cancel the replacement request, leaving a durable recovery marker without live work.
_worker_path = P / "app/src/main/java/com/localqbank/library/QBankDeletionRecoveryWorker.kt"
if not _worker_path.is_file():
    raise SystemExit("[659] QBankDeletionRecoveryWorker.kt missing during CI root-cause repair")
_worker = _worker_path.read_text(encoding="utf-8")
_old_cancel = """                if (migratingAdmission) {
                    workManager.cancelUniqueWork(UNIQUE)
                }
"""
if _old_cancel in _worker:
    _worker = _worker.replace(_old_cancel, "", 1)
elif "workManager.cancelUniqueWork(UNIQUE)" in _worker:
    raise SystemExit("[659] unexpected QBank recovery cancellation structure; refusing unsafe rewrite")
_old_policy = "                    UNIQUE, ExistingWorkPolicy.KEEP, request"
_new_policy = "                    UNIQUE, if (migratingAdmission) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, request"
if _worker.count(_old_policy) == 1:
    _worker = _worker.replace(_old_policy, _new_policy, 1)
elif _new_policy not in _worker:
    raise SystemExit("[659] unique-work policy anchor mismatch in QBank recovery worker")
if "workManager.cancelUniqueWork(UNIQUE)" in _worker:
    raise SystemExit("[659] unsafe asynchronous cancel remains in recovery migration")
if _new_policy not in _worker:
    raise SystemExit("[659] atomic WorkManager migration policy postcondition failed")
_worker_path.write_text(_worker, encoding="utf-8")
print("[659] QBank recovery migration now atomically REPLACEs stale unique work; avoids cancel/enqueue race")

# TEMPORARY P0 diagnostic: expose the exact failing recovery admission assertion
# from the generated source archive in CI logs; remove after root-cause repair.
for _diag_rel in (
    "app/src/androidTest/java/com/localqbank/library/QBankDeletionRecoveryAdmissionTest.kt",
    "app/src/main/java/com/localqbank/library/QBankDeletionRecoveryWorker.kt",
):
    _diag_path = P / _diag_rel
    if _diag_path.is_file():
        _diag_lines = _diag_path.read_text(encoding="utf-8").splitlines()
        print("[P0-DIAG] " + _diag_rel)
        for _idx, _line in enumerate(_diag_lines[:100], 1):
            print(f"[P0-DIAG] {_idx}: {_line}")
    else:
        print("[P0-DIAG] MISSING " + _diag_rel)

