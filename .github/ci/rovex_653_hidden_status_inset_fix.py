#!/usr/bin/env python3
"""Phase 653: prevent hidden status-bar insets from becoming blank fullscreen headers."""
from pathlib import Path
import sys

project = Path(sys.argv[1]).resolve()
gradle = project / "app/build.gradle.kts"
if not gradle.is_file():
    raise SystemExit("[653] missing app/build.gradle.kts")
g = gradle.read_text(encoding="utf-8")
if 'versionName = "8.3.650"' not in g or "versionCode = 736" not in g:
    raise SystemExit("[653] expected v8.3.650 / versionCode 736 baseline")

manager = project / "app/src/main/java/com/localqbank/library/AdaptiveLayoutManager.kt"
if not manager.is_file():
    raise SystemExit("[653] AdaptiveLayoutManager.kt missing")
s = manager.read_text(encoding="utf-8")
old = """            val top = bars.top + dp(v,topExtraDp)
            val bottom = bars.bottom + dp(v,bottomExtraDp)"""
new = """            // Hidden-status-bar screens must not reserve the hidden status bar's inset.
            // Otherwise the fullscreen content begins below an empty band even though the
            // status bar is invisible (notably obvious in Pastel flashcard review).
            // Display cutouts remain protected independently of status-bar visibility.
            val topSafe = if (keepStatusBarVisible) {
                bars.top
            } else if (protectDisplayCutout) {
                insets.getInsets(WindowInsetsCompat.Type.displayCutout()).top
            } else {
                0
            }
            val top = topSafe + dp(v,topExtraDp)
            val bottom = bars.bottom + dp(v,bottomExtraDp)"""
if s.count(old) != 1:
    if "val topSafe = if (keepStatusBarVisible)" in s:
        print("[653] hidden-status-bar inset fix already applied")
    else:
        raise SystemExit("[653] exact AdaptiveLayoutManager inset anchor missing")
else:
    s = s.replace(old, new, 1)
    manager.write_text(s, encoding="utf-8")

if "val topSafe = if (keepStatusBarVisible)" not in s:
    raise SystemExit("[653] top inset policy postcondition failed")
if "insets.getInsets(WindowInsetsCompat.Type.displayCutout()).top" not in s:
    raise SystemExit("[653] display-cutout protection was not preserved")

gradle.write_text(g.replace('versionName = "8.3.650"', 'versionName = "8.3.651"', 1).replace("versionCode = 736", "versionCode = 737", 1), encoding="utf-8")
print("[653] hidden status-bar screens no longer reserve a status-bar-height top inset")
print("[653] display-cutout and bottom navigation insets remain protected")
print("[653] applied v8.3.651 / versionCode 737")
