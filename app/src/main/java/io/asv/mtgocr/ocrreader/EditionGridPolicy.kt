package io.asv.mtgocr.ocrreader

import io.asv.mtgocr.ocrreader.data.CardEditionOption

/** Session corrections use every edition of the card, not just the last scanner candidates. */
internal object EditionGridPolicy {
    fun choices(options: List<CardEditionOption>, set: String, preferredFinish: String): List<CardEditionOption> {
        val finish = preferredFinish.takeUnless { it.isBlank() || it.equals("normal", true) } ?: "nonfoil"
        val candidates = options.filter { it.setCode.equals(set, true) }
        return candidates.filter { it.finish.equals(finish, true) }.ifEmpty { candidates }
            .distinctBy { it.printingUuid to it.finish }
    }

    fun sets(options: List<CardEditionOption>): List<ArtPrintingIndex.SetInfo> =
        options.sortedByDescending { it.releaseDate }.distinctBy { it.setCode.uppercase(java.util.Locale.ROOT) }
            .map { ArtPrintingIndex.SetInfo(it.setCode, it.setName, it.releaseDate, "", null, null) }
}
