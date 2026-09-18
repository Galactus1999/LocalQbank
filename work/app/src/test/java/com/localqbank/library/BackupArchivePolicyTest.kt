package com.localqbank.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupArchivePolicyTest {
    @Test
    fun acceptsNormalBackupBounds() {
        BackupArchivePolicy.validateEntryCount(1)
        BackupArchivePolicy.validateEntryPath("database/qbank.db")
        BackupArchivePolicy.validateEntrySize(1024L)
        BackupArchivePolicy.validateTotalSize(1024L)
        assertEquals(4096, BackupArchivePolicy.MAX_ENTRIES)
    }

    @Test
    fun rejectsZipBombEntryCount() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupArchivePolicy.validateEntryCount(BackupArchivePolicy.MAX_ENTRIES + 1)
        }
    }

    @Test
    fun rejectsOversizedEntry() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupArchivePolicy.validateEntrySize(BackupArchivePolicy.MAX_SINGLE_ENTRY_BYTES + 1)
        }
    }

    @Test
    fun rejectsOversizedArchive() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupArchivePolicy.validateTotalSize(BackupArchivePolicy.MAX_TOTAL_UNCOMPRESSED_BYTES + 1)
        }
    }

    @Test
    fun rejectsOverlongPath() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupArchivePolicy.validateEntryPath("x".repeat(BackupArchivePolicy.MAX_PATH_LENGTH + 1))
        }
    }
}
