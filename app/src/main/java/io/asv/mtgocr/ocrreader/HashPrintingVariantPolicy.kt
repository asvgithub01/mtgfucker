package io.asv.mtgocr.ocrreader

import java.util.Locale

/** Narrows reused artwork only with evidence read from this physical card. */
internal object HashPrintingVariantPolicy {
    fun preferred(
        variants: List<ArtPrintingIndex.Variant>,
        indexedSetCode: String,
        indexedCollector: String
    ): ArtPrintingIndex.Variant? = variants.minWithOrNull(
        compareByDescending<ArtPrintingIndex.Variant> {
            it.set.code.equals(indexedSetCode, ignoreCase = true) &&
                PrintingMetadataParser.collectorKeysMatch(it.collectorNumber, indexedCollector)
        }.thenByDescending { it.finishes and ArtPrintingIndex.FINISH_NONFOIL != 0 }
            .thenByDescending { it.set.releaseDate }
            .thenBy { it.printingUuid }
    )

    fun compatible(
        candidateName: String,
        variants: List<ArtPrintingIndex.Variant>,
        ocrNames: List<String>,
        printing: PrintingMetadataGuess?,
        border: CardBorderColor?,
        languageCode: String,
        verifyBorder: Boolean,
        verifyLanguage: Boolean
    ): List<ArtPrintingIndex.Variant> {
        var result = variants.filter { sameCardName(it.cardName, candidateName) }
        val nameConfirmed = ocrNames.any { name ->
            sameCardName(candidateName, name) || result.any { sameCardName(it.cardName, name) }
        }
        if (nameConfirmed && printing?.setCode != null && printing.collectorNumber != null) {
            val code = printing.setCode.uppercase(Locale.ROOT)
            result = result.filter {
                it.set.code.uppercase(Locale.ROOT) == code &&
                    PrintingMetadataParser.collectorKeysMatch(it.collectorNumber, printing.collectorNumber)
            }
        }
        if (verifyBorder && border in setOf(CardBorderColor.BLACK, CardBorderColor.WHITE)) {
            result = result.filter { it.border == border }
        }
        if (verifyLanguage && languageCode.isNotBlank()) {
            result = result.filter { it.languageCode.equals(languageCode, ignoreCase = true) }
        }
        return result.distinctBy { it.printingUuid to it.languageCode }
    }

    fun resolve(
        candidateName: String,
        variants: List<ArtPrintingIndex.Variant>,
        ocrNames: List<String>,
        printing: PrintingMetadataGuess?,
        border: CardBorderColor?,
        languageCode: String,
        verifyBorder: Boolean,
        verifyLanguage: Boolean
    ): ArtPrintingIndex.Variant? = compatible(
        candidateName, variants, ocrNames, printing, border, languageCode,
        verifyBorder, verifyLanguage
    ).singleOrNull()

    private fun sameCardName(first: String, second: String): Boolean {
        if (first.equals(second, ignoreCase = true)) return true
        val firstFaces = first.split(" // ")
        val secondFaces = second.split(" // ")
        return firstFaces.any { face -> secondFaces.any { it.equals(face, ignoreCase = true) } }
    }
}
