package io.asv.mtgocr.ocrreader.app.di

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import io.asv.mtgocr.ocrreader.data.legacy.LegacyLibraryShellRepository
import io.asv.mtgocr.ocrreader.feature.library.LibraryShellViewModel
import kotlinx.coroutines.Dispatchers

class LibraryShellDependencies(context: Context, private val libraryId: String) {
    private val repository = LegacyLibraryShellRepository(context, Dispatchers.IO)
    val factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            require(modelClass == LibraryShellViewModel::class.java)
            @Suppress("UNCHECKED_CAST")
            return LibraryShellViewModel(libraryId, repository, extras.createSavedStateHandle()) as T
        }
    }
}
