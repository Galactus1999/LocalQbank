package com.localqbank.library

import android.os.Build
import android.os.Bundle
import android.os.SharedMemory

/**
 * Bulk IPC transport for payloads that should not be copied through a Binder Parcel.
 * Binder remains the control plane. Large UTF-8 text is placed in SharedMemory and the Bundle
 * carries only the SharedMemory file descriptor plus a small byte-count marker.
 */
private const val BEN_IPC_SHARED_INLINE_THRESHOLD_BYTES = 32 * 1024
private const val BEN_IPC_SHARED_MAX_PAYLOAD_BYTES = 8 * 1024 * 1024
private const val BEN_IPC_SHARED_MEMORY_SUFFIX = ".shm"
private const val BEN_IPC_SHARED_SIZE_SUFFIX = ".size"

fun benIpcPutText(bundle: Bundle, key: String, text: String): SharedMemory? {
    val bytes = text.toByteArray(Charsets.UTF_8)
    if (Build.VERSION.SDK_INT < 27 || bytes.size <= BEN_IPC_SHARED_INLINE_THRESHOLD_BYTES) {
        bundle.putString(key, text)
        return null
    }
    require(bytes.size <= BEN_IPC_SHARED_MAX_PAYLOAD_BYTES) { "Shared IPC payload too large: ${bytes.size}" }
    val memory = SharedMemory.create("BenIpc$key", bytes.size)
    val mapped = memory.mapReadWrite()
    try {
        mapped.put(bytes)
        mapped.rewind()
        try {
            memory.setProtect(android.system.OsConstants.PROT_READ)
        } catch (_: Exception) {
            // Read-only mapping is still enforced on the receiver; protection is an extra hardening layer.
        }
    } catch (e: Exception) {
        try { SharedMemory.unmap(mapped) } catch (_: Exception) { }
        try { memory.close() } catch (_: Exception) { }
        throw e
    }
    SharedMemory.unmap(mapped)
    bundle.remove(key)
    bundle.putParcelable(key + BEN_IPC_SHARED_MEMORY_SUFFIX, memory)
    bundle.putInt(key + BEN_IPC_SHARED_SIZE_SUFFIX, bytes.size)
    return memory
}

fun benIpcGetText(bundle: Bundle, key: String): String {
    val memory = if (Build.VERSION.SDK_INT >= 33) {
        bundle.getParcelable(key + BEN_IPC_SHARED_MEMORY_SUFFIX, SharedMemory::class.java)
    } else {
        @Suppress("DEPRECATION")
        bundle.getParcelable(key + BEN_IPC_SHARED_MEMORY_SUFFIX) as? SharedMemory
    }
    if (memory == null) return bundle.getString(key).orEmpty()
    val size = bundle.getInt(key + BEN_IPC_SHARED_SIZE_SUFFIX, memory.getSize())
    require(size in 0..memory.getSize()) { "Invalid shared IPC payload size: $size" }
    val mapped = memory.mapReadOnly()
    return try {
        val bytes = ByteArray(size)
        mapped.get(bytes)
        String(bytes, Charsets.UTF_8)
    } finally {
        try { SharedMemory.unmap(mapped) } catch (_: Exception) { }
        try { memory.close() } catch (_: Exception) { }
    }
}

fun benIpcHasSharedText(bundle: Bundle, key: String): Boolean =
    Build.VERSION.SDK_INT >= 27 && bundle.containsKey(key + BEN_IPC_SHARED_MEMORY_SUFFIX)

fun benIpcCloseSharedMemory(memory: SharedMemory?) {
    if (memory == null) return
    try { memory.close() } catch (_: Exception) { }
}
