package com.example.opsc.app

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.opsc.R
import com.example.opsc.auth.AuthRepository
import com.example.opsc.profile.LanguageManager
import com.example.opsc.ui.UiMessages
import com.example.opsc.workouts.LogWorkoutSheet
import com.example.opsc.workouts.WorkoutEvent
import com.example.opsc.workouts.WorkoutsViewModel
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.launch

/**
 * Home tab (the dashboard): a greeting, this month's workouts, day streak and total reps, the
 * monthly goal, a prompt to join a club (club cards arrive with the clubs feature) and a shortcut
 * to log a workout. Numbers update live from the phone's database, so they work offline.
 */
class HomeFragment : Fragment(R.layout.fragment_home) {
    private val auth = AuthRepository()
    private val viewModel: WorkoutsViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val user = auth.currentUser ?: return
        val greeting = view.findViewById<TextView>(R.id.textGreeting)
        greeting.text = getString(R.string.home_greeting, user.displayName ?: user.email.orEmpty())
        viewLifecycleOwner.lifecycleScope.launch {
            val profile = auth.loadProfile(user.uid) ?: return@launch
            if (profile.username.isNotEmpty()) greeting.text = getString(R.string.home_greeting, profile.username)
            // Restore the language saved in the profile (e.g. after logging in on a new phone).
            if (profile.language.isNotEmpty()) LanguageManager.apply(profile.language)
        }

        view.findViewById<View>(R.id.dashLogWorkout).setOnClickListener {
            if (childFragmentManager.findFragmentByTag(SHEET_TAG) == null) LogWorkoutSheet().show(childFragmentManager, SHEET_TAG)
        }
        view.findViewById<View>(R.id.dashFindClub).setOnClickListener { findNavController().navigate(R.id.clubsFragment) }

        val statMonth = view.findViewById<TextView>(R.id.statMonth)
        val statStreak = view.findViewById<TextView>(R.id.statStreak)
        val statReps = view.findViewById<TextView>(R.id.statReps)
        val goalText = view.findViewById<TextView>(R.id.dashGoalText)
        val goalBar = view.findViewById<LinearProgressIndicator>(R.id.dashGoalBar)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.state.collect { state ->
                        val stats = state.stats
                        statMonth.text = stats.monthCount.toString()
                        statStreak.text = stats.streakDays.toString()
                        statReps.text = stats.monthReps.toString()
                        goalText.text = getString(R.string.workouts_goal_progress, stats.monthCount, stats.goal)
                        goalBar.setProgressCompat((stats.progress * 100).toInt(), true)
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        if (event is WorkoutEvent.Saved) {
                            UiMessages.success(view, if (event.offline) R.string.msg_saved_offline else R.string.msg_workout_saved)
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val SHEET_TAG = "log_workout_sheet"
    }
}
