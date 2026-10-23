package io.asv.mtgocr.ocrreader.data.legacy

import java.io.File

/** Metadata only: never deserialize, create, repair or remove a library file.
 * The root is resolved on every call, matching the Android source's existing behavior.
 */
class PrivateLibraryFileReader(private val privateRoot: () -> File) {
    fun bytes(fileName: String): Long? {
        val root = privateRoot().canonicalFile
        val file = File(root, fileName).canonicalFile
        require(file.parentFile == root) { "Library file outside private root" }
        return if (file.isFile) file.length() else null
    }
}
