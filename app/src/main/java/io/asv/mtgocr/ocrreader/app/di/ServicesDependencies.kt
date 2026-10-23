package io.asv.mtgocr.ocrreader.app.di

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import io.asv.mtgocr.ocrreader.data.legacy.LegacyServicesReader
import io.asv.mtgocr.ocrreader.domain.services.ServicesReader
import io.asv.mtgocr.ocrreader.feature.services.ServicesViewModel

/** Activity-scoped composition; no service/job is initialized by this factory. */
internal class ServicesDependencies(reader: () -> ServicesReader) {
    constructor(context: Context) : this(servicesReaderProvider(context))

    val factory: ViewModelProvider.Factory = QueryViewModelFactory(ServicesViewModel::class.java) {
        ServicesViewModel(reader())
    }
}

private fun servicesReaderProvider(context: Context): () -> ServicesReader {
    val appContext = context.applicationContext
    return { LegacyServicesReader(appContext) }
}
