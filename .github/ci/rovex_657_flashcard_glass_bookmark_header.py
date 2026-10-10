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
