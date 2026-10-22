package io.asv.mtgocr.ocrreader.feature.photos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.asv.mtgocr.ocrreader.domain.photos.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class PhotoUiState(val loading: Boolean = true, val snapshot: PhotoSnapshot? = null)
class PhotoViewModel(private val reader: PhotoReader, private val libraryId: String) : ViewModel() {
    private val mutable = MutableStateFlow(PhotoUiState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var generation = 0
    fun refresh() {
        val request = ++generation
        job?.cancel()
        mutable.value = PhotoUiState()
        job = viewModelScope.launch {
            val result = try { reader.read(libraryId) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { PhotoSnapshot(PhotoSection(failed = true), PhotoSection(failed = true)) }
            if (request == generation) mutable.value = PhotoUiState(false, result)
        }
    }
}
