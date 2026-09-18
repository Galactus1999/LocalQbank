package com.localqbank.library

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * Owns only local model artifact storage/validation. It deliberately does not own inference.
 * A future LiteRT backend consumes the validated artifact through BenInferenceBackend.
 */
class BenNeuralModelManager(context: Context) {
    private val app = context.applicationContext
    private val modelDir = File(app.filesDir, "ben_models")

    data class InstalledModel(
        val profile: BenNeuralModelRegistry.ModelProfile,
        val file: File,
        val bytes: Long
    )


    private val embeddingTokenizerFile = File(modelDir, "embeddinggemma_sentencepiece.model")

    fun installedEmbeddingGemmaTokenizer(): File? =
        embeddingTokenizerFile.takeIf { it.isFile && it.length() in 4_000L..(16L * 1024L * 1024L) }

    /** Copies the official EmbeddingGemma SentencePiece tokenizer into app-private storage. */
    fun importEmbeddingGemmaTokenizer(uri: Uri): File? {
        modelDir.mkdirs()
        val temp = File(modelDir, "embeddinggemma_sentencepiece.model.partial")
        var total = 0L
        return runCatching {
            app.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > 16L * 1024L * 1024L) throw IllegalArgumentException("Tokenizer exceeds safe size limit")
                        output.write(buffer, 0, read)
                    }
                    output.fd.sync()
                }
            } ?: return null
            if (total < 4_000L) return null
            if (embeddingTokenizerFile.exists() && !embeddingTokenizerFile.delete()) { temp.delete(); return null }
            if (!temp.renameTo(embeddingTokenizerFile)) { temp.delete(); return null }
            embeddingTokenizerFile
        }.getOrNull().also { if (it == null) temp.delete() }
    }

    fun deleteEmbeddingGemmaTokenizer(): Boolean = embeddingTokenizerFile.delete()

    fun installed(profile: BenNeuralModelRegistry.ModelProfile): InstalledModel? {
        val file = File(modelDir, fileName(profile))
        if (!file.isFile || file.length() <= 0L) return null
        return InstalledModel(profile, file, file.length())
    }

    /**
     * Copies a user-selected local model into app-private storage using bounded streaming I/O.
     * It refuses oversized artifacts and never executes the selected file.
     */
    fun importModel(uri: Uri, profile: BenNeuralModelRegistry.ModelProfile): InstalledModel? {
        val resolver = app.contentResolver
        modelDir.mkdirs()
        val destination = File(modelDir, fileName(profile))
        val temp = File(modelDir, fileName(profile) + ".partial")
        var total = 0L
        val maxBytes = (profile.estimatedModelMb + 64).toLong() * 1024L * 1024L
        return runCatching {
            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > maxBytes) throw IllegalArgumentException("Model artifact exceeds safe size limit")
                        output.write(buffer, 0, read)
                    }
                    output.fd.sync()
                }
            } ?: return null
            if (total <= 0L) return null
            if (!validateArtifact(temp, profile)) {
                temp.delete()
                return null
            }
            if (!temp.renameTo(destination)) {
                temp.delete()
                return null
            }
            InstalledModel(profile, destination, total)
        }.getOrNull().also {
            if (it == null) temp.delete()
        }
    }

    fun delete(profile: BenNeuralModelRegistry.ModelProfile): Boolean =
        File(modelDir, fileName(profile)).delete()

    fun clearAll() {
        modelDir.deleteRecursively()
    }

    private fun validateArtifact(file: File, profile: BenNeuralModelRegistry.ModelProfile): Boolean {
        return runCatching {
            when (profile.format) {
                ".litertlm" -> file.inputStream().use { input ->
                    val header = ByteArray(8)
                    input.read(header) == 8 && header.contentEquals(byteArrayOf(0x4c,0x49,0x54,0x45,0x52,0x54,0x4c,0x4d))
                }
                ".tflite" -> file.inputStream().use { input ->
                    // TFLite models are FlatBuffers: the first 4 bytes are the root-table
                    // offset, followed by the 4-byte FlatBuffer file identifier "TFL3".
                    // Do not expect "TFL3" at byte 0.
                    val header = ByteArray(8)
                    val read = input.read(header)
                    read == 8 &&
                        header.copyOfRange(4, 8).contentEquals(byteArrayOf(0x54, 0x46, 0x4c, 0x33)) &&
                        file.length() > 1024L
                }
                else -> false
            }
        }.getOrDefault(false)
    }

    private fun fileName(profile: BenNeuralModelRegistry.ModelProfile): String =
        profile.id + profile.format
}
