package io.asv.mtgocr.ocrreader.data

import java.io.Reader

/** Release supplement: canonical name, printed name, language; no network on the OCR path. */
internal object BundledOcrNames {
    const val ASSET = "ocr_aliases/release_supplement.tsv"
    fun read(reader: Reader, updatedAt: Long): List<CardNameAliasEntity> =
        reader.buffered().readLines().filter { it.isNotBlank() }.map { line ->
            val fields = line.split('\t')
            require(fields.size == 3 && fields.all { it.isNotBlank() }) { "Invalid bundled OCR alias" }
            CardNameAliasEntity(
                MtgJsonParsers.normalizeSearchName(fields[1]), fields[0], fields[1], fields[2], updatedAt
            )
        }
}
