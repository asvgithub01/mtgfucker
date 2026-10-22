package io.asv.mtgocr.ocrreader.domain.photos

/** Read projections only: never serialize these back into the legacy stores. */
data class PhotoRecord(val id: String, val title: String, val path: String?, val details: String)
data class PhotoSection(val records: List<PhotoRecord> = emptyList(), val failed: Boolean = false)
data class PhotoSnapshot(val evidence: PhotoSection, val imports: PhotoSection)
fun interface PhotoReader { suspend fun read(libraryId: String): PhotoSnapshot }
