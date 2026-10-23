package io.asv.mtgocr.ocrreader.app.di

import android.content.Context
import androidx.lifecycle.*
import io.asv.mtgocr.ocrreader.data.catalog.LegacySetSearchMatcher
import io.asv.mtgocr.ocrreader.domain.catalog.SetCatalogReader
import io.asv.mtgocr.ocrreader.domain.catalog.SetSearchMatcher
import io.asv.mtgocr.ocrreader.feature.catalog.SetCatalogViewModel

class SetCatalogViewModelFactory(
    reader: () -> SetCatalogReader,
    matcher: SetSearchMatcher
) : ViewModelProvider.Factory by RestorableQueryViewModelFactory(SetCatalogViewModel::class.java, { extras ->
    // Keep lazy reader resolution before SavedStateHandle creation, as in the original factory.
    SetCatalogViewModel(reader(), matcher, extras.createSavedStateHandle())
}) {
    constructor(context: Context) : this(CatalogDependencies(context))
    private constructor(catalog: CatalogDependencies) : this({ catalog.setCatalogReader }, LegacySetSearchMatcher)
}
