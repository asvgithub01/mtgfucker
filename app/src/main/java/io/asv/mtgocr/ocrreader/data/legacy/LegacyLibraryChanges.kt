package io.asv.mtgocr.ocrreader.data.legacy

import android.os.FileObserver
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * Watch the directory, not an inode: DataUtils publishes by rename.
 * Captures writes arriving after RESUMED (OCR shutdown does not drain its executor).
 */
internal fun legacyLibraryChanges(directory: File, names: Set<String>): Flow<Unit> =
    observedLibraryChanges(AndroidLibraryDirectoryObserver(directory), names)

private class AndroidLibraryDirectoryObserver(private val directory: File) : LibraryDirectoryObserver {
    override fun watch(onEvent: (String?) -> Unit): () -> Unit {
        @Suppress("DEPRECATION")
        val observer = object : FileObserver(directory.absolutePath, CLOSE_WRITE or MOVED_TO or DELETE or MOVED_FROM) {
            override fun onEvent(event: Int, path: String?) { onEvent(path) }
        }
        observer.startWatching()
        return { observer.stopWatching() }
    }
}
