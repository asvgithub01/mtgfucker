package io.asv.mtgocr.ocrreader.feature.launcher

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.asv.mtgocr.ocrreader.domain.launcher.LauncherData
import io.asv.mtgocr.ocrreader.domain.launcher.LauncherRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LauncherUiState(
    val data: LauncherData? = null,
    val loading: Boolean = true,
    val failed: Boolean = false,
    val navigating: Boolean = false
) {
    val canOpen: Boolean get() = !loading && !failed && !navigating && data?.readable == true
}

class LauncherViewModel(
    private val repository: LauncherRepository,
    private val savedState: SavedStateHandle
) : ViewModel() {
    private val mutableState = MutableStateFlow(LauncherUiState())
    val state = mutableState.asStateFlow()
    private var request: Job? = null

    fun refresh() {
        if (request?.isActive == true) return
        load { repository.load() }
    }

    fun select(id: String) {
        if (mutableState.value.loading || mutableState.value.navigating) return
        if (mutableState.value.data?.libraries?.none { it.id == id } != false) return
        load { repository.select(id) }
    }

    private fun load(block: suspend () -> LauncherData) {
        mutableState.value = mutableState.value.copy(loading = true, failed = false, navigating = false)
        request = viewModelScope.launch {
            try {
                mutableState.value = LauncherUiState(data = block(), loading = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(loading = false, failed = true)
            }
        }
    }

    /** Nullable ticket: null blocks double taps; false still means a valid navigation. */
    fun prepareOpen(): Boolean? {
        if (!mutableState.value.canOpen) return null
        mutableState.value = mutableState.value.copy(navigating = true)
        return savedState.get<Boolean>("pricePromptSent") != true
    }

    fun navigationSucceeded() { savedState["pricePromptSent"] = true }
    fun navigationFailed() {
        mutableState.value = mutableState.value.copy(navigating = false, failed = true)
    }
}
