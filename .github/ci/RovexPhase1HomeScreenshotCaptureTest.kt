package com.localqbank.library

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

/**
 * CI-only Phase 1 visual capture. This test is injected into the selected source archive
 * before Gradle runs because the archive can predate the repository's test sources.
 */
@RunWith(AndroidJUnit4::class)
class RovexPhase1HomeScreenshotCaptureTest {
    @Test
    fun captureSixThemeHomeScreenshots() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val previousTheme = ThemeManager.get(context)
        val cases = listOf(
            "light" to ThemeManager.LIGHT,
            "amoled" to ThemeManager.AMOLED,
            "mint" to ThemeManager.MINT,
            "sunset" to ThemeManager.SUNSET,
            "lavender" to ThemeManager.LAVENDER,
            "pastel" to ThemeManager.PASTEL
        )
        try {
            for ((name, theme) in cases) {
                ThemeManager.set(context, theme)
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                    var attached = false
                    for (attempt in 0 until 200) {
                        instrumentation.waitForIdleSync()
                        scenario.onActivity { activity ->
                            val root = activity.findViewById<ViewGroup>(android.R.id.content)
                            attached = root.findViewWithTag<View>("ROVEX_HOME_SHELL") != null &&
                                root.findViewWithTag<View>("ROVEX_HOME_SCROLL") != null
                        }
                        if (attached) break
                        Thread.sleep(50L)
                    }
                    check(attached) { "Home dashboard did not attach for screenshot theme=$name" }
                    instrumentation.waitForIdleSync()
                    Thread.sleep(250L)
                    val bitmap = instrumentation.uiAutomation.takeScreenshot()
                    try {
                        val directory = context.getExternalFilesDir(null)
                            ?: error("External screenshot directory unavailable")
                        check(directory.exists() || directory.mkdirs()) { "Cannot create screenshot directory" }
                        val file = java.io.File(directory, "phase1_home_" + name + ".png")
                        android.util.Log.i(
                            "RovexVisualTruth",
                            "Saving theme screenshot theme=" + name + " package=" +
                                context.packageName + " path=" + file.absolutePath
                        )
                        java.io.FileOutputStream(file).use { output ->
                            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)) {
                                "PNG compression failed for theme=$name"
                            }
                        }
                        check(file.length() > 1024L) { "Captured screenshot is empty for theme=$name" }
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
        } finally {
            ThemeManager.set(context, previousTheme)
        }
    }
}
