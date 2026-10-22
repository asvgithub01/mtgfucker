package io.asv.mtgocr.ocrreader.app.di

import android.content.Context
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.CreationExtras
import io.asv.mtgocr.ocrreader.data.catalog.LegacySetSearchMatcher
import io.asv.mtgocr.ocrreader.feature.catalog.SetCatalogViewModel

class SetCatalogViewModelFactory(context: Context) : ViewModelProvider.Factory {
    private val catalog = CatalogDependencies(context)
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        require(modelClass == SetCatalogViewModel::class.java)
        @Suppress("UNCHECKED_CAST")
        return SetCatalogViewModel(catalog.setCatalogReader, LegacySetSearchMatcher,
            extras.createSavedStateHandle()) as T
    }
}
