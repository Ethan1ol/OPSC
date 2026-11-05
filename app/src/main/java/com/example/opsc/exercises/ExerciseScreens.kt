package com.example.opsc.exercises

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.text.HtmlCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.opsc.R
import com.example.opsc.ui.ConnectivityMonitor
import com.example.opsc.ui.StateLayout
import com.example.opsc.ui.UiMessages
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

/** How a chosen exercise travels back to the screen that wanted it (Fragment Result API). */
object ExercisePicker {
    const val RESULT_KEY = "exercise_picked"
    const val RESULT_NAME = "name"
    const val ARG_PICK_MODE = "pickMode"
    const val ARG_EXERCISE_ID = "exerciseId"

    /** Hands the chosen exercise name back to whoever opened the library. */
    fun deliver(fragment: Fragment, name: String) {
        fragment.parentFragmentManager.setFragmentResult(RESULT_KEY, bundleOf(RESULT_NAME to name))
    }
}

/** The exercise list: name and a line with category and equipment. */
class ExerciseAdapter(private val onClick: (ExerciseEntity) -> Unit) : ListAdapter<ExerciseEntity, ExerciseAdapter.Holder>(Diff) {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val name: TextView = view.findViewById(R.id.exerciseName)
        private val meta: TextView = view.findViewById(R.id.exerciseMeta)

        fun bind(item: ExerciseEntity, onClick: (ExerciseEntity) -> Unit) {
            name.text = item.name
            val parts = listOf(item.categoryName, item.equipmentText).filter { it.isNotBlank() }
            meta.text = parts.joinToString(" · ")
            meta.visibility = if (parts.isEmpty()) View.GONE else View.VISIBLE
            itemView.setOnClickListener { onClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_exercise, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position), onClick)

    private object Diff : DiffUtil.ItemCallback<ExerciseEntity>() {
        override fun areItemsTheSame(a: ExerciseEntity, b: ExerciseEntity) = a.id == b.id
        override fun areContentsTheSame(a: ExerciseEntity, b: ExerciseEntity) = a == b
    }
}

/**
 * The exercise library screen: search, category chips, the list with "load more", and states for
 * loading (with a download progress bar), empty results, errors with Retry, and offline.
 * In pick mode, tapping an exercise returns it straight to the log-workout sheet.
 */
class ExerciseLibraryFragment : Fragment(R.layout.fragment_exercise_library) {
    private val viewModel: ExerciseLibraryViewModel by viewModels()
    private var shownFailure = false
    private var builtCategories: List<CategoryRow>? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val pickMode = arguments?.getBoolean(ExercisePicker.ARG_PICK_MODE) == true
        val search = view.findViewById<TextInputEditText>(R.id.librarySearch)
        val chips = view.findViewById<ChipGroup>(R.id.libraryChips)
        val stateLayout = view.findViewById<StateLayout>(R.id.libraryState)
        val list = view.findViewById<RecyclerView>(R.id.libraryList)
        val progress = view.findViewById<LinearProgressIndicator>(R.id.libraryProgress)
        val progressText = view.findViewById<TextView>(R.id.libraryProgressText)
        val loadMore = view.findViewById<MaterialButton>(R.id.libraryLoadMore)

        val adapter = ExerciseAdapter { exercise ->
            if (pickMode) {
                ExercisePicker.deliver(this, exercise.name)
                findNavController().popBackStack()
            } else {
                findNavController().navigate(R.id.exerciseDetailFragment, bundleOf(ExercisePicker.ARG_EXERCISE_ID to exercise.id))
            }
        }
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = adapter
        // Load the next page automatically when the user nears the end of the list.
        list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                val layout = rv.layoutManager as LinearLayoutManager
                if (dy > 0 && layout.findLastVisibleItemPosition() >= adapter.itemCount - 5 && viewModel.state.value.canLoadMore) viewModel.loadMore()
            }
        })
        loadMore.setOnClickListener { viewModel.loadMore() }

        if (search.text.isNullOrEmpty() && viewModel.currentQuery.isNotEmpty()) search.setText(viewModel.currentQuery)
        search.doAfterTextChanged { viewModel.setQuery(it?.toString().orEmpty()) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    buildChips(chips, state.categories)
                    progress.visibility = if (state.syncing) View.VISIBLE else View.GONE
                    progressText.visibility = if (state.syncing) View.VISIBLE else View.GONE
                    if (state.syncing) {
                        progress.setProgressCompat((state.progress * 100).toInt(), true)
                        progressText.text = getString(R.string.library_downloading, (state.progress * 100).toInt())
                    }
                    loadMore.visibility = if (state.canLoadMore) View.VISIBLE else View.GONE

                    val cacheEmpty = state.cachedCount == 0
                    when {
                        !state.loaded -> stateLayout.showLoading()
                        cacheEmpty && state.failure != null -> {
                            if (state.failure == RefreshFailure.OFFLINE) stateLayout.showOffline { viewModel.refresh() }
                            else stateLayout.showError(R.string.library_error) { viewModel.refresh() }
                        }
                        cacheEmpty -> stateLayout.showLoading()
                        state.items.isEmpty() -> stateLayout.showEmpty(R.drawable.ic_state_empty, R.string.library_no_match)
                        else -> {
                            adapter.submitList(state.items)
                            stateLayout.showContent()
                        }
                    }

                    // A failed refresh when we still have saved exercises is only worth a quiet note.
                    if (state.failure != null && !cacheEmpty && !shownFailure) {
                        shownFailure = true
                        UiMessages.info(view, R.string.library_update_failed)
                    }
                    if (state.failure == null) shownFailure = false
                }
            }
        }
    }

    /** Builds the "All" chip plus one chip per category, once per distinct list of categories. */
    private fun buildChips(group: ChipGroup, categories: List<CategoryRow>) {
        if (categories == builtCategories) return
        builtCategories = categories
        group.setOnCheckedStateChangeListener(null)
        group.removeAllViews()
        val selected = viewModel.currentCategory
        fun addChip(label: String, categoryId: Int) {
            val chip = layoutInflater.inflate(R.layout.chip_filter, group, false) as Chip
            chip.id = View.generateViewId()
            chip.text = label
            chip.tag = categoryId
            group.addView(chip)
            if (categoryId == selected) group.check(chip.id)
        }
        addChip(getString(R.string.library_all), 0)
        categories.forEach { addChip(it.categoryName, it.categoryId) }
        if (group.checkedChipId == View.NO_ID) group.check(group.getChildAt(0).id)
        group.setOnCheckedStateChangeListener { g, ids ->
            val chip = ids.firstOrNull()?.let { g.findViewById<Chip>(it) }
            viewModel.setCategory((chip?.tag as? Int) ?: 0)
        }
    }
}

/** Shows one exercise: category, muscles, equipment and the description, with a button to log it. */
class ExerciseDetailFragment : Fragment(R.layout.fragment_exercise_detail) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val state = view.findViewById<StateLayout>(R.id.detailState)
        val id = arguments?.getInt(ExercisePicker.ARG_EXERCISE_ID) ?: -1
        state.showLoading()
        viewLifecycleOwner.lifecycleScope.launch {
            val exercise = ExerciseRepository.get(requireContext()).get(id)
            if (exercise == null) {
                state.showEmpty(R.drawable.ic_state_empty, R.string.library_not_found)
                return@launch
            }
            view.findViewById<TextView>(R.id.detailName).text = exercise.name
            view.findViewById<TextView>(R.id.detailCategory).text = exercise.categoryName
            view.findViewById<TextView>(R.id.detailMuscles).text = exercise.musclesText.ifBlank { getString(R.string.detail_none) }
            view.findViewById<TextView>(R.id.detailSecondary).text = exercise.secondaryMusclesText.ifBlank { getString(R.string.detail_none) }
            view.findViewById<TextView>(R.id.detailEquipment).text = exercise.equipmentText.ifBlank { getString(R.string.detail_none) }
            view.findViewById<TextView>(R.id.detailDescription).text =
                if (exercise.description.isBlank()) getString(R.string.detail_no_description)
                else HtmlCompat.fromHtml(exercise.description, HtmlCompat.FROM_HTML_MODE_COMPACT)
            view.findViewById<View>(R.id.detailLog).setOnClickListener {
                ExercisePicker.deliver(this@ExerciseDetailFragment, exercise.name)
                // Go back to the screen that opened the library (Workouts) so it can open the log sheet.
                if (!findNavController().popBackStack(R.id.workoutsFragment, false)) findNavController().popBackStack()
            }
            state.showContent()
        }
    }
}
