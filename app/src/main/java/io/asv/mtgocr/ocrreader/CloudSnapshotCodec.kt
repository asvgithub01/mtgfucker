package io.asv.mtgocr.ocrreader

import io.asv.mtgocr.ocrreader.model.Biblio
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** Versioned, lossless representation of the legacy collection model stored in Firestore. */
internal object CloudSnapshotCodec {
    const val SCHEMA_VERSION = 1
    const val CHUNK_BYTES = 450_000

    fun encode(collection: Biblio): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { gzip ->
            ObjectOutputStream(gzip).use { objects ->
                objects.writeObject(collection.snapshotForPersistence())
            }
        }
        return output.toByteArray()
    }

    fun decode(bytes: ByteArray): Biblio =
        GZIPInputStream(ByteArrayInputStream(bytes)).use { gzip ->
            ObjectInputStream(gzip).use { objects ->
                (objects.readObject() as? Biblio)
                    ?: throw IllegalArgumentException("La copia de seguridad no contiene una Biblio")
            }
        }

    fun chunks(bytes: ByteArray, size: Int = CHUNK_BYTES): List<ByteArray> {
        require(size > 0)
        if (bytes.isEmpty()) return listOf(ByteArray(0))
        val result = ArrayList<ByteArray>((bytes.size + size - 1) / size)
        var start = 0
        while (start < bytes.size) {
            val end = (start + size).coerceAtMost(bytes.size)
            result += bytes.copyOfRange(start, end)
            start = end
        }
        return result
    }

    fun join(chunks: List<ByteArray>): ByteArray {
        val output = ByteArrayOutputStream(chunks.sumOf { it.size })
        chunks.forEach(output::write)
        return output.toByteArray()
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    /** Merges a first-use local collection with an older cloud backup without losing either. */
    fun merge(remote: Biblio, local: Biblio, targetFile: String, targetName: String): Biblio {
        val merged = Biblio(targetFile, targetName)
        val cards = linkedMapOf<String, io.asv.mtgocr.ocrreader.model.CardInfo>()
        remote.cards.orEmpty().filterNotNull().forEach { cards[it.collectionItemId] = it }
        // Local wins for the same stable row id because it is the copy currently visible to the user.
        local.cards.orEmpty().filterNotNull().forEach { cards[it.collectionItemId] = it }
        cards.values.forEach { merged.addCard(it.snapshotForPersistence()) }
        return merged
    }
}
