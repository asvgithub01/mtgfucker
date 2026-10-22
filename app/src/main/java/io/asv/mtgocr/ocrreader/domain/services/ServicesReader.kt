package io.asv.mtgocr.ocrreader.domain.services

data class LibraryFiles(val id: String, val name: String, val available: Boolean,
    val primaryBytes: Long?, val backupBytes: Long?)
data class ServicesSnapshot(val premium: Boolean, val currency: String, val autoSyncPreference: Boolean,
    val libraries: List<LibraryFiles>)
fun interface ServicesReader { suspend fun read(): ServicesSnapshot }
