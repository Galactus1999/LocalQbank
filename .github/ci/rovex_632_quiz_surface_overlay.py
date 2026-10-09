#!/usr/bin/env python3
from pathlib import Path
import sys

OLD_NAME='versionName = "8.3.631"'
OLD_CODE='versionCode = 717'
NEW_NAME='versionName = "8.3.632"'
NEW_CODE='versionCode = 718'

def one(root: Path, name: str) -> Path:
    xs = list(root.rglob(name))
    if len(xs) != 1:
        raise SystemExit(f"v8.3.632 expected one {name}, found {len(xs)}")
    return xs[0]

def replace_once(s: str, old: str, new: str, label: str) -> str:
    if old not in s:
        raise SystemExit(f"v8.3.632 anchor missing: {label}")
    return s.replace(old, new, 1)

def main():
    if len(sys.argv) != 2:
        raise SystemExit("usage: rovex_632_quiz_surface_overlay.py <project>")
    root = Path(sys.argv[1]).resolve()
    gradle = root / "app/build.gradle.kts"
    if not gradle.is_file():
        raise SystemExit("v8.3.632: missing app/build.gradle.kts")
    g = gradle.read_text(encoding="utf-8")
    if OLD_NAME not in g or OLD_CODE not in g:
        raise SystemExit("v8.3.632 requires v8.3.631/717; refusing to patch")

    p = one(root, "QuizLayoutBuilder.kt")
    s = p.read_text(encoding="utf-8")
    if "v8.3.632" in s:
        raise SystemExit("v8.3.632 already applied")

    # Keep question rendering/state logic unchanged. Consolidate only the quiz shell.
    old_info = """            setPadding(dp(18), dp(3), dp(18), dp(4))
            background=ThemeManager.backgroundDrawable(context)
        }"""
    new_info = """            setPadding(dp(18), dp(3), dp(18), dp(4))
            background=RovexVisualSurfaceStyle.glass(context, 14f)
        }"""
    s = replace_once(s, old_info, new_info, "quiz info rail surface")

    old_settings = """            setTextColor(ThemeManager.accent(context))
            background = rounded(ThemeManager.elevated(context), 11f)
            contentDescription = "Question settings"
            setOnClickListener { onShowSettings(this) }"""
    new_settings = """            setTextColor(ThemeManager.accent(context))
            RovexVisualButtonStyle.apply(this, context)
            contentDescription = "Question settings"
            setOnClickListener { onShowSettings(this) }"""
    s = replace_once(s, old_settings, new_settings, "quiz settings control")

    old_jump = """            setTextColor(ThemeManager.accent(context))
            gravity = Gravity.CENTER
            background = rounded(ThemeManager.explanationBg(context), 11f)
            setPadding(dp(8), 0, dp(8), 0)
            setOnClickListener { onShowQuestionJump(this) }"""
    new_jump = """            setTextColor(ThemeManager.accent(context))
            gravity = Gravity.CENTER
            RovexVisualButtonStyle.apply(this, context)
            setPadding(dp(8), 0, dp(8), 0)
            setOnClickListener { onShowQuestionJump(this) }"""
    s = replace_once(s, old_jump, new_jump, "quiz jump control")

    old_result = """            setTypeface(null, Typeface.BOLD)
            setBackgroundColor(ThemeManager.panel(context))
            visibility = View.GONE"""
    new_result = """            setTypeface(null, Typeface.BOLD)
            background = RovexVisualSurfaceStyle.glass(context, 12f)
            visibility = View.GONE"""
    s = replace_once(s, old_result, new_result, "quiz result surface")

    marker = """        // v8.3.632: quiz shell surface hierarchy uses semantic visual tokens; question rendering is unchanged.
"""
    anchor = """    fun build(): Result {
"""
    s = replace_once(s, anchor, anchor + marker, "phase marker")

    p.write_text(s, encoding="utf-8")
    g = g.replace(OLD_NAME, NEW_NAME, 1).replace(OLD_CODE, NEW_CODE, 1)
    gradle.write_text(g, encoding="utf-8")
    print("v8.3.632 quiz surface consolidation: APPLIED")

if __name__ == "__main__":
    main()
