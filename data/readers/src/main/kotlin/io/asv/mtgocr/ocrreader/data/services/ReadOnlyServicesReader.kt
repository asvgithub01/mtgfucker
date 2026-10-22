package io.asv.mtgocr.ocrreader.data.services

import io.asv.mtgocr.ocrreader.domain.services.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.Collections

/** File conventions and the default-library identity are resolved by the host. */
data class ServiceLibraryReference(val id: String, val name: String,
    val availableWithoutPremium: Boolean, val primaryFile: String, val backupFile: String)

interface ServicesReadSource {
    fun premiumEnabled(): Boolean
    fun libraries(): List<ServiceLibraryReference>
    fun bytes(fileName: String): Long?
    fun preferredCurrency(): String
    fun autoSyncPreference(): Boolean
}

/** Local state only: no activation, sync, restore, pricing or cleanup command exists here. */
class ReadOnlyServicesReader(
    private val source: ServicesReadSource,
    private val io: CoroutineDispatcher
) : ServicesReader {
    override suspend fun read(): ServicesSnapshot = withContext(io) {
        val premium = source.premiumEnabled()
        val libraries = source.libraries().map { library ->
            LibraryFiles(library.id, library.name, premium || library.availableWithoutPremium,
                source.bytes(library.primaryFile), source.bytes(library.backupFile))
        }
        ServicesSnapshot(premium, source.preferredCurrency(), source.autoSyncPreference(),
            Collections.unmodifiableList(libraries))
    }
}
