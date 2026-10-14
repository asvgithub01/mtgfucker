package io.asv.mtgocr.ocrreader

import android.content.Context
import io.asv.mtgocr.ocrreader.model.CardInfo
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/** File-backed evidence is authoritative; the UI uses an immutable, IO-free snapshot. */
object ScanReviewStatus {
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var conflicts = emptySet<String>()
    private fun key(library: String, item: String, printing: String) = "$library|$item|$printing"
    @JvmStatic fun hasConflict(context: Context, card: CardInfo): Boolean =
        key(LibraryCatalog.activeFile(context), card.collectionItemId, card.printingUuid.orEmpty()) in conflicts
    @JvmStatic fun refreshIfChanged(context: Context, done: Runnable) {
        val previous = conflicts
        refresh(context, Runnable { if (previous != conflicts) done.run() })
    }
    @JvmStatic fun refresh(context: Context, done: Runnable) {
        val app = context.applicationContext
        worker.execute {
            val library = LibraryCatalog.activeFile(app)
            conflicts = ScanReviewStore.read(app, library).filter { it.state == "conflict" }
                .map { key(library, it.card.collectionItemId, it.card.printingUuid.orEmpty()) }.toSet()
            android.os.Handler(android.os.Looper.getMainLooper()).post(done)
        }
    }
}
