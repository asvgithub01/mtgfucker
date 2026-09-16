package io.asv.mtgocr.ocrreader

import io.asv.mtgocr.ocrreader.model.Biblio
import com.squareup.moshi.Moshi
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

/** Browser-readable projection stored beside the lossless Java backup. */
internal object CloudWebSnapshotCodec {
    const val SCHEMA_VERSION = 1
    private val jsonAdapter = Moshi.Builder().build().adapter(Any::class.java)

    fun encode(collection: Biblio): ByteArray {
        val cards = collection.cards.orEmpty().filterNotNull().map { card ->
            linkedMapOf<String, Any?>(
                "collectionItemId" to card.collectionItemId,
                "name" to card.name.orEmpty(),
                "quantity" to card.quantityCount,
                "printingUuid" to card.printingUuid.orEmpty(),
                "mcmId" to card.mcmId,
                "mcmMetaId" to card.mcmMetaId,
                "setCode" to card.setCode.orEmpty(),
                "setName" to card.setName.orEmpty(),
                "mcmSetId" to card.mcmSetId,
                "mcmSetIdExtras" to card.mcmSetIdExtras,
                "mcmSetName" to card.mcmSetName,
                "collectorNumber" to card.collectorNumber.orEmpty(),
                "finish" to card.finish.orEmpty(),
                "language" to card.languageCode,
                "condition" to card.condition,
                "price" to card.basePrice.orEmpty()
            )
        }
        val json = jsonAdapter.toJson(linkedMapOf(
            "schemaVersion" to SCHEMA_VERSION,
            "name" to collection.name.orEmpty(),
            "fileName" to collection.nameFile.orEmpty(),
            "cards" to cards
        )).toByteArray(Charsets.UTF_8)
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(json) }
        return output.toByteArray()
    }
}
