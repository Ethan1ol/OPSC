package com.example.opsc.exercises

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the library screen shows. */
data class LibraryState(
    val items: List<ExerciseEntity> = emptyList(),
    /** True if there are more matches than are shown (for the "Load more" button). */
    val canLoadMore: Boolean = false,
    val categories: List<CategoryRow> = emptyList(),
    /** How many exercises are stored on the phone. 0 means the library has never been downloaded. */
    val cachedCount: Int = 0,
    val syncing: Boolean = false,
    /** 0.0 to 1.0 while [syncing]. */
    val progress: Float = 0f,
    /** Set if the last download failed. */
    val failure: RefreshFailure? = null,
    val loaded: Boolean = false
)

/**
 * Drives the exercise library: the search text, category filter and "load more" paging all work on
 * the phone's cache; the download from wger runs in the background and can be retried.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class ExerciseLibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ExerciseRepository.get(application)

    private val query = MutableStateFlow("")
    private val categoryId = MutableStateFlow(0)
    private val limit = MutableStateFlow(PAGE_SIZE)
    private val sync = MutableStateFlow(SyncUi())

    private data class SyncUi(val syncing: Boolean = false, val progress: Float = 0f, val failure: RefreshFailure? = null)

    private val results = combine(
        query.debounce { if (it.isEmpty()) 0L else 250L }.distinctUntilChanged(),
        categoryId,
        limit
    ) { q, c, l -> Triple(q, c, l) }
        .flatMapLatest { (q, c, l) ->
            // Ask for one extra row so we know whether "Load more" is needed.
            repository.search(q, c, l + 1).map { rows -> rows.take(l) to (rows.size > l) }
        }

    val state: StateFlow<LibraryState> = combine(results, repository.categories(), repository.count(), sync) { (rows, more), categories, count, s ->
        LibraryState(rows, more, categories, count, s.syncing, s.progress, s.failure, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryState())

    /** The text currently in the search box (kept so rotation restores it). */
    val currentQuery: String get() = query.value
    val currentCategory: Int get() = categoryId.value

    init {
        // First visit, or the library is over a week old: download it in the background.
        if (repository.isStale()) refresh()
    }

    fun setQuery(text: String) {
        if (text == query.value) return
        query.value = text
        limit.value = PAGE_SIZE
    }

    fun setCategory(id: Int) {
        if (id == categoryId.value) return
        categoryId.value = id
        limit.value = PAGE_SIZE
    }

    fun loadMore() {
        limit.value = limit.value + PAGE_SIZE
    }

    /** Downloads the library from wger. Safe to call repeatedly (it ignores calls while running). */
    fun refresh() {
        if (sync.value.syncing) return
        sync.value = SyncUi(syncing = true)
        viewModelScope.launch {
            val result = repository.refresh { progress -> sync.value = SyncUi(syncing = true, progress = progress) }
            sync.value = when (result) {
                is RefreshResult.Success -> SyncUi()
                is RefreshResult.Failure -> SyncUi(failure = result.reason)
            }
        }
    }

    private companion object {
        const val PAGE_SIZE = 30
    }
}
