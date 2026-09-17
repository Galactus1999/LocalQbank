package com.localqbank.library.benchmark

import android.content.ComponentName
import android.content.Intent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MemoryCountersMetric
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.uiAutomator
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TARGET_PACKAGE = "com.localqbank.library"
private const val MAIN_ACTIVITY = "$TARGET_PACKAGE.MainActivity"

@OptIn(ExperimentalMetricApi::class)
@LargeTest
@RunWith(AndroidJUnit4::class)
class ColdStartupBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    private fun measureStartup(compilationMode: CompilationMode) = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(
            StartupTimingMetric(),
            FrameTimingMetric(),
            MemoryUsageMetric(mode = MemoryUsageMetric.Mode.Max),
            MemoryCountersMetric(),
            TraceSectionMetric("Rovex.StartupCoordinator"),
            TraceSectionMetric("Rovex.MainActivity.firstUsable")
        ),
        compilationMode = compilationMode,
        startupMode = StartupMode.COLD,
        iterations = 5,
        setupBlock = { pressHome() }
    ) {
        uiAutomator {
            startActivityAndWait(Intent().setComponent(ComponentName(TARGET_PACKAGE, MAIN_ACTIVITY)))
            onElement(10_000) { viewIdResourceName == "homeSearchCard" && isVisibleToUser }
            onElement(10_000) { viewIdResourceName == "libraryList" && isVisibleToUser }
        }
    }

    @Test
    fun coldStartupNoCompilation() = measureStartup(CompilationMode.None())

    @Test
    fun coldStartupPartialCompilation() = measureStartup(CompilationMode.Partial())
}
