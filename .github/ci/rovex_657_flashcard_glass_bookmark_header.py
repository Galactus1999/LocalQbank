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
end = hs.index("\\n    private fun withMotionSurface(", start)
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
