package io.asv.mtgocr.ocrreader.data.legacy

import android.content.Context
import io.asv.mtgocr.ocrreader.DataUtils
import io.asv.mtgocr.ocrreader.PhotoScanEntry
import io.asv.mtgocr.ocrreader.domain.photos.*
import java.io.File
import java.io.Serializable
import java.util.Collections

/** Detached projections only; retains legacy filenames, backup reads and path validation. */
internal class AndroidLegacyPhotoSource(private val context: Context) : PhotoReadSource {
    override fun evidence(libraryId: String): PhotoSection { return when (val load = AndroidLegacyCollectionSource(context).load(libraryId)) {
        LegacyCollectionLoad.Missing -> PhotoSection()
        is LegacyCollectionLoad.Failed -> PhotoSection(failed = true)
        is LegacyCollectionLoad.Loaded -> {
            val ids = HashSet<String>()
            val records = ArrayList<PhotoRecord>()
            for (card in load.collection.snapshotForPersistence().cards.orEmpty()) {
                if (card == null || card.ensureCollectionItemId() || !ids.add(card.collectionItemId))
                    return PhotoSection(failed = true)
                records += evidenceRecords(card.collectionItemId, card.name.orEmpty(),
                    card.scanMetadataHistory, card.scanPhotoPaths) { path ->
                    PhotoPathPolicy.resolve(context.filesDir, path, false)?.absolutePath
                }
            }
            PhotoSection(Collections.unmodifiableList(records))
        }
    }
    }
    override fun imports(): PhotoSection {
        val name = "photoScans.bin"
        if (!File(context.filesDir, name).exists() && !File(context.filesDir, "$name.bak").exists()) return PhotoSection()
        val value: Any? = DataUtils.readSerializable<Serializable>(context, name)
        if (value !is List<*> || value.any { it !is PhotoScanEntry }) return PhotoSection(failed = true)
        val ids = HashSet<String>()
        val records = ArrayList<PhotoRecord>()
        for (entry in value.filterIsInstance<PhotoScanEntry>()) {
            if (entry.id.isBlank() || !ids.add(entry.id)) return PhotoSection(failed = true)
            records += PhotoRecord(entry.id, java.util.Date(entry.createdAt).toString(),
                PhotoPathPolicy.resolve(context.filesDir, entry.imagePath, true)?.absolutePath,
                listOf(entry.state, entry.error).plus(entry.cards.map {
                    "${it.displayName} \u00d7 ${it.detectedQuantity} \u00b7 ${it.setCode} ${it.collectorNumber} \u00b7 ${it.printingUuid} \u00b7 ${it.finish} \u00b7 ${it.languageCode}"
                }).filter { it.isNotBlank() }.joinToString("\n"))
        }
        return PhotoSection(Collections.unmodifiableList(records))
    }
}
