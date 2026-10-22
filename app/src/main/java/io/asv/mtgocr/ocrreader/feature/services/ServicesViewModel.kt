package io.asv.mtgocr.ocrreader.feature.services

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.asv.mtgocr.ocrreader.domain.services.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class ServicesUiState(val loading: Boolean = true, val snapshot: ServicesSnapshot? = null, val failed: Boolean = false)
class ServicesViewModel(private val reader: ServicesReader) : ViewModel() {
    private val mutable = MutableStateFlow(ServicesUiState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var generation = 0
    fun refresh() {
        val request = ++generation
        job?.cancel()
        mutable.value = ServicesUiState()
        job = viewModelScope.launch {
            val result = try { reader.read() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
            if (request == generation) mutable.value = ServicesUiState(false, result, result == null)
        }
    }
}
