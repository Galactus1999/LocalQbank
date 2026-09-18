package com.localqbank.library

import android.app.ActivityManager
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Phase-2 device stress coverage for the already-green Ben IPC architecture.
 *
 * This test is intentionally test-only: the production IPC/diagnostic implementation is not
 * modified by the stress harness. Run on the OnePlus SM8650/CPH2691 device.
 */
@RunWith(AndroidJUnit4::class)
class BenInferenceProcessStressTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun quarantinedNeuralRuntime_doesNotStartNativeInference() = runBlocking {
        val policy = BenAiRuntimePolicy(context)
        assertTrue("Neural execution must remain disabled by default", !policy.enabled)
        assertTrue("Disabled neural runtime must be blocked", policy.blockReason(300, 300,
            BenAiResourceGovernor.Snapshot(true, 4096, 0, false, false, 1)) != null)
    }

    @Test
    fun repeatedTransportRequestsRemainTerminalAndBounded() = runBlocking {
        BenInferenceProcessClient(context).use { client ->
            repeat(12) { index ->
                val ping = client.pingSuspend(10_000L)
                assertTrue("Transport ping #${index + 1} failed: ${ping.failure}", ping.ok)
            }
        }
    }

    @Test
    fun workerProcessDeath_isRecoveredAtTransportLayerWithoutNativeRestart() = runBlocking {
        BenInferenceProcessClient(context).use { client ->
            val before = client.pingSuspend(10_000L)
            assertTrue("Initial Binder ping failed: ${before.failure}", before.ok)
            val pid = findInferenceWorkerPid()
            assertNotNull("Could not locate :inference_process PID", pid)
            killPid(pid!!)
            delay(800L)
            val recovered = client.pingSuspend(15_000L)
            assertTrue("Binder did not recover after worker death: ${recovered.failure}", recovered.ok)
            android.util.Log.i("BenPhase2", "TRANSPORT_PROCESS_DEATH pid=$pid recovered=true")
        }
    }

    @Test
    fun repeatedTrimAndRebindLeavesTransportHealthy() = runBlocking {
        repeat(6) { index ->
            BenInferenceProcessClient(context).use { client ->
                val ping = client.pingSuspend(10_000L)
                assertTrue("Cycle ${index + 1} initial ping failed: ${ping.failure}", ping.ok)
                client.trim()
                delay(150L)
                val rebound = client.pingSuspend(10_000L)
                assertTrue("Cycle ${index + 1} rebound ping failed: ${rebound.failure}", rebound.ok)
            }
        }
    }

    private fun BenInferenceProcessClient.GenerationEvent.isTerminal(): Boolean = when (this) {
        is BenInferenceProcessClient.GenerationEvent.Complete,
        is BenInferenceProcessClient.GenerationEvent.Failed,
        BenInferenceProcessClient.GenerationEvent.Cancelled,
        BenInferenceProcessClient.GenerationEvent.ProcessDied -> true
        is BenInferenceProcessClient.GenerationEvent.Token -> false
    }

    private fun findInferenceWorkerPid(): Int? {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val fromAm = am.runningAppProcesses.orEmpty()
            .firstOrNull { it.processName == "${context.packageName}:inference_process" }
            ?.pid
        if (fromAm != null && fromAm > 0) return fromAm

        return runCatching {
            val fd = InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("pidof ${context.packageName}:inference_process")
            BufferedReader(InputStreamReader(android.os.ParcelFileDescriptor.AutoCloseInputStream(fd)))
                .readLine()
                ?.trim()
                ?.split(Regex("\\s+"))
                ?.firstOrNull()
                ?.toIntOrNull()
        }.getOrNull()
    }

    private fun killPid(pid: Int) {
        val fd = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("kill -9 $pid")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
    }
}
