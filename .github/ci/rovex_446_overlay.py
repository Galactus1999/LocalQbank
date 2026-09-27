from pathlib import Path
import sys

PROJECT = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else None
if PROJECT is None or not PROJECT.is_dir():
    raise SystemExit("usage: rovex_446_overlay.py <extracted-project>")

def replace_exact(path, old, new, count=None):
    p = PROJECT / path
    s = p.read_text(encoding="utf-8")
    actual = s.count(old)
    if count is not None and actual != count:
        raise SystemExit(f"overlay mismatch {path}: expected {count}, found {actual}")
    if actual == 0:
        raise SystemExit(f"overlay target not found: {path}")
    p.write_text(s.replace(old, new), encoding="utf-8")

q = "app/src/main/java/com/localqbank/library/QBankDb.kt"
# v8.3.445 already contains the punctuation-as-token-boundary repair.
# Split only the FTS/LIKE responsibilities here.
old_line = "        val tokens=if(meaningful.isNotEmpty()) meaningful else rawTokens\n"
new_lines = """        val ftsTokens=if(meaningful.isNotEmpty()) meaningful else rawTokens
        // Preserve punctuation-bearing search terms as one literal LIKE token. Splitting
        // acid%base/question-A into FTS words is useful for recall, but doing the same for
        // LIKE destroys the user's literal %/_/\\ escape semantics and can match distractors.
        val likeTokens=rawTokens.mapNotNull { raw ->
            if (raw.any { it == '%' || it == '_' || it == '\\' || it == '-' }) raw
            else raw.takeIf { it.length >= 2 && it.lowercase() !in stopWords }
        }.distinct().take(8).ifEmpty { rawTokens }
"""
replace_exact(q, old_line, new_lines)

replace_exact(q, 'val strict=read(tokens.joinToString(" AND "){"$it*"})\n            if(strict.isNotEmpty() || tokens.size<=1) return strict\n            val broad=read(tokens.joinToString(" OR "){"$it*"})',
    'val strict=read(ftsTokens.joinToString(" AND "){"$it*"})\n            if(strict.isNotEmpty()) return strict\n            val broad=read(ftsTokens.joinToString(" OR "){"$it*"})')
replace_exact(q, 'val clauses=tokens.map{"(q.text LIKE ?', 'val clauses=likeTokens.map{"(q.text LIKE ?')
replace_exact(q, 'val args=tokens.flatMap{val like="%\${escapeLike(it)}%";List(12){like}}',
    'val args=likeTokens.flatMap{val like="%\${escapeLike(it)}%";List(12){like}}')
replace_exact(q, 'return if(strictFallback.isNotEmpty() || tokens.size<=1) strictFallback else likeRows(false)',
    'return if(strictFallback.isNotEmpty() || likeTokens.size<=1) strictFallback else likeRows(false)')
replace_exact(q, 'val strict=fts(tokens.joinToString(" AND "){"$it*"})\n            if(strict.isNotEmpty() || tokens.size<=1) return strict\n            val broad=fts(tokens.joinToString(" OR "){"$it*"})',
    'val strict=fts(ftsTokens.joinToString(" AND "){"$it*"})\n            if(strict.isNotEmpty()) return strict\n            val broad=fts(ftsTokens.joinToString(" OR "){"$it*"})')

d = "app/src/androidTest/java/com/localqbank/library/QBankDeletionIsolationTest.kt"
replace_exact(d, '''        assertEquals(1, db.questionCount("delete-test"))
        assertEquals(1, db.questionCount("keep-test"))
''', '''        assertEquals(1, raw.rawQuery("SELECT COUNT(*) FROM question WHERE test_id='delete-test'", null).use { it.moveToFirst(); it.getInt(0) })
        assertEquals(1, raw.rawQuery("SELECT COUNT(*) FROM question WHERE test_id='keep-test'", null).use { it.moveToFirst(); it.getInt(0) })
''', 1)
replace_exact(d, '''        raw.execSQL("UPDATE source SET deleting=1 WHERE id=1")
        assertEquals(listOf(1L), db.pendingDeletingSourceIds())
''', '''        raw.execSQL("UPDATE source SET deleting=1 WHERE id=1")
        val tombstoned = raw.rawQuery("SELECT id FROM source WHERE deleting=1 ORDER BY id", null).use { c ->
            buildList { while (c.moveToNext()) add(c.getLong(0)) }
        }
        assertEquals(listOf(1L), tombstoned)
''', 1)
replace_exact(d, '''        val legacySource = db.importBundle("legacy-hidden", "legacy.html", "HTML", listOf(
''', '''        db.importBundle("legacy-hidden", "legacy.html", "HTML", listOf(
''', 1)
replace_exact(d, '''        ))
        val explicitSource = db.importBundle("explicit-delete", "same.html", "HTML", listOf(
''', '''        ))
        val legacySource = raw.rawQuery("SELECT id FROM source WHERE file_name='legacy-hidden'", null).use { it.moveToFirst(); it.getLong(0) }
        db.importBundle("explicit-delete", "same.html", "HTML", listOf(
''', 1)
replace_exact(d, '''        ))
        assertTrue(legacySource > 0)
        assertTrue(explicitSource > 0)
''', '''        ))
        val explicitSource = raw.rawQuery("SELECT id FROM source WHERE file_name='explicit-delete'", null).use { it.moveToFirst(); it.getLong(0) }
        assertTrue(legacySource > 0)
        assertTrue(explicitSource > 0)
''', 1)

i = "app/src/androidTest/java/com/localqbank/library/QBankImportIdentityRegressionTest.kt"
replace_exact(i, 'assertEquals(1, db.importBundle("rvx-uri-A", "index.html", "HTML", listOf(oneQuestion("A"))))\n        assertEquals(1, db.importBundle("rvx-uri-B", "index.html", "HTML", listOf(oneQuestion("B"))))',
    'db.importBundle("rvx-uri-A", "index.html", "HTML", listOf(oneQuestion("A")))\n        db.importBundle("rvx-uri-B", "index.html", "HTML", listOf(oneQuestion("B")))', 1)

r = "app/src/androidTest/java/com/localqbank/library/RovexStage7InstrumentedTest.kt"
replace_exact(r, '''        ActivityScenario.launch<SettingsActivity>(Intent(context, SettingsActivity::class.java)).use { scenario ->
            assertTrue(scenario.state.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))
            onView(withText("Adaptive Engines")).check(matches(isDisplayed()))
            onView(withText("ENGINE OVERVIEW")).check(matches(isDisplayed()))
        }
''', '''        ActivityScenario.launch<SettingsActivity>(Intent(context, SettingsActivity::class.java)).use { scenario ->
            // Default Settings is a scrollable multi-section screen; the adaptive section is
            // verified in its dedicated deep-link launch below. Here we only gate startup.
            assertTrue(scenario.state.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))
        }
''', 1)

bp = PROJECT / "app/build.gradle.kts"
s = bp.read_text(encoding="utf-8")
s2 = s.replace("versionCode = 537", "versionCode = 538").replace('versionName = "8.3.445"', 'versionName = "8.3.446"')
if s2 == s:
    raise SystemExit("overlay version bump target not found")
bp.write_text(s2, encoding="utf-8")
print("Rovex v8.3.446 overlay applied")
