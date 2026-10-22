package io.asv.mtgocr.ocrreader.data.legacy

import android.content.Context
import io.asv.mtgocr.ocrreader.domain.photos.PhotoReader
import kotlinx.coroutines.Dispatchers

/** No store mutations, recovery repairs, processing jobs, or active-library changes. */
internal class LegacyPhotoReader(context: Context) : PhotoReader by
    ReadOnlyPhotoReader(AndroidLegacyPhotoSource(context), Dispatchers.IO)
