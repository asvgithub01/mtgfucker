package io.asv.mtgocr.ocrreader.data.legacy

import io.asv.mtgocr.ocrreader.domain.photos.PhotoRecord

fun evidenceRecords(id: String, title: String, metadata: List<String>, photos: List<String>,
    resolve: (String) -> String?): List<PhotoRecord> = (0 until maxOf(metadata.size, photos.size)).reversed().map { index ->
    PhotoRecord("$id:$index", "$title \u00b7 ${index + 1}", photos.getOrNull(index)?.let(resolve),
        metadata.getOrNull(index).orEmpty())
}
