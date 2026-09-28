package io.asv.mtgocr.ocrreader

import android.content.Context
import android.os.Handler
import android.os.Looper
import io.asv.mtgocr.ocrreader.model.CardInfo
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/** One off-main evidence lookup per adapter, never a directory scan during row binding. */
class LibraryCaptureImages(context: Context, cards: List<CardInfo>, library: String, ready: Runnable) {
    private var photos: Map<String, File> = emptyMap()
    private val selected = HashSet<String>()
    init {
        val files = context.applicationContext.filesDir
        val snapshots = cards.map { it.collectionItemId to it.scanPhotoPaths.toList() }
        worker.execute {
            val found = HashMap<String, File>()
            snapshots.forEach { (id, paths) ->
                paths.asReversed().firstNotNullOfOrNull { ScanEvidencePanel.photoFile(files, it) }
                    ?.let { found[id] = it }
            }
            File(files, "edscan").listFiles().orEmpty()
                .filter { it.name.endsWith(".link.json") }.sortedBy { it.lastModified() }.forEach { link ->
                    runCatching {
                        val json = JSONObject(link.readText())
                        val key = link.name.removeSuffix(".link.json")
                        if (json.optString("library") == library && key.matches(Regex("[0-9a-fA-F-]{36}"))) {
                            val photo = File(link.parentFile, "$key.jpg")
                            if (photo.isFile) found[json.getString("collectionItemId")] = photo
                        }
                    }
                }
            Handler(Looper.getMainLooper()).post { photos = found; ready.run() }
        }
    }
    fun available(id: String) = photos.containsKey(id)
    fun selectedPhoto(id: String): File? = if (id in selected) photos[id] else null
    fun toggle(id: String) { if (!selected.remove(id) && available(id)) selected.add(id) }
    companion object { private val worker = Executors.newSingleThreadExecutor() }
}
