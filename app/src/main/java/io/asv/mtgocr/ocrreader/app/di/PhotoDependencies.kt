package io.asv.mtgocr.ocrreader.app.di

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import io.asv.mtgocr.ocrreader.data.legacy.LegacyPhotoReader
import io.asv.mtgocr.ocrreader.domain.photos.PhotoReader
import io.asv.mtgocr.ocrreader.feature.photos.PhotoViewModel

/** Activity-scoped composition; the reader is created only when a model is requested. */
internal class PhotoDependencies(reader: () -> PhotoReader, libraryId: String) {
    constructor(context: Context, libraryId: String) : this(photoReaderProvider(context), libraryId)

    val factory: ViewModelProvider.Factory = QueryViewModelFactory(PhotoViewModel::class.java) {
        PhotoViewModel(reader(), libraryId)
    }
}

private fun photoReaderProvider(context: Context): () -> PhotoReader {
    val appContext = context.applicationContext
    return { LegacyPhotoReader(appContext) }
}
