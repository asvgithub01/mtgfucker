package io.asv.mtgocr.ocrreader

import android.content.Context
import android.net.Uri
import io.asv.mtgocr.ocrreader.data.CardEditionOption
import java.io.File
import java.io.Serializable
import java.util.UUID

data class PhotoScanEntry(
    val id: String = UUID.randomUUID().toString(),
    val imagePath: String,
    val createdAt: Long = System.currentTimeMillis(),
    var state: String = STATE_ANALYZING,
    var error: String = "",
    val cards: MutableList<PhotoScanCard> = mutableListOf()
) : Serializable {
    fun totalPrice(): Double = cards.sumOf { (it.price ?: 0.0) * it.detectedQuantity }

    companion object {
        const val STATE_ANALYZING = "analyzing"
        const val STATE_READY = "ready"
        const val STATE_ERROR = "error"
        private const val serialVersionUID = 1L
    }
}

data class PhotoScanCard(
    val id: String = UUID.randomUUID().toString(),
    var cardName: String,
    var displayName: String,
    var detectedQuantity: Int,
    var printingUuid: String,
    var setCode: String,
    var setName: String,
    var collectorNumber: String,
    var finish: String,
    var imageUrl: String,
    var typeLine: String,
    var rulesText: String,
    var price: Double?,
    var currency: String,
    var languageCode: String = ""
) : Serializable {
    fun toEditionOption(): CardEditionOption = CardEditionOption(
        printingUuid = printingUuid,
        cardName = cardName,
        displayName = displayName,
        setCode = setCode,
        setName = setName,
        collectorNumber = collectorNumber,
        releaseDate = "",
        rarity = "",
        finish = finish,
        isFoil = CardFinish.isFoil(finish),
        imageUrl = imageUrl,
        typeLine = typeLine,
        rulesText = rulesText,
        price = price,
        currency = currency,
        priceProvider = null,
        priceDate = null
    )

    companion object {
        private const val serialVersionUID = 1L
    }
}

object PhotoScanStore {
    private const val FILE_NAME = "photoScans.bin"

    @Synchronized
    fun entries(context: Context): MutableList<PhotoScanEntry> {
        if (!File(context.filesDir, FILE_NAME).isFile) return mutableListOf()
        return DataUtils.readSerializable<ArrayList<PhotoScanEntry>>(context, FILE_NAME)?.toMutableList()
            ?: mutableListOf()
    }

    @Synchronized
    fun save(context: Context, entry: PhotoScanEntry) {
        val entries = entries(context)
        val index = entries.indexOfFirst { it.id == entry.id }
        if (index >= 0) entries[index] = entry else entries.add(0, entry)
        DataUtils.saveSerializable(context, ArrayList(entries), FILE_NAME)
    }

    @Synchronized
    fun find(context: Context, id: String): PhotoScanEntry? = entries(context).firstOrNull { it.id == id }

    @Synchronized
    fun delete(context: Context, entry: PhotoScanEntry) {
        val entries = entries(context).filterNot { it.id == entry.id }
        DataUtils.saveSerializable(context, ArrayList(entries), FILE_NAME)
        runCatching { File(entry.imagePath).delete() }
    }

    fun importPhoto(context: Context, uri: Uri): PhotoScanEntry {
        val directory = File(context.filesDir, "photo_scans").apply { mkdirs() }
        val destination = File(directory, "${System.currentTimeMillis()}-${UUID.randomUUID()}.image")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "No se pudo abrir la imagen" }
            destination.outputStream().buffered().use(input::copyTo)
        }
        return PhotoScanEntry(imagePath = destination.absolutePath).also { save(context, it) }
    }
}
