package io.asv.mtgocr.ocrreader

import android.content.Context
import android.util.Log
import java.io.File
import java.util.UUID

/** Keeps a small, private set of raw scanner photos for this branch's art-hash trial. */
internal object ArtHashSampleStore {
    private const val TAG = "ArtHashSampleStore"
    private val BRANCHES = setOf(
        "codex/art-hash-identification",
        "feature/improbe-hash-scanner"
    )
    private const val DIRECTORY = "art_hash_samples"
    private const val MAX_SAMPLES = 30

    fun retain(context: Context, source: File) {
        if (!BuildConfig.DEBUG || BuildConfig.GIT_BRANCH !in BRANCHES || !source.isFile) return
        runCatching {
            val directory = File(context.filesDir, DIRECTORY)
            check(directory.isDirectory || directory.mkdirs()) { "No se pudo crear $directory" }
            val destination = File(directory, "${System.currentTimeMillis()}-${UUID.randomUUID()}.jpg")
            val temporary = File(directory, "${destination.name}.part")
            try {
                source.copyTo(temporary, overwrite = true)
                check(temporary.length() > 0L) { "Captura vacía" }
                check(temporary.renameTo(destination)) { "No se pudo finalizar $destination" }
            } finally {
                temporary.delete()
            }
            prune(directory)
        }.onFailure { Log.w(TAG, "No se pudo conservar la captura de prueba", it) }
    }

    internal fun prune(directory: File) {
        directory.listFiles { file -> file.isFile && file.extension == "jpg" }
            ?.sortedByDescending { it.name }
            ?.drop(MAX_SAMPLES)
            ?.forEach { it.delete() }
    }
}
