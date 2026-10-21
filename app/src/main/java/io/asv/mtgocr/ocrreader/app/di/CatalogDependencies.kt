package io.asv.mtgocr.ocrreader.app.di

import android.content.Context
import io.asv.mtgocr.ocrreader.data.CardRepository
import io.asv.mtgocr.ocrreader.data.catalog.CallbackSetCatalogReader
import io.asv.mtgocr.ocrreader.data.catalog.SetCatalogSource
import io.asv.mtgocr.ocrreader.domain.catalog.SetCatalogReader
import io.asv.mtgocr.ocrreader.domain.catalog.SetSummary

class CatalogDependencies(context: Context) {
    private val appContext = context.applicationContext
    val setCatalogReader: SetCatalogReader by lazy {
        CallbackSetCatalogReader(SetCatalogSource { callback ->
            CardRepository.get(appContext).loadSetCatalog { sets, error ->
                callback(sets.map { SetSummary(it.code, it.name, it.releaseDate, it.cardCount) }, error)
            }
        })
    }
}
