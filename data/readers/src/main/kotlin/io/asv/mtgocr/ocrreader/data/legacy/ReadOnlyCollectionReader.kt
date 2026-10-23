package io.asv.mtgocr.ocrreader.data.legacy

import io.asv.mtgocr.ocrreader.domain.collection.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.Collections

/** Detached read views only; never pass live models or serialize these projections. */
fun interface CollectionReadSource {
    fun load(libraryId: String): CollectionRowsLoad
}

sealed interface CollectionRowsLoad {
    /** Consumed once, within the read dispatcher. Stop at the first invalid entry. */
    data class Loaded(val rows: Sequence<CollectionRowRead>) : CollectionRowsLoad
    object Missing : CollectionRowsLoad
    data class Failed(val reason: CollectionReadFailure) : CollectionRowsLoad
}

sealed interface CollectionRowRead {
    /** Mapping is deferred until identity is checked, preserving legacy getter order.
     * The source supplies the same row ID and detached, immutable nested lists.
     */
    data class Ready(val id: String, val project: () -> CollectionRow) : CollectionRowRead
    data class Failed(val reason: CollectionReadFailure) : CollectionRowRead
}

class ReadOnlyCollectionReader(
    private val source: CollectionReadSource,
    private val io: CoroutineDispatcher
) : CollectionReader {
    override suspend fun read(libraryId: String): CollectionReadResult = withContext(io) {
        when (val loaded = source.load(libraryId)) {
            CollectionRowsLoad.Missing -> CollectionReadResult.Missing
            is CollectionRowsLoad.Failed -> CollectionReadResult.Failed(loaded.reason)
            is CollectionRowsLoad.Loaded -> project(libraryId, loaded.rows)
        }
    }

    private fun project(libraryId: String, entries: Sequence<CollectionRowRead>): CollectionReadResult {
        val ids = HashSet<String>()
        val rows = ArrayList<CollectionRow>()
        for (entry in entries) {
            when (entry) {
                is CollectionRowRead.Failed -> return CollectionReadResult.Failed(entry.reason)
                is CollectionRowRead.Ready -> {
                    if (!ids.add(entry.id)) {
                        return CollectionReadResult.Failed(CollectionReadFailure.DUPLICATE_IDENTITY)
                    }
                    rows += entry.project()
                }
            }
        }
        return CollectionReadResult.Ready(CollectionSnapshot(libraryId, Collections.unmodifiableList(rows)))
    }
}
