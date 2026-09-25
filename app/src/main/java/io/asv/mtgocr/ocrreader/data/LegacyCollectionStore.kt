package io.asv.mtgocr.ocrreader.data

import android.content.Context
import io.asv.mtgocr.ocrreader.DataUtils
import io.asv.mtgocr.ocrreader.LibraryCatalog
import io.asv.mtgocr.ocrreader.OcrCaptureActivity
import io.asv.mtgocr.ocrreader.model.Biblio
import io.asv.mtgocr.ocrreader.model.CardInfo
import io.asv.mtgocr.ocrreader.model.CardCondition
import java.io.File
import java.util.Locale
import java.util.UUID

object LegacyCollectionStore {
    data class ScanCaptureEvidence(
        val metadataJson: String,
        val photoJpeg: ByteArray
    )

    data class CardmarketPrintingMetadata(
        val mcmId: String,
        val mcmMetaId: String? = null,
        val mcmSetId: Int? = null,
        val mcmSetIdExtras: Int? = null,
        val mcmSetName: String? = null
    )

    private fun fileName(context: Context) = LibraryCatalog.activeFile(context)

    /** Returns the persisted cards so catalog screens use the collection file as source of truth. */
    fun cards(context: Context): List<CardInfo> =
        DataUtils.readSerializable<Biblio>(context, fileName(context))?.cards?.toList()
            ?: activeInMemory(context)?.cards?.toList()
            ?: emptyList()

    fun add(context: Context, options: List<SetCardOption>): List<CardInfo> {
        val activeLibrary = LibraryCatalog.active(context)
        val collection = DataUtils.readSerializable<Biblio>(context, activeLibrary.fileName)
            ?: Biblio(activeLibrary.fileName, activeLibrary.name)
        val baseTime = System.currentTimeMillis()
        val added = options.mapIndexed { index, option ->
            val priceText = option.price?.let { "%.2f %s".format(Locale.US, it, option.currency.orEmpty()) }.orEmpty()
            CardInfo(
                option.cardName,
                priceText,
                listOf(option.typeLine, option.rulesText).filter { it.isNotBlank() }.joinToString("\n"),
                option.imageUrl.orEmpty(),
                ""
            ).also { card ->
                card.addedAt = baseTime + index
                card.printingUuid = option.printingUuid
                card.mcmId = option.mcmId
                card.mcmMetaId = option.mcmMetaId
                card.mcmSetId = option.mcmSetId
                card.mcmSetIdExtras = option.mcmSetIdExtras
                card.mcmSetName = option.mcmSetName
                card.setCode = option.setCode
                card.setName = option.setName
                card.collectorNumber = option.collectorNumber
                card.finish = option.finish
                option.price?.let {
                    card.priceL = it.toString()
                    card.priceM = it.toString()
                    card.priceH = it.toString()
                }
                collection.addCard(card)
            }
        }
        DataUtils.saveSerializable(context, collection, collection.nameFile)
        OcrCaptureActivity.mBiblio = collection
        return added
    }

    /** Adds physical copies in one save, folding into the matching name/printing/finish row. */
    fun addCopy(
        context: Context,
        option: CardEditionOption,
        languageCode: String = "",
        scanEvidence: ScanCaptureEvidence? = null,
        copies: Int = 1,
        preferredCollectionItemId: String? = null,
        groupName: String? = null,
        expectedLibraryFile: String? = null
    ): CardInfo {
        require(copies in 1..99)
        val activeLibrary = LibraryCatalog.active(context)
        check(expectedLibraryFile == null || activeLibrary.fileName == expectedLibraryFile) { "La biblioteca activa ha cambiado" }
        val collection = DataUtils.readSerializable<Biblio>(context, activeLibrary.fileName)
            ?: Biblio(activeLibrary.fileName, activeLibrary.name)
        val result = if (groupName == null) addCopiesToCollection(collection, option, languageCode, copies, preferredCollectionItemId)
            else {
                require(groupName.isNotBlank() && preferredCollectionItemId == null)
                var added: CardInfo? = null
                repeat(copies) { added = addCopyToCollection(collection, option, languageCode, groupName) }
                requireNotNull(added)
            }
        val evidenceFiles = ArrayList<File>()
        val expectedQuantity = result.quantityCount
        try {
            if (scanEvidence != null) repeat(copies) {
                val file = saveScanPhoto(context, result.collectionItemId, scanEvidence.photoJpeg)
                evidenceFiles += file
                result.addScanEvidence(scanEvidence.metadataJson,
                    file.relativeTo(context.filesDir).invariantSeparatorsPath)
            }
            DataUtils.saveSerializable(context, collection, collection.nameFile)
        } catch (error: Throwable) {
            evidenceFiles.forEach { it.delete() }
            throw error
        }

        // Re-read the file before reporting success. This also makes the object exposed to the
        // still-running collection Activity exactly the same object that will survive a restart.
        val persisted = DataUtils.readSerializable<Biblio>(context, collection.nameFile) ?: run {
            evidenceFiles.forEach { it.delete() }
            throw IllegalStateException("No se pudo guardar la copia en la colección")
        }
        OcrCaptureActivity.mBiblio = persisted
        val saved = persisted.cards.firstOrNull { it.collectionItemId == result.collectionItemId } ?: run {
            evidenceFiles.forEach { it.delete() }
            throw IllegalStateException("La copia guardada no se pudo verificar")
        }
        if (saved.quantityCount < expectedQuantity) {
            evidenceFiles.forEach { it.delete() }
            throw IllegalStateException("La cantidad guardada no se pudo verificar")
        }
        if (scanEvidence != null && saved.scanMetadataHistory.lastOrNull() != scanEvidence.metadataJson) {
            evidenceFiles.forEach { it.delete() }
            throw IllegalStateException("Los datos del escaneo no se pudieron verificar")
        }
        return saved
    }

    private fun saveScanPhoto(context: Context, collectionItemId: String, jpeg: ByteArray): File {
        require(jpeg.isNotEmpty()) { "La foto del escaneo está vacía" }
        val directory = File(context.filesDir, "scan_evidence/$collectionItemId").apply { mkdirs() }
        val file = File(directory, "${System.currentTimeMillis()}-${UUID.randomUUID()}.jpg")
        file.writeBytes(jpeg)
        return file
    }

    data class BatchEditionResult(val card: CardInfo, val originalRemaining: CardInfo?)

    /** Correct only this consecutive batch, never older copies merged into the original row. */
    internal fun correctBatchInCollection(collection: Biblio, itemId: String, original: CardEditionOption,
        selected: CardEditionOption, saved: Int, total: Int): BatchEditionResult {
        require(saved > 0 && total in saved..99)
        val source = collection.cards.firstOrNull { it.collectionItemId == itemId } ?: error("El lote ya no existe")
        check(samePrinting(source, original) && source.quantityCount >= saved)
        val remaining = source.quantityCount - saved
        val target = if (remaining == 0) source else source.copyForNewCollectionItem().also { copy ->
            val count = minOf(saved, source.scanMetadataHistory.size, source.scanPhotoPaths.size)
            copy.scanMetadataHistory.clear()
            copy.scanPhotoPaths.clear()
            copy.scanMetadataHistory.addAll(source.scanMetadataHistory.takeLast(count))
            copy.scanPhotoPaths.addAll(source.scanPhotoPaths.takeLast(count))
            repeat(count) { source.removeLastScanEvidence() } // Move references; do not delete photo files.
            source.quantityCount = remaining
            collection.addCard(copy)
        }
        updateEditionInCollection(collection, target.collectionItemId, selected)
        target.quantityCount = total
        return BatchEditionResult(target, source.takeIf { remaining > 0 })
    }

    fun correctScanBatch(context: Context, itemId: String, original: CardEditionOption,
        selected: CardEditionOption, saved: Int, total: Int, evidence: ScanCaptureEvidence?): BatchEditionResult {
        val collection = DataUtils.readSerializable<Biblio>(context, fileName(context))
            ?: activeInMemory(context)?.snapshotForPersistence() ?: error("La colección no está disponible")
        val result = correctBatchInCollection(collection, itemId, original, selected, saved, total)
        val files = ArrayList<File>()
        try {
            if (evidence != null) repeat(total - saved) {
                val file = saveScanPhoto(context, result.card.collectionItemId, evidence.photoJpeg)
                files += file
                result.card.addScanEvidence(evidence.metadataJson, file.relativeTo(context.filesDir).invariantSeparatorsPath)
            }
            DataUtils.saveSerializable(context, collection, collection.nameFile)
            val persisted = DataUtils.readSerializable<Biblio>(context, collection.nameFile) ?: error("No se pudo verificar el lote")
            val card = persisted.cards.firstOrNull { it.collectionItemId == result.card.collectionItemId }
                ?: error("El lote guardado no se encuentra")
            check(samePrinting(card, selected) && card.quantityCount == total &&
                card.scanMetadataHistory == result.card.scanMetadataHistory && card.scanPhotoPaths == result.card.scanPhotoPaths)
            val remaining = result.originalRemaining?.let { old ->
                persisted.cards.firstOrNull { it.collectionItemId == itemId && it.quantityCount == old.quantityCount && samePrinting(it, original) }
                    ?: error("No se pudieron verificar las copias anteriores")
            }
            OcrCaptureActivity.mBiblio = persisted
            return BatchEditionResult(card, remaining)
        } catch (error: Throwable) {
            files.forEach { it.delete() }
            throw error
        }
    }

    internal fun addCopiesToCollection(collection: Biblio, option: CardEditionOption,
        languageCode: String, copies: Int, preferredCollectionItemId: String? = null): CardInfo {
        require(copies in 1..99)
        if (preferredCollectionItemId != null) {
            val target = collection.cards.firstOrNull { it.collectionItemId == preferredCollectionItemId }
                ?: error("El lote ya no existe")
            check(samePrinting(target, option) && CardLanguage.toCode(target.languageCode) == CardLanguage.toCode(languageCode))
            return target.also { it.quantityCount += copies }
        }
        val result = addCopyToCollection(collection, option, languageCode)
        repeat(copies - 1) { addCopyToCollection(collection, option, languageCode) }
        return result
    }

    internal fun addCopyToCollection(
        collection: Biblio,
        option: CardEditionOption,
        languageCode: String = "",
        groupName: String? = null
    ): CardInfo {
        val normalizedLanguage = CardLanguage.toCode(languageCode)
        val existing = collection.cards.firstOrNull { card ->
            samePrinting(card, option) &&
                card.condition == CardCondition.NEAR_MINT &&
                (groupName == null || (card.groups == listOf(groupName) && card.decks.isEmpty() &&
                    CardLanguage.toCode(card.languageCode) == normalizedLanguage)) &&
                (normalizedLanguage.isBlank() || CardLanguage.toCode(card.languageCode) == normalizedLanguage)
        }
        val result = existing?.also { it.quantityCount = it.quantityCount + 1 } ?: CardInfo(
            option.cardName,
            option.price?.let { "%.2f %s".format(Locale.US, it, option.currency.orEmpty()) }.orEmpty(),
            listOf(option.typeLine, option.rulesText).filter { it.isNotBlank() }.joinToString("\n"),
            option.imageUrl.orEmpty(),
            "1"
        ).also { card ->
            card.printingUuid = option.printingUuid
            card.mcmId = option.mcmId
            card.mcmMetaId = option.mcmMetaId
            card.mcmSetId = option.mcmSetId
            card.mcmSetIdExtras = option.mcmSetIdExtras
            card.mcmSetName = option.mcmSetName
            card.setCode = option.setCode
            card.setName = option.setName
            card.collectorNumber = option.collectorNumber
            card.finish = option.finish
            card.languageCode = normalizedLanguage
            groupName?.let { card.addGroup(it) }
            option.price?.let {
                card.priceL = it.toString()
                card.priceM = it.toString()
                card.priceH = it.toString()
            }
            collection.addCard(card)
        }
        return result
    }

    /** MTGJSON calls a regular finish `nonfoil`; old collection files may call it `normal`. */
    internal fun samePrinting(card: CardInfo, option: CardEditionOption): Boolean =
        card.printingUuid.orEmpty() == option.printingUuid &&
            normalizeFinish(card.finish) == normalizeFinish(option.finish)

    /** Applies exact-printing Cardmarket metadata without changing user-owned card fields. */
    internal fun enrichCardmarketIdentifiers(
        collection: Biblio,
        metadataByPrinting: Map<String, CardmarketPrintingMetadata>
    ): Int {
        var changed = 0
        collection.cards.orEmpty().filterNotNull().forEach { card ->
            val metadata = metadataByPrinting[card.printingUuid.orEmpty()] ?: return@forEach
            var cardChanged = false
            if (card.mcmId.isNullOrBlank() && metadata.mcmId.isNotBlank()) {
                card.mcmId = metadata.mcmId
                cardChanged = true
            }
            if (card.mcmMetaId.isNullOrBlank() && !metadata.mcmMetaId.isNullOrBlank()) {
                card.mcmMetaId = metadata.mcmMetaId
                cardChanged = true
            }
            if (card.mcmSetId == null && metadata.mcmSetId != null) {
                card.mcmSetId = metadata.mcmSetId
                cardChanged = true
            }
            if (card.mcmSetIdExtras == null && metadata.mcmSetIdExtras != null) {
                card.mcmSetIdExtras = metadata.mcmSetIdExtras
                cardChanged = true
            }
            if (card.mcmSetName.isNullOrBlank() && !metadata.mcmSetName.isNullOrBlank()) {
                card.mcmSetName = metadata.mcmSetName
                cardChanged = true
            }
            if (cardChanged) changed++
        }
        return changed
    }

    private fun normalizeFinish(value: String?): String = when (value.orEmpty().trim().lowercase(Locale.ROOT)) {
        "", "normal", "regular", "non-foil" -> "nonfoil"
        else -> value.orEmpty().trim().lowercase(Locale.ROOT)
    }

    data class CopyRemovalResult(val remainingQuantity: Int, val removedCollectionItemId: String)

    /** Removes one physical copy of an exact printing/finish, deleting its row at zero. */
    fun removeCopy(
        context: Context,
        option: CardEditionOption,
        preferredCollectionItemId: String? = null,
        removeLatestScanEvidence: Boolean = false
    ): CopyRemovalResult? {
        val collection = DataUtils.readSerializable<Biblio>(context, fileName(context))
            ?: activeInMemory(context)
            ?: return null
        val card = collection.cards.firstOrNull {
            it.collectionItemId == preferredCollectionItemId && samePrinting(it, option)
        } ?: collection.cards.firstOrNull { samePrinting(it, option) } ?: return null
        val remaining = card.quantityCount - 1
        val removedEvidencePaths = when {
            remaining <= 0 -> card.scanPhotoPaths.toList()
            removeLatestScanEvidence -> listOf(card.removeLastScanEvidence())
            else -> emptyList()
        }
        if (remaining > 0) card.quantityCount = remaining else collection.cards.remove(card)
        DataUtils.saveSerializable(context, collection, collection.nameFile)
        removedEvidencePaths.filter(String::isNotBlank).forEach { relative ->
            File(context.filesDir, relative).delete()
        }
        OcrCaptureActivity.mBiblio = collection
        return CopyRemovalResult(remaining.coerceAtLeast(0), card.collectionItemId)
    }

    fun updateSelectedEdition(
        context: Context,
        collectionItemId: String,
        option: CardEditionOption
    ): Boolean {
        val collection = DataUtils.readSerializable<Biblio>(context, fileName(context))
            ?: activeInMemory(context)
            ?: return false
        if (updateEditionInCollection(collection, collectionItemId, option) == null) return false
        DataUtils.saveSerializable(context, collection, collection.nameFile)
        OcrCaptureActivity.mBiblio = collection
        return true
    }

    internal fun updateEditionInCollection(collection: Biblio, collectionItemId: String, option: CardEditionOption): CardInfo? {
        val card = collection.cards.firstOrNull { it.collectionItemId == collectionItemId } ?: return null
        card.printingUuid = option.printingUuid
        card.mcmId = option.mcmId
        card.mcmMetaId = option.mcmMetaId
        card.mcmSetId = option.mcmSetId
        card.mcmSetIdExtras = option.mcmSetIdExtras
        card.mcmSetName = option.mcmSetName
        card.setCode = option.setCode
        card.setName = option.setName
        card.collectorNumber = option.collectorNumber
        card.finish = option.finish
        card.imgPath = option.imageUrl.orEmpty()
        option.price?.let { amount ->
            val raw = amount.toString()
            card.price = "%.2f %s".format(Locale.US, amount, option.currency.orEmpty()).trim()
            card.priceL = raw
            card.priceM = raw
            card.priceH = raw
        } ?: run {
            card.price = ""
            card.priceL = ""
            card.priceM = ""
            card.priceH = ""
        }
        return card
    }

    fun updateCondition(context: Context, collectionItemId: String, condition: String): CardInfo? {
        val collection = DataUtils.readSerializable<Biblio>(context, fileName(context))
            ?: activeInMemory(context)
            ?: return null
        val card = collection.cards.firstOrNull { it.collectionItemId == collectionItemId } ?: return null
        card.condition = condition
        DataUtils.saveSerializable(context, collection, collection.nameFile)
        OcrCaptureActivity.mBiblio = collection
        return card
    }

    fun updateLanguageVariant(
        context: Context,
        collectionItemId: String,
        languageCode: String,
        imageUrl: String
    ): CardInfo? {
        val collection = DataUtils.readSerializable<Biblio>(context, fileName(context))
            ?: activeInMemory(context)
            ?: return null
        val card = collection.cards.firstOrNull { it.collectionItemId == collectionItemId } ?: return null
        card.languageCode = CardLanguage.toCode(languageCode)
        card.imgPath = imageUrl
        DataUtils.saveSerializable(context, collection, collection.nameFile)
        OcrCaptureActivity.mBiblio = collection
        return card
    }

    private fun activeInMemory(context: Context): Biblio? =
        OcrCaptureActivity.mBiblio?.takeIf { it.nameFile == fileName(context) }
}
