package io.asv.mtgocr.ocrreader.data.legacy

import io.asv.mtgocr.ocrreader.domain.photos.PhotoReader
import io.asv.mtgocr.ocrreader.domain.photos.PhotoSection
import io.asv.mtgocr.ocrreader.domain.photos.PhotoSnapshot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Read-only projections, never persisted DTOs or processing commands. Imports are global. */
interface PhotoReadSource {
    fun evidence(libraryId: String): PhotoSection
    fun imports(): PhotoSection
}

class ReadOnlyPhotoReader(
    private val source: PhotoReadSource,
    private val io: CoroutineDispatcher
) : PhotoReader {
    override suspend fun read(libraryId: String): PhotoSnapshot = withContext(io) {
        PhotoSnapshot(safeSection { source.evidence(libraryId) }, safeSection { source.imports() })
    }

    // Preserve the legacy independent-section failure policy and call order.
    private fun safeSection(read: () -> PhotoSection): PhotoSection = try { read() }
    catch (_: Exception) { PhotoSection(failed = true) }
}
