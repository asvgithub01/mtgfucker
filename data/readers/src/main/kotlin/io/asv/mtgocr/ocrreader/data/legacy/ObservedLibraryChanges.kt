package io.asv.mtgocr.ocrreader.data.legacy

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*

/** Observe publications only. Each registration returns its own stop action. */
fun interface LibraryDirectoryObserver {
    fun watch(onEvent: (String?) -> Unit): () -> Unit
}

/** Cold flow: one observer per collector; no reads, writes or processing jobs. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
fun observedLibraryChanges(source: LibraryDirectoryObserver, names: Set<String>): Flow<Unit> = callbackFlow {
    val stop = source.watch { path -> if (path in names) trySend(Unit) }
    // Closes the registration gap even if a publication preceded registration.
    trySend(Unit)
    awaitClose { stop() }
}.buffer(Channel.CONFLATED).debounce(150)
