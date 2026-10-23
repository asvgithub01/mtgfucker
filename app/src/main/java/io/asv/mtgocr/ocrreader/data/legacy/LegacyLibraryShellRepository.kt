package io.asv.mtgocr.ocrreader.data.legacy

import android.content.Context
import io.asv.mtgocr.ocrreader.domain.library.LibraryShellRepository
import io.asv.mtgocr.ocrreader.data.library.ReadOnlyLibraryShellRepository
import kotlinx.coroutines.CoroutineDispatcher

/** No DeckCatalogStore.load: it migrates and writes the inventory as a side effect. */
class LegacyLibraryShellRepository(context: Context, io: CoroutineDispatcher) : LibraryShellRepository by
    ReadOnlyLibraryShellRepository(AndroidLibraryShellReadSource(context, io), io)
