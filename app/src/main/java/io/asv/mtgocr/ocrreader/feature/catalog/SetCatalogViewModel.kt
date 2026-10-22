package io.asv.mtgocr.ocrreader.feature.catalog

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.asv.mtgocr.ocrreader.domain.catalog.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class SetCatalogUiState(
    val loading: Boolean = true,
    val failed: Boolean = false,
    val query: String = "",
    val sets: List<SetSummary> = emptyList(),
    val selected: SetSummary? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class SetCatalogViewModel(
    private val reader: SetCatalogReader,
    private val matcher: SetSearchMatcher,
    private val saved: SavedStateHandle,
    private val computation: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {
    private data class Result(val loading: Boolean = true, val failed: Boolean = false,
        val sets: List<SetSummary> = emptyList())
    private val result = MutableStateFlow(Result())
    private val query = saved.getStateFlow("catalogQuery", "")
    private val selected = saved.getStateFlow<String?>("catalogSelected", null)
    private var request: Job? = null
    private var generation = 0
    val state = combine(result, query, selected) { data, text, code -> Triple(data, text, code) }
        .mapLatest { (data, text, code) ->
            withContext(computation) {
                SetCatalogUiState(data.loading, data.failed, text,
                    data.sets.filter { matcher.matches(it, text) },
                    data.sets.firstOrNull { it.code == code })
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, SetCatalogUiState(query = query.value))

    init { reload() }

    fun reload() {
        val ticket = ++generation
        request?.cancel()
        result.value = Result()
        request = viewModelScope.launch {
            val next = try {
                val sets = reader.read().toList()
                // Ambiguous keys must not crash a lazy list or silently merge different sets.
                check(sets.none { it.code.isBlank() } && sets.map { it.code }.distinct().size == sets.size)
                Result(loading = false, sets = sets.sortedWith(
                    compareByDescending<SetSummary> { it.releaseDate }.thenBy { it.code }))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { Result(loading = false, failed = true) }
            if (ticket == generation) result.value = next
        }
    }

    fun query(value: String) { saved["catalogQuery"] = value }
    fun select(code: String) {
        if (!result.value.loading && !result.value.failed && result.value.sets.any { it.code == code })
            saved["catalogSelected"] = code
    }
    fun dismiss() { saved["catalogSelected"] = null }
}
