package io.asv.mtgocr.ocrreader

import io.asv.mtgocr.ocrreader.data.CardEditionOption

object ReviewProposalCycle {
    fun preferredOcr(options: List<CardEditionOption>, artUuid: String?, savedUuid: String?): CardEditionOption? {
        val nonfoil=options.filter { it.finish=="nonfoil" }
        val choices=nonfoil.ifEmpty { options }
        return choices.firstOrNull { it.printingUuid==artUuid }
            ?: choices.firstOrNull { it.printingUuid==savedUuid } ?: choices.firstOrNull()
    }

    fun next(current: String, sources: List<String>): String =
        sources.getOrNull(if(current.isEmpty()) 0 else sources.indexOf(current)+1).orEmpty()

    fun proposals(ocr: CardEditionOption?, art: CardEditionOption?): List<Pair<String,CardEditionOption>> =
        if(ocr!=null && art!=null && ocr.printingUuid==art.printingUuid && ocr.finish==art.finish) listOf("OCR/arte" to art)
        else listOfNotNull(ocr?.let { "OCR" to it },art?.let { "arte" to it })
}
