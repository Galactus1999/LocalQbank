package com.localqbank.library

/**
 * Safety limits for extracting Rovex's own full-backup ZIP files.
 *
 * These limits protect restore from malformed/hostile archives without changing the
 * backup format or normal backup contents. They are deliberately generous for
 * legitimate study libraries while bounding ZIP-bomb style expansion.
 */
object BackupArchivePolicy {
    const val MAX_ENTRIES: Int = 4096
    const val MAX_TOTAL_UNCOMPRESSED_BYTES: Long = 8L * 1024L * 1024L * 1024L
    const val MAX_SINGLE_ENTRY_BYTES: Long = 1L * 1024L * 1024L * 1024L
    const val MAX_PATH_LENGTH: Int = 1024

    fun validateEntryPath(path: String) {
        require(path.isNotBlank()) { "Empty backup entry path" }
        require(path.length <= MAX_PATH_LENGTH) { "Backup path too long" }
    }

    fun validateEntrySize(size: Long) {
        require(size >= 0L) { "Negative backup entry size" }
        require(size <= MAX_SINGLE_ENTRY_BYTES) { "Backup entry too large" }
    }

    fun validateEntryCount(count: Int) {
        require(count in 0..MAX_ENTRIES) { "Too many backup entries" }
    }

    fun validateTotalSize(total: Long) {
        require(total >= 0L && total <= MAX_TOTAL_UNCOMPRESSED_BYTES) { "Backup archive too large" }
    }
}
