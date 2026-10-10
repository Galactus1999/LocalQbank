#!/usr/bin/env python3
"""Phase 655: premium high-contrast animated Home cards, without geometry-changing wrappers."""
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
new_alpha = "            ThemeManager.PASTEL -> 0.32f"
if s.count(old_alpha) == 1:
    s = s.replace(old_alpha, new_alpha, 1)
elif s.count(new_alpha) != 1:
    raise SystemExit("[655] Pastel animation alpha anchor mismatch")

old_card = '''        val base=if(ThemeManager.isDark(c)) {
            when(index%4){0->ThemeManager.elevated(c);1->ThemeManager.panel(c);2->ThemeManager.elevated(c);else->ThemeManager.panel(c)}
        } else ThemeManager.pastelAccentFill(c,index)
        val end=if(ThemeManager.isDark(c)) ThemeManager.panel(c) else when(index%4){0->ThemeManager.panel(c);1->ThemeManager.elevated(c);2->ThemeManager.panel(c);else->ThemeManager.elevated(c)}
        return GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(base,end)).apply{
            cornerRadius=d(22,c).toFloat()
            val ac=ThemeManager.accent(c)
            setStroke(d(1,c),Color.argb(if(ThemeManager.isDark(c))95 else 70,Color.red(ac),Color.green(ac),Color.blue(ac)))
        }'''
new_card = '''        if (ThemeManager.get(c) == ThemeManager.PASTEL) {
            // Distinct but light card fills let the existing Lottie surface remain visible
            // without washing out labels. Strong saturated edge accents create the pop.
            val palettes = arrayOf(
                intArrayOf(Color.rgb(255, 235, 245), Color.rgb(235, 239, 255), Color.rgb(236, 55, 132)),
                intArrayOf(Color.rgb(225, 250, 255), Color.rgb(226, 239, 255), Color.rgb(0, 145, 210)),
                intArrayOf(Color.rgb(241, 231, 255), Color.rgb(255, 238, 249), Color.rgb(126, 76, 220)),
                intArrayOf(Color.rgb(255, 242, 220), Color.rgb(255, 232, 239), Color.rgb(218, 104, 30)),
                intArrayOf(Color.rgb(224, 250, 237), Color.rgb(230, 244, 255), Color.rgb(0, 145, 115))
            )
            val p = palettes[Math.floorMod(index, palettes.size)]
            return GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(p[0], p[1])).apply {
                cornerRadius = d(22, c).toFloat()
                setStroke(d(2, c), Color.argb(218, Color.red(p[2]), Color.green(p[2]), Color.blue(p[2])))
            }
        }
        val base=if(ThemeManager.isDark(c)) {
            when(index%4){0->ThemeManager.elevated(c);1->ThemeManager.panel(c);2->ThemeManager.elevated(c);else->ThemeManager.panel(c)}
        } else ThemeManager.pastelAccentFill(c,index)
        val end=if(ThemeManager.isDark(c)) ThemeManager.panel(c) else when(index%4){0->ThemeManager.panel(c);1->ThemeManager.elevated(c);2->ThemeManager.panel(c);else->ThemeManager.elevated(c)}
        return GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(base,end)).apply{
            cornerRadius=d(22,c).toFloat()
            val ac=ThemeManager.accent(c)
            setStroke(d(1,c),Color.argb(if(ThemeManager.isDark(c))95 else 70,Color.red(ac),Color.green(ac),Color.blue(ac)))
        }'''
if s.count(old_card) != 1:
    if "Distinct but light card fills let the existing Lottie surface remain visible" not in s:
        raise SystemExit("[655] card drawable anchor mismatch")
else:
    s = s.replace(old_card, new_card, 1)

if "ThemeManager.PASTEL -> 0.32f" not in s or "Distinct but light card fills" not in s:
    raise SystemExit("[655] visual postconditions missing")
home.write_text(s, encoding="utf-8")
g = g.replace('versionName = "8.3.652"', 'versionName = "8.3.653"', 1).replace("versionCode = 738", "versionCode = 739", 1)
gradle.write_text(g, encoding="utf-8")
print("[655] Pastel Home cards use distinct vivid accent gradients and stronger outlines")
print("[655] Pastel Lottie surface visibility increased; text fill remains light for contrast")
print("[655] existing card geometry, click targets and motion pause/resume policy preserved")
print("[655] applied v8.3.653 / versionCode 739")
