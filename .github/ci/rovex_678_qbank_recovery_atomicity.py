#!/usr/bin/env python3
"""Phase 678: serialize durable QBank deletion recovery marker generations."""
from pathlib import Path
import re
import sys

root = Path(sys.argv[1]).resolve()
app = root / "app"
pkg = app / "src/main/java/com/localqbank/library"
gradle = app / "build.gradle.kts"
worker_path = pkg / "QBankDeletionRecoveryWorker.kt"
test_path = app / "src/androidTest/java/com/localqbank/library/QBankDeletionRecoveryAdmissionTest.kt"
if not all(p.is_file() for p in (gradle, worker_path, test_path)):
    raise SystemExit("[678] FATAL: required Gradle, recovery worker, or regression test file missing")

g = gradle.read_text(encoding="utf-8")
if 'versionName = "8.3.675"' not in g or not re.search(r"\bversionCode\s*=\s*761\b", g):
    raise SystemExit("[678] FATAL: expected v8.3.675 / code 761 after Phase 677")

worker = worker_path.read_text(encoding="utf-8")
def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"[678] FATAL: {label} anchor expected once, found {count}")
    return text.replace(old, new, 1)

worker = replace_once(
    worker,
    "        private val admissionLock = Any()\n",
    "        private val admissionLock = Any()\n        // Serialize generation read/modify/write with terminal marker clearing.\n        private val recoveryStateLock = Any()\n",
    "recovery state lock",
)
worker = replace_once(
    worker,
    '''        fun markRecoveryNeeded(context: Context) {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            // This flag is the crash-recovery hand-off. apply() is intentionally not sufficient:
            // Android may kill the process before the asynchronous disk write completes.
            check(
                prefs.edit()
                    .putBoolean(KEY_NEEDED, true)
                    .putLong(KEY_GENERATION, prefs.getLong(KEY_GENERATION, 0L) + 1L)
                    .commit()
            ) { "Unable to persist QBank deletion recovery marker" }
        }
''',
    '''        fun markRecoveryNeeded(context: Context) {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            // Read + increment + durable commit are one in-process critical section. Without it,
            // concurrent delete/import requests can read the same generation and overwrite one
            // another, allowing a worker to mistake a newer request for the generation it handled.
            synchronized(recoveryStateLock) {
                val nextGeneration = prefs.getLong(KEY_GENERATION, 0L) + 1L
                check(
                    prefs.edit()
                        .putBoolean(KEY_NEEDED, true)
                        .putLong(KEY_GENERATION, nextGeneration)
                        .commit()
                ) { "Unable to persist QBank deletion recovery marker" }
            }
        }
''',
    "atomic marker increment",
)
worker = replace_once(
    worker,
    '''        private fun clearRecoveryNeeded(context: Context, expectedGeneration: Long) {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (prefs.getLong(KEY_GENERATION, 0L) == expectedGeneration) {
                // Clearing is also synchronous so the marker cannot become stale after a
                // successful terminal pass. A stale marker is safe, but the recovery state
                // itself is explicitly documented as durable state.
                check(prefs.edit().putBoolean(KEY_NEEDED, false).commit()) {
                    "Unable to persist QBank deletion recovery completion marker"
                }
            }
        }
''',
    '''        private fun clearRecoveryNeeded(context: Context, expectedGeneration: Long): Boolean =
            synchronized(recoveryStateLock) {
                val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                if (prefs.getLong(KEY_GENERATION, 0L) != expectedGeneration) return@synchronized false
                // Compare + clear share the same lock as marker increments, so a concurrent
                // request cannot be erased between the generation check and the durable commit.
                check(prefs.edit().putBoolean(KEY_NEEDED, false).commit()) {
                    "Unable to persist QBank deletion recovery completion marker"
                }
                true
            }
''',
    "atomic marker clear",
)
worker = replace_once(
    worker,
    '''            if (failed || currentGeneration != startGeneration) {
                // A new deletion request arrived while this run was finishing. Keep the marker
                // and ask WorkManager for another pass instead of clearing a newer request.
                Result.retry()
            } else {
                clearRecoveryNeeded(applicationContext, startGeneration)
                Result.success()
            }
''',
    '''            if (failed || currentGeneration != startGeneration) {
                // A new deletion request arrived while this run was finishing. Keep the marker
                // and ask WorkManager for another pass instead of clearing a newer request.
                Result.retry()
            } else if (clearRecoveryNeeded(applicationContext, startGeneration)) {
                Result.success()
            } else {
                // The generation changed after the earlier snapshot but before the clear lock.
                // Do not report success while a newer durable recovery request remains pending.
                Result.retry()
            }
''',
    "worker terminal decision",
)
worker_path.write_text(worker, encoding="utf-8")

test = test_path.read_text(encoding="utf-8")
if "import java.util.concurrent.CountDownLatch" not in test:
    test = replace_once(test, "import java.util.concurrent.TimeUnit\n", "import java.util.concurrent.CountDownLatch\nimport java.util.concurrent.TimeUnit\nimport java.util.concurrent.atomic.AtomicReference\n", "test concurrency imports")
anchor = '''    @Test
    fun recoveryMarkerIsDurableAcrossFreshContextHandle() {
'''
new_test = '''    @Test
    fun concurrentRecoveryRequestsDoNotLoseGenerationIncrements() {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val initialGeneration = prefs.getLong(KEY_GENERATION, 0L)
        val requestCount = 32
        val ready = CountDownLatch(requestCount)
        val start = CountDownLatch(1)
        val finished = CountDownLatch(requestCount)
        val failure = AtomicReference<Exception?>(null)

        repeat(requestCount) {
            Thread {
                ready.countDown()
                try {
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        failure.compareAndSet(null, IllegalStateException("start barrier timed out"))
                    } else {
                        QBankDeletionRecoveryWorker.markRecoveryNeeded(context)
                    }
                } catch (error: Exception) {
                    failure.compareAndSet(null, error)
                } finally {
                    finished.countDown()
                }
            }.start()
        }

        assertTrue("marker writers did not reach start barrier", ready.await(5, TimeUnit.SECONDS))
        start.countDown()
        assertTrue("marker writers did not finish", finished.await(15, TimeUnit.SECONDS))
        failure.get()?.let { throw AssertionError("concurrent recovery marker write failed", it) }
        assertEquals(initialGeneration + requestCount, prefs.getLong(KEY_GENERATION, 0L))
        assertTrue(QBankDeletionRecoveryWorker.isRecoveryNeeded(context))
    }

    @Test
    fun recoveryMarkerIsDurableAcrossFreshContextHandle() {
'''
test = replace_once(test, anchor, new_test, "concurrent marker regression test")
if 'private const val KEY_GENERATION = "recovery_generation"' not in test:
    test = replace_once(
        test,
        'private const val KEY_NEEDED = "recovery_needed"\n',
        'private const val KEY_NEEDED = "recovery_needed"\n        private const val KEY_GENERATION = "recovery_generation"\n',
        "test generation key",
    )
test_path.write_text(test, encoding="utf-8")

g = g.replace('versionName = "8.3.675"', 'versionName = "8.3.676"', 1).replace("versionCode = 761", "versionCode = 762", 1)
if 'versionName = "8.3.676"' not in g or not re.search(r"\bversionCode\s*=\s*762\b", g):
    raise SystemExit("[678] FATAL: version bump postcondition failed")
gradle.write_text(g, encoding="utf-8")

worker_after = worker_path.read_text(encoding="utf-8")
test_after = test_path.read_text(encoding="utf-8")
for token in ("private val recoveryStateLock = Any()", "synchronized(recoveryStateLock)", "private fun clearRecoveryNeeded(context: Context, expectedGeneration: Long): Boolean"):
    if token not in worker_after:
        raise SystemExit(f"[678] FATAL: worker postcondition missing: {token}")
if "concurrentRecoveryRequestsDoNotLoseGenerationIncrements" not in test_after:
    raise SystemExit("[678] FATAL: concurrent marker regression test postcondition failed")
if 'private const val KEY_GENERATION = "recovery_generation"' not in test_after:
    raise SystemExit("[678] FATAL: test recovery generation key postcondition failed")
print("[678] PASS: recovery marker generation increments and terminal clearing are serialized.")
print("[678] PASS: concurrent marker regression test added; version is 8.3.676 / code 762.")
