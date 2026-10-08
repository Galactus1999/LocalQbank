#!/usr/bin/env python3
"""Add CI-only rendered screenshot capture to verified v8.3.619 source."""
from pathlib import Path
import sys

EXPECTED_VERSION = 'versionName = "8.3.619"'
EXPECTED_CODE = "versionCode = 705"

TEST = r'''package com.localqbank.library

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import java.io.BufferedReader
import java.io.InputStreamReader

/** Captures real rendered activity surfaces for CI visual truth. Never ships in the release APK. */
@RunWith(AndroidJUnit4::class)
class RovexVisualTruthCaptureTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun shell(command: String) {
        val fd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        BufferedReader(InputStreamReader(android.os.ParcelFileDescriptor.AutoCloseInputStream(fd))).use { it.readText() }
    }

    private fun capture(name: String) {
        shell("mkdir -p /sdcard/RovexVisualTruth")
        shell("screencap -p /sdcard/RovexVisualTruth/$name.png")
        // The host-side collector validates the file after instrumentation.
        // Avoid making the test depend on shell-output timing for the screenshot file.
    }

    private fun settle() { Thread.sleep(1200) }

    @Test
    fun captureCoreRenderedScreens() {
        shell("rm -rf /sdcard/RovexVisualTruth")
        shell("mkdir -p /sdcard/RovexVisualTruth")
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use {
            check(it.state.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)); settle(); capture("01_home")
        }
        ActivityScenario.launch<RovexSectionDashboardActivity>(
            Intent(context, RovexSectionDashboardActivity::class.java).putExtra("section", "qbank")
        ).use {
            check(it.state.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)); settle(); capture("02_qbank")
        }
        ActivityScenario.launch<RovexSectionDashboardActivity>(
            Intent(context, RovexSectionDashboardActivity::class.java).putExtra("section", "flashcards")
        ).use {
            check(it.state.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)); settle(); capture("03_flashcards")
        }
        ActivityScenario.launch<RenActivity>(Intent(context, RenActivity::class.java)).use {
            check(it.state.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)); settle(); capture("04_ren")
        }
        ActivityScenario.launch<SettingsActivity>(Intent(context, SettingsActivity::class.java)).use {
            check(it.state.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)); settle(); capture("05_settings")
        }
        ActivityScenario.launch<VisualLabActivity>(Intent(context, VisualLabActivity::class.java)).use {
            check(it.state.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)); settle(); capture("06_visual_lab")
        }
    }
}
'''

def main() -> int:
    if len(sys.argv) != 2:
        raise SystemExit("usage: rovex_620_visual_truth_overlay.py <project>")
    project = Path(sys.argv[1]).resolve()
    app = project / "app"
    gradle = app / "build.gradle.kts"
    test_dir = app / "src" / "androidTest" / "java" / "com" / "localqbank" / "library"
    if not gradle.is_file() or not test_dir.is_dir():
        raise SystemExit("v8.3.620 overlay: required baseline files missing")
    g = gradle.read_text(encoding="utf-8")
    if EXPECTED_VERSION not in g or EXPECTED_CODE not in g:
        raise SystemExit("v8.3.620 overlay requires v8.3.619/705; refusing to patch")
    target = test_dir / "RovexVisualTruthCaptureTest.kt"
    if target.exists():
        raise SystemExit("v8.3.620 overlay: visual truth test already exists")
    target.write_text(TEST, encoding="utf-8")
    g = g.replace(EXPECTED_VERSION, 'versionName = "8.3.620"', 1)
    g = g.replace(EXPECTED_CODE, "versionCode = 706", 1)
    gradle.write_text(g, encoding="utf-8")
    print("v8.3.620 rendered visual truth overlay: APPLIED")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
