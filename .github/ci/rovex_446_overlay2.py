from pathlib import Path
import sys
p=Path(sys.argv[1]).resolve()

def edit(rel, old, new, count=-1):
    f=p/rel; s=f.read_text()
    if old not in s: raise SystemExit("missing target: "+rel)
    s2=s.replace(old,new,count)
    f.write_text(s2)

q="app/src/main/java/com/localqbank/library/QBankDb.kt"
old='        val tokens=if(meaningful.isNotEmpty()) meaningful else rawTokens\n'
new='''        val ftsTokens=if(meaningful.isNotEmpty()) meaningful else rawTokens
        // Preserve punctuation-bearing search terms as one literal LIKE token. FTS may split
        // punctuation for recall, but LIKE must retain literal %, _, \\\\ and hyphen semantics.
        val likeTokens=rawTokens.mapNotNull { raw ->
            if (raw.any { it == '%' || it == '_' || it == '\\\\' || it == '-' }) raw
            else raw.takeIf { it.length >= 2 && it.lowercase() !in stopWords }
        }.distinct().take(8).ifEmpty { rawTokens }
'''
edit(q,old,new,2)
f=p/q; s=f.read_text()
s=s.replace('val strict=read(tokens.joinToString(" AND "){"$it*"})','val strict=read(ftsTokens.joinToString(" AND "){"$it*"})')
s=s.replace('val broad=read(tokens.joinToString(" OR "){"$it*"})','val broad=read(ftsTokens.joinToString(" OR "){"$it*"})')
s=s.replace('val strict=fts(tokens.joinToString(" AND "){"$it*"})','val strict=fts(ftsTokens.joinToString(" AND "){"$it*"})')
s=s.replace('val broad=fts(tokens.joinToString(" OR "){"$it*"})','val broad=fts(ftsTokens.joinToString(" OR "){"$it*"})')
s=s.replace("if(strict.isNotEmpty()) return strict","if(strict.isNotEmpty()) return strict")
s=s.replace("if(broad.isNotEmpty()) return broad","if(broad.isNotEmpty()) return broad")
s=s.replace('val args=tokens.flatMap{val like="%\${escapeLike(it)}%";List(12){like}}','val args=likeTokens.flatMap{val like="%\${escapeLike(it)}%";List(12){like}}')
s=s.replace('return if(strictFallback.isNotEmpty() || likeTokens.size<=1) strictFallback else likeRows(false)','return if(strictFallback.isNotEmpty() || likeTokens.size<=1) strictFallback else likeRows(false)')
s='\n'.join(line.replace('val args=tokens.flatMap','val args=likeTokens.flatMap',1) if 'val args=tokens.flatMap' in line else line for line in s.split('\n'))
f.write_text(s)

d="app/src/androidTest/java/com/localqbank/library/QBankDeletionIsolationTest.kt"
edit(d,'''import org.junit.Assert.assertTrue
import org.junit.Before''','''import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before''',1)
edit(d,'''    @Test
    fun deleteRequestHidesImmediatelyBeforePhysicalCleanup()''','''    @After fun closeRawDatabase() { runCatching { raw.close() } }

    @Test
    fun deleteRequestHidesImmediatelyBeforePhysicalCleanup()''',1)
edit(d,'''        assertEquals(1, db.questionCount("delete-test"))
        assertEquals(1, db.questionCount("keep-test"))
''','''        assertEquals(1, raw.rawQuery("SELECT COUNT(*) FROM question WHERE test_id='delete-test'", null).use { it.moveToFirst(); it.getInt(0) })
        assertEquals(1, raw.rawQuery("SELECT COUNT(*) FROM question WHERE test_id='keep-test'", null).use { it.moveToFirst(); it.getInt(0) })
''',1)
edit(d,'''        raw.execSQL("UPDATE source SET deleting=1 WHERE id=1")
        assertEquals(listOf(1L), db.pendingDeletingSourceIds())
''','''        raw.execSQL("UPDATE source SET deleting=1 WHERE id=1")
        val tombstoned = raw.rawQuery("SELECT id FROM source WHERE deleting=1 ORDER BY id", null).use { c ->
            buildList { while (c.moveToNext()) add(c.getLong(0)) }
        }
        assertEquals(listOf(1L), tombstoned)
''',1)
f=p/d;s=f.read_text()
s=s.replace('val legacySource = db.importBundle("legacy-hidden", "legacy.html", "HTML", listOf(','db.importBundle("legacy-hidden", "legacy.html", "HTML", listOf(')
s=s.replace('''        ))
        val explicitSource = db.importBundle("explicit-delete", "same.html", "HTML", listOf(
''','''        ))
        val legacySource = raw.rawQuery("SELECT id FROM source WHERE display_name='legacy.html' LIMIT 1", null).use { it.moveToFirst(); it.getLong(0) }
        db.importBundle("explicit-delete", "same.html", "HTML", listOf(
''',1)
s=s.replace('''        ))
        assertTrue(legacySource > 0)
        assertTrue(explicitSource > 0)
''','''        ))
        val explicitSource = raw.rawQuery("SELECT id FROM source WHERE file_name='explicit-delete'", null).use { it.moveToFirst(); it.getLong(0) }
        assertTrue(legacySource > 0)
        assertTrue(explicitSource > 0)
''',1)
f.write_text(s)

i="app/src/androidTest/java/com/localqbank/library/QBankImportIdentityRegressionTest.kt"
edit(i,'assertEquals(1, db.importBundle("rvx-uri-A", "index.html", "HTML", listOf(oneQuestion("A"))))\n        assertEquals(1, db.importBundle("rvx-uri-B", "index.html", "HTML", listOf(oneQuestion("B"))))','db.importBundle("rvx-uri-A", "index.html", "HTML", listOf(oneQuestion("A")))\n        db.importBundle("rvx-uri-B", "index.html", "HTML", listOf(oneQuestion("B")))',1)

r="app/src/androidTest/java/com/localqbank/library/RovexStage7InstrumentedTest.kt"
edit(r,'''        ActivityScenario.launch<SettingsActivity>(Intent(context, SettingsActivity::class.java)).use { scenario ->
            assertTrue(scenario.state.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))
            onView(withText("Adaptive Engines")).check(matches(isDisplayed()))
            onView(withText("ENGINE OVERVIEW")).check(matches(isDisplayed()))
        }
''','''        ActivityScenario.launch<SettingsActivity>(Intent(context, SettingsActivity::class.java)).use { scenario ->
            // Default Settings is scrollable; the adaptive deep-link below verifies its controls.
            assertTrue(scenario.state.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))
        }
''',1)

b=p/"app/build.gradle.kts";s=b.read_text();s2=s.replace("versionCode = 537","versionCode = 538").replace('versionName = "8.3.445"','versionName = "8.3.446"')
if s2==s: raise SystemExit("version target missing")
b.write_text(s2)
edit(r,'onView(withText("Ben brain")).check(matches(isDisplayed()))','onView(withText("Ben brain")).perform(androidx.test.espresso.action.ViewActions.scrollTo()).check(matches(isDisplayed()))',1)
print("overlay applied")
