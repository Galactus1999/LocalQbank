#!/usr/bin/env python3
"""Phase 654: fail safely instead of leaving a partially initialized flashcard reviewer alive."""
from pathlib import Path
import sys

project = Path(sys.argv[1]).resolve()
gradle = project / "app/build.gradle.kts"
source = project / "app/src/main/java/com/localqbank/library/FlashcardStudyActivity.kt"
if not gradle.is_file() or not source.is_file():
    raise SystemExit("[654] expected Gradle or FlashcardStudyActivity source missing")
g = gradle.read_text(encoding="utf-8")
if 'versionName = "8.3.651"' not in g or "versionCode = 737" not in g:
    raise SystemExit("[654] expected Phase 653 v8.3.651 / versionCode 737 baseline")

s = source.read_text(encoding="utf-8")
old = '''        } catch(t:Exception) { ProductionCrashReporter.recordNonFatal(t,"FLASHCARD_STUDY_OPEN"); RovexUndoSnackbar.show(window.decorView,"Unable to open flashcards: ${t.message ?: t.javaClass.simpleName}") }'''
new = '''        } catch(t:Exception) {
            // Do not leave a half-built reviewer Activity visible after a startup failure.
            // Reporting/UI feedback are best-effort; teardown must still happen if either fails.
            runCatching { ProductionCrashReporter.recordNonFatal(t,"FLASHCARD_STUDY_OPEN") }
            runCatching {
                if (!isFinishing && !isDestroyed) {
                    RovexUndoSnackbar.show(window.decorView,"Unable to open flashcards: ${t.message ?: t.javaClass.simpleName}")
                }
            }
            if (!isFinishing && !isDestroyed) window.decorView.post { if (!isFinishing && !isDestroyed) finish() }
        }'''
if s.count(old) != 1:
    if "Do not leave a half-built reviewer Activity visible after a startup failure." in s:
        print("[654] startup-failure teardown already applied")
    else:
        raise SystemExit("[654] exact FlashcardStudyActivity startup catch anchor missing")
else:
    s = s.replace(old, new, 1)
    source.write_text(s, encoding="utf-8")

if "Do not leave a half-built reviewer Activity visible after a startup failure." not in s:
    raise SystemExit("[654] startup teardown postcondition failed")
if 'window.decorView.post { if (!isFinishing && !isDestroyed) finish() }' not in s:
    raise SystemExit("[654] deferred safe teardown missing")

g = g.replace('versionName = "8.3.651"', 'versionName = "8.3.652"', 1).replace("versionCode = 737", "versionCode = 738", 1)
gradle.write_text(g, encoding="utf-8")
print("[654] startup failure reporting is best-effort and partial reviewer Activity is safely closed")
print("[654] normal study, reveal, rating, progress and SRS paths are unchanged")
print("[654] applied v8.3.652 / versionCode 738")
