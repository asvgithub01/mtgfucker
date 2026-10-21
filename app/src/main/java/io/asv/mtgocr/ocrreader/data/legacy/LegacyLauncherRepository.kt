package io.asv.mtgocr.ocrreader.data.legacy

import android.content.Context
import io.asv.mtgocr.ocrreader.LibraryCatalog
import io.asv.mtgocr.ocrreader.LaunchArtworkUrl
import io.asv.mtgocr.ocrreader.LaunchBackgroundPolicy
import io.asv.mtgocr.ocrreader.PremiumAccess
import io.asv.mtgocr.ocrreader.domain.launcher.LauncherData
import io.asv.mtgocr.ocrreader.domain.launcher.LauncherLibrary
import io.asv.mtgocr.ocrreader.domain.launcher.LauncherRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.concurrent.ThreadLocalRandom

class LegacyLauncherRepository(
    context: Context,
    private val io: CoroutineDispatcher
) : LauncherRepository {
    private val context = context.applicationContext
    private val source = AndroidLegacyCollectionSource(this.context)
    private val mutex = Mutex()

    override suspend fun load(): LauncherData = withContext(io) {
        mutex.withLock { readCurrent() }
    }

    override suspend fun select(libraryId: String): LauncherData = withContext(io) {
        mutex.withLock {
            // Preserve Premium/default behavior and preference keys.
            LibraryCatalog.select(context, libraryId)
            readCurrent()
        }
    }

    private fun readCurrent(): LauncherData {
        val available = LibraryCatalog.availableLibraries(context)
        val active = LibraryCatalog.active(context)
        val loaded = source.load(active.id)
        val cards = (loaded as? LegacyCollectionLoad.Loaded)?.collection?.cards.orEmpty()
            .filterNotNull()
        val chosen = LaunchBackgroundPolicy.choose(
            cards, LibraryCatalog.pinnedBackgroundId(context, active.id),
            PremiumAccess.isEnabled(context)
        ) { ThreadLocalRandom.current().nextInt(it) }
        return LauncherData(
            Collections.unmodifiableList(available.map { LauncherLibrary(it.id, it.name) }),
            active.id, chosen?.imgPath?.let(LaunchArtworkUrl::resolve),
            loaded !is LegacyCollectionLoad.Failed
        )
    }
}
