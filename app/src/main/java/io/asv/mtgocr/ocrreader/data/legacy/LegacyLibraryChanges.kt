package io.asv.mtgocr.ocrreader.data.legacy

import android.os.FileObserver
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import java.io.File

/**
 * Watch the directory, not an inode: DataUtils publishes by rename.
 * Captures writes arriving after RESUMED (OCR shutdown does not drain its executor).
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
internal fun legacyLibraryChanges(directory: File, names: Set<String>): Flow<Unit> = callbackFlow {
    @Suppress("DEPRECATION")
    val observer = object : FileObserver(directory.absolutePath, CLOSE_WRITE or MOVED_TO or DELETE or MOVED_FROM) {
        override fun onEvent(event: Int, path: String?) {
            if (path in names) trySend(Unit)
        }
    }
    observer.startWatching()
    // Closes the registration gap even if a publication preceded startWatching.
    trySend(Unit)
    awaitClose { observer.stopWatching() }
}.buffer(Channel.CONFLATED).debounce(150)
