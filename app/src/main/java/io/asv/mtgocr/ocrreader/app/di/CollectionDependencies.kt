package io.asv.mtgocr.ocrreader.app.di

import android.content.Context
import io.asv.mtgocr.ocrreader.data.legacy.AndroidLegacyCollectionSource
import io.asv.mtgocr.ocrreader.data.legacy.LegacyCollectionReader
import io.asv.mtgocr.ocrreader.domain.collection.CollectionReader
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Composition root for the new read-only UI. Not wired into legacy scanners. */
class CollectionDependencies(
    context: Context,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    val collectionReader: CollectionReader =
        LegacyCollectionReader(AndroidLegacyCollectionSource(context), ioDispatcher)
}
