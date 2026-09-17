package com.localqbank.library

import android.os.Build
import android.os.Debug
import java.io.File
import java.security.MessageDigest

/**
 * Low-level EmbeddingGemma artifact/device inspection. Kept separate from inference lifecycle
 * so model contract execution does not own diagnostics and file hashing concerns.
 */
internal class BenEmbeddingGemmaArtifactInspector {
    data class RuntimeMemory(val javaHeapMb: Long, val nativeHeapMb: Long)

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun graphHints(file: File): String {
        val needles = listOf("DISPATCH_OP", "LiteRtDispatch", "Qualcomm", "SM8650", "SM8550", "SM8750", "SM8850")
        val found = linkedSetOf<String>()
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            var carry = ByteArray(0)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                val chunk = ByteArray(carry.size + read)
                carry.copyInto(chunk)
                System.arraycopy(buffer, 0, chunk, carry.size, read)
                val text = String(chunk, Charsets.ISO_8859_1)
                for (needle in needles) if (text.contains(needle)) found += needle
                if (found.size == needles.size) break
                carry = chunk.takeLast(64).toByteArray()
            }
        }
        return if (found.isEmpty()) "No known dispatch/Qualcomm markers found in flatbuffer byte scan" else found.joinToString(", ")
    }

    fun deviceDescription(): String {
        val soc = if (Build.VERSION.SDK_INT >= 31) "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}" else Build.HARDWARE
        return "${Build.MANUFACTURER} ${Build.MODEL} / $soc"
    }

    fun runtimeMemory(): RuntimeMemory {
        val r = java.lang.Runtime.getRuntime()
        val javaMb = ((r.totalMemory() - r.freeMemory()).coerceAtLeast(0L) / (1024L * 1024L))
        val nativeMb = Debug.getNativeHeapAllocatedSize() / (1024L * 1024L)
        return RuntimeMemory(javaMb, nativeMb)
    }
}
