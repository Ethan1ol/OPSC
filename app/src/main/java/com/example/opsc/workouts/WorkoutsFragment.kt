package com.example.opsc.workouts

import android.graphics.Canvas
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.opsc.R
import com.example.opsc.ui.StateLayout
import com.example.opsc.ui.UiMessages
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/** Maps a validation error to its message. */
@StringRes
fun WorkoutError.messageRes(): Int = when (this) {
    WorkoutError.EXERCISE_EMPTY -> R.string.err_exercise_empty
    WorkoutError.EXERCISE_TOO_LONG -> R.string.err_exercise_long
    WorkoutError.NOT_A_NUMBER -> R.string.err_whole_number
    WorkoutError.SETS_OUT_OF_RANGE -> R.string.err_sets_range
    WorkoutError.REPS_OUT_OF_RANGE -> R.string.err_reps_range
    WorkoutError.DURATION_OUT_OF_RANGE -> R.string.err_duration_range
    WorkoutError.NOTES_TOO_LONG -> R.string.err_notes_long
}

/** The Workouts tab: goal ring, the workout list with Pending and Synced badges, Sync now, and a button to log a workout. Swipe to delete (with Undo). Works offline. */
class WorkoutsFragment : Fragment(R.layout.fragment_workouts) {
    private val viewModel: WorkoutsViewModel by activityViewModels()
    private val adapter = WorkoutAdapter()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val stateLayout = view.findViewById<StateLayout>(R.id.workoutsState)
        val list = view.findViewById<RecyclerView>(R.id.workoutList)
        val fab = view.findViewById<ExtendedFloatingActionButton>(R.id.fabLog)
        val ring = view.findViewById<CircularProgressIndicator>(R.id.goalRing)
        val percent = view.findViewById<TextView>(R.id.goalPercent)
        val goalText = view.findViewById<TextView>(R.id.goalText)
        val syncStatus = view.findViewById<TextView>(R.id.syncStatus)

        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = adapter
        attachSwipeToDelete(list, view)

        fab.setOnClickListener { showLogSheet() }
        view.findViewById<MaterialButton>(R.id.goalEdit).setOnClickListener {
            if (childFragmentManager.findFragmentByTag(GOAL_TAG) == null) GoalDialogFragment().show(childFragmentManager, GOAL_TAG)
        }
        view.findViewById<MaterialButton>(R.id.syncNow).setOnClickListener { viewModel.syncNow() }
        view.findViewById<MaterialButton>(R.id.browseExercises).setOnClickListener { findNavController().navigate(R.id.exerciseLibraryFragment) }
        listenForPickedExercise()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.state.collect { state ->
                        val stats = state.stats
                        ring.setProgressCompat((stats.progress * 100).toInt(), true)
                        percent.text = getString(R.string.percent_format, (stats.progress * 100).toInt())
                        goalText.text = getString(R.string.workouts_goal_progress, stats.monthCount, stats.goal)
                        syncStatus.text =
                            if (state.pendingCount == 0) getString(R.string.sync_all_synced)
                            else getString(R.string.sync_pending_count, state.pendingCount)

                        when {
                            !state.loaded -> stateLayout.showLoading()
                            state.items.isEmpty() -> {
                                stateLayout.showEmpty(R.drawable.ic_state_empty, R.string.empty_workouts, R.string.btn_log_workout) { showLogSheet() }
                                fab.visibility = View.GONE
                            }
                            else -> {
                                adapter.submitList(state.items)
                                stateLayout.showContent()
                                fab.visibility = View.VISIBLE
                            }
                        }
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is WorkoutEvent.SyncFinished -> if (event.pushed > 0) UiMessages.success(view, R.string.msg_sync_done)
                            WorkoutEvent.SyncQueuedOffline -> UiMessages.info(view, R.string.msg_sync_offline, Snackbar.LENGTH_LONG)
                            is WorkoutEvent.Saved ->
                                UiMessages.success(view, if (event.offline) R.string.msg_saved_offline else R.string.msg_workout_saved)
                        }
                    }
                }
            }
        }
    }

    private fun showLogSheet() = showLogSheet(null)

    /** Swipe a card to delete it. It disappears at once and Undo brings it back until the message goes. */
    private fun attachSwipeToDelete(list: RecyclerView, root: View) {
        val callback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false

            override fun onSwiped(holder: RecyclerView.ViewHolder, direction: Int) {
                val workout = adapter.currentList.getOrNull(holder.bindingAdapterPosition) ?: return
                viewModel.hide(workout.id)
                val bar = UiMessages.undo(root, R.string.msg_workout_deleted) { viewModel.undoHide(workout.id) }
                bar.addCallback(object : Snackbar.Callback() {
                    override fun onDismissed(bar: Snackbar, event: Int) {
                        // Anything except Undo means the delete is accepted.
                        if (event != DISMISS_EVENT_ACTION) viewModel.commitDelete(workout.id)
                    }
                })
            }

            override fun onChildDraw(c: Canvas, rv: RecyclerView, holder: RecyclerView.ViewHolder, dX: Float, dY: Float, state: Int, active: Boolean) {
                val item = holder.itemView
                val background = ColorDrawable(MaterialColors.getColor(item, androidx.appcompat.R.attr.colorError))
                background.setBounds(item.left, item.top, item.right, item.bottom - resources.getDimensionPixelSize(R.dimen.space_sm))
                if (dX != 0f) background.draw(c)
                super.onChildDraw(c, rv, holder, dX, dY, state, active)
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(list)
    }

    private companion object {
        const val GOAL_TAG = "goal_dialog"
    }
}
