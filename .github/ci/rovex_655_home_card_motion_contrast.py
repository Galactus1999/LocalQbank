#!/usr/bin/env python3
"""Phase 655: glassmorphic pop-color Home cards with vivid edge light and restrained motion."""
from pathlib import Path
import sys

project = Path(sys.argv[1]).resolve()
gradle = project / "app/build.gradle.kts"
home = project / "app/src/main/java/com/localqbank/library/RovexHomeRevolution.kt"
if not gradle.is_file() or not home.is_file():
    raise SystemExit("[655] required Gradle/Home source missing")
g = gradle.read_text(encoding="utf-8")
if 'versionName = "8.3.652"' not in g or "versionCode = 738" not in g:
    raise SystemExit("[655] expected v8.3.652 / versionCode 738 baseline")

s = home.read_text(encoding="utf-8")
old_alpha = "            ThemeManager.PASTEL -> 0.20f"
new_alpha = "            ThemeManager.PASTEL -> 0.27f"
if s.count(old_alpha) == 1:
    s = s.replace(old_alpha, new_alpha, 1)
elif s.count(new_alpha) != 1:
    raise SystemExit("[655] Pastel animation alpha anchor mismatch")

old_card = """        val base=if(ThemeManager.isDark(c)) {
            when(index%4){0->ThemeManager.elevated(c);1->ThemeManager.panel(c);2->ThemeManager.elevated(c);else->ThemeManager.panel(c)}
        } else ThemeManager.pastelAccentFill(c,index)
        val end=if(ThemeManager.isDark(c)) ThemeManager.panel(c) else when(index%4){0->ThemeManager.panel(c);1->ThemeManager.elevated(c);2->ThemeManager.panel(c);else->ThemeManager.elevated(c)}
        return GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(base,end)).apply{
            cornerRadius=d(22,c).toFloat()
            val ac=ThemeManager.accent(c)
            setStroke(d(1,c),Color.argb(if(ThemeManager.isDark(c))95 else 70,Color.red(ac),Color.green(ac),Color.blue(ac)))
        }"""
new_card = """        if (ThemeManager.get(c) == ThemeManager.PASTEL) {
            // Glass, not opaque pastel: translucent base + saturated color refraction,
            // top-edge sheen and a bright neon rim. No blur dependency or extra view wrapper.
            val accents = intArrayOf(
                Color.rgb(255, 42, 145),   // hot pink
                Color.rgb(0, 220, 255),    // electric cyan
                Color.rgb(164, 78, 255),   // ultraviolet
                Color.rgb(255, 143, 36),   // vivid orange
                Color.rgb(0, 245, 170)     // neon mint
            )
            val accent = accents[Math.floorMod(index, accents.size)]
            val radius = d(22, c).toFloat()
            val glass = RovexVisualSurfaceStyle.glass(c, 22f, true)
            val colorRefraction = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    Color.argb(66, Color.red(accent), Color.green(accent), Color.blue(accent)),
                    Color.argb(18, Color.red(accent), Color.green(accent), Color.blue(accent)),
                    Color.TRANSPARENT
                )
            ).apply { cornerRadius = radius }
            val sheen = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.argb(92, 255, 255, 255), Color.argb(16, 255, 255, 255), Color.TRANSPARENT)
            ).apply { cornerRadius = radius }
            val neonRim = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = radius
                setStroke(d(2, c).coerceAtLeast(1), Color.argb(235, Color.red(accent), Color.green(accent), Color.blue(accent)))
            }
            return android.graphics.drawable.LayerDrawable(arrayOf(glass, colorRefraction, sheen, neonRim))
        }
        val base=if(ThemeManager.isDark(c)) {
            when(index%4){0->ThemeManager.elevated(c);1->ThemeManager.panel(c);2->ThemeManager.elevated(c);else->ThemeManager.panel(c)}
        } else ThemeManager.pastelAccentFill(c,index)
        val end=if(ThemeManager.isDark(c)) ThemeManager.panel(c) else when(index%4){0->ThemeManager.panel(c);1->ThemeManager.elevated(c);2->ThemeManager.panel(c);else->ThemeManager.elevated(c)}
        return GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(base,end)).apply{
            cornerRadius=d(22,c).toFloat()
            val ac=ThemeManager.accent(c)
            setStroke(d(1,c),Color.argb(if(ThemeManager.isDark(c))95 else 70,Color.red(ac),Color.green(ac),Color.blue(ac)))
        }"""
if s.count(old_card) != 1:
    if (!s.includes("Glass, not opaque pastel")) throw new Error("[655] exact card anchor missing");
} else {
    s = s.replace(old_card, new_card);
}
if (!s.includes("ThemeManager.PASTEL -> 0.27f") || !s.includes("Glass, not opaque pastel")) throw new Error("[655] glass postconditions missing");
home.write_text(s, encoding="utf-8")
g = g.replace('versionName = "8.3.652"', 'versionName = "8.3.653"', 1).replace("versionCode = 738", "versionCode = 739", 1)
gradle.write_text(g, encoding="utf-8")
print("[655] translucent glass + saturated color refraction + top sheen + neon rims")
print("[655] restrained motion, original geometry/click targets, v8.3.653 / versionCode 739")
