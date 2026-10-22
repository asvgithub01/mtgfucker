package io.asv.mtgocr.ocrreader.data.legacy

import java.io.File

internal object PhotoPathPolicy {
    fun resolve(filesDir: File, path: String, imported: Boolean): File? = runCatching {
        if (path.isBlank()) return null
        val input = File(path)
        if (input.isAbsolute != imported) return null
        val root = File(filesDir, if (imported) "photo_scans" else "scan_evidence").canonicalFile
        if (!root.path.startsWith(filesDir.canonicalPath + File.separator)) return null
        val file = (if (imported) input else File(filesDir, path)).canonicalFile
        file.takeIf { it.path.startsWith(root.path + File.separator) && it.isFile }
    }.getOrNull()
}
