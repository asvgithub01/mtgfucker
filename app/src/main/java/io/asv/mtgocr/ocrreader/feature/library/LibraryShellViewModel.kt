package io.asv.mtgocr.ocrreader.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.asv.mtgocr.ocrreader.domain.collection.CollectionRow
import io.asv.mtgocr.ocrreader.domain.library.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class LibraryOptions(
    val section: LibrarySection = LibrarySection.CARDS,
    val membership: String? = null,
    val query: String = "",
    val sort: LibrarySort = LibrarySort.NEWEST
)
data class LibraryShellUiState(
    val loading: Boolean = true,
    val failure: ShellReadFailure? = null,
    val options: LibraryOptions = LibraryOptions(),
    val rows: List<CollectionRow> = emptyList(),
    val memberships: List<MembershipSummary> = emptyList(),
    val copies: Long = 0
) {
    val showMemberships get() = options.section != LibrarySection.CARDS && options.membership == null
}

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryShellViewModel(
    private val libraryId: String,
    private val repository: LibraryShellRepository,
    private val saved: SavedStateHandle,
    private val computation: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {
    private val options = MutableStateFlow(LibraryOptions(
        section = LibrarySection.values().firstOrNull { it.name == saved.get<String>("section") } ?: LibrarySection.CARDS,
        membership = saved["membership"], query = saved["query"] ?: "",
        sort = LibrarySort.values().firstOrNull { it.name == saved.get<String>("sort") } ?: LibrarySort.NEWEST
    ))
    private val loaded = MutableStateFlow<ShellReadResult?>(null)
    private var request: Job? = null
    private var generation = 0
    val state = combine(loaded, options) { result, selection -> result to selection }
        .mapLatest { (result, selection) ->
            withContext(computation) {
                when (result) {
                    null -> LibraryShellUiState(options = selection)
                    is ShellReadResult.Failed -> LibraryShellUiState(loading = false, failure = result.reason, options = selection)
                    is ShellReadResult.Ready -> {
                        val memberships = LibraryPresentation.memberships(result.snapshot, selection.section)
                        val rows = LibraryPresentation.rows(result.snapshot, selection.section, selection.membership,
                            selection.query, selection.sort)
                        LibraryShellUiState(false, null, selection, rows, memberships, LibraryPresentation.copies(rows))
                    }
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, LibraryShellUiState(options = options.value))

    fun refresh() {
        val ticket = ++generation
        request?.cancel()
        loaded.value = null
        request = viewModelScope.launch {
            val result = try {
                repository.read(libraryId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ShellReadResult.Failed(ShellReadFailure.COLLECTION_UNREADABLE)
            }
            if (ticket != generation) return@launch
            if (result is ShellReadResult.Ready) {
                if (result.snapshot.collection.libraryId != libraryId) {
                    loaded.value = ShellReadResult.Failed(ShellReadFailure.LIBRARY_CHANGED)
                    return@launch
                }
                val current = options.value
                if (current.membership != null &&
                    LibraryPresentation.memberships(result.snapshot, current.section).none { it.name == current.membership }) {
                    update(current.copy(membership = null))
                }
            }
            loaded.value = result
        }
    }

    /** Collected only while visible; late scanner publications invalidate the read model. */
    suspend fun observeChanges() {
        repository.changes(libraryId).collect { refresh() }
    }

    fun section(value: LibrarySection) = update(options.value.copy(section = value, membership = null, query = ""))
    fun membership(value: String?) = update(options.value.copy(membership = value, query = ""))
    fun query(value: String) = update(options.value.copy(query = value))
    fun sort(value: LibrarySort) = update(options.value.copy(sort = value))

    private fun update(value: LibraryOptions) {
        options.value = value
        saved["section"] = value.section.name
        saved["membership"] = value.membership
        saved["query"] = value.query
        saved["sort"] = value.sort.name
    }
}
