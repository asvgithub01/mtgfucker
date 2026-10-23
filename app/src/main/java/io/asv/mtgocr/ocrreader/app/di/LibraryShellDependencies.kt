package io.asv.mtgocr.ocrreader.app.di

import android.content.Context
import androidx.lifecycle.createSavedStateHandle
import io.asv.mtgocr.ocrreader.data.legacy.LegacyLibraryShellRepository
import io.asv.mtgocr.ocrreader.domain.library.LibraryShellRepository
import io.asv.mtgocr.ocrreader.feature.library.LibraryShellViewModel
import kotlinx.coroutines.Dispatchers

class LibraryShellDependencies(repository: LibraryShellRepository, libraryId: String) {
    constructor(context: Context, libraryId: String) :
        this(LegacyLibraryShellRepository(context, Dispatchers.IO), libraryId)

    val factory: androidx.lifecycle.ViewModelProvider.Factory =
        RestorableQueryViewModelFactory(LibraryShellViewModel::class.java) { extras ->
            LibraryShellViewModel(libraryId, repository, extras.createSavedStateHandle())
        }
}
