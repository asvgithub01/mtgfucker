package io.asv.mtgocr.ocrreader.domain.catalog

data class SetSummary(val code: String, val name: String, val releaseDate: String, val cardCount: Int)

fun interface SetCatalogReader {
    suspend fun read(): List<SetSummary>
}
