package io.asv.mtgocr.ocrreader.data.legacy

import android.content.Context
import io.asv.mtgocr.ocrreader.domain.services.ServicesReader
import io.asv.mtgocr.ocrreader.data.services.ReadOnlyServicesReader
import kotlinx.coroutines.Dispatchers

/** Local observation only. Never initialize Firebase, inspect workers, or plan cleanup. */
internal class LegacyServicesReader(context: Context) : ServicesReader by
    ReadOnlyServicesReader(AndroidServicesReadSource(context), Dispatchers.IO)
