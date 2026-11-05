package com.example.opsc.app

import android.Manifest
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import com.example.opsc.notifications.NotificationPrefs
import com.example.opsc.notifications.PushPermission
import com.example.opsc.notifications.PushTokenManager
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.opsc.R
import com.example.opsc.auth.AuthRepository
import com.example.opsc.profile.LanguageManager
import com.example.opsc.ui.UiMessages
import com.example.opsc.clubs.ClubAdapter
import com.example.opsc.clubs.ClubsViewModel
import com.example.opsc.clubs.openClub
import com.example.opsc.workouts.listenForPickedExercise
import com.example.opsc.workouts.showLogSheet
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
    private val clubsViewModel: ClubsViewModel by activityViewModels()

    // Registered here (before the fragment starts), as Android requires.
    private val requestPush = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        view?.findViewById<View>(R.id.pushPromptCard)?.visibility = View.GONE
        NotificationPrefs.dismissPrompt(requireContext())
        if (granted) {
            NotificationPrefs.setEnabled(requireContext(), true)
            viewLifecycleOwner.lifecycleScope.launch { PushTokenManager.register(requireContext().applicationContext) }
        } else {
            view?.let { UiMessages.info(it, R.string.push_denied) }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val user = auth.currentUser ?: return
        val greeting = view.findViewById<TextView>(R.id.textGreeting)
        greeting.text = getString(R.string.home_greeting, user.displayName ?: user.email.orEmpty())
        viewLifecycleOwner.lifecycleScope.launch {
            val profile = auth.loadProfile(user.uid) ?: return@launch
            if (profile.username.isNotEmpty()) greeting.text = getString(R.string.home_greeting, profile.username)
            // Restore the language saved in the profile (e.g. after logging in on a new phone).
            if (profile.language.isNotEmpty()) LanguageManager.apply(profile.language)
            // The notification switch follows the profile (so it is the same on every phone).
            NotificationPrefs.setEnabled(requireContext(), profile.pushEnabled)
        }

        // Sensible moment to ask for the notification permission: after login, once, with an explanation.
        val pushCard = view.findViewById<View>(R.id.pushPromptCard)
        val showPrompt = PushPermission.needsRuntimePermission() && !PushPermission.isGranted(requireContext()) && !NotificationPrefs.promptDismissed(requireContext())
        pushCard.visibility = if (showPrompt) View.VISIBLE else View.GONE
        view.findViewById<View>(R.id.pushPromptAllow).setOnClickListener { requestPush.launch(Manifest.permission.POST_NOTIFICATIONS) }
        view.findViewById<View>(R.id.pushPromptLater).setOnClickListener {
            NotificationPrefs.dismissPrompt(requireContext())
            pushCard.visibility = View.GONE
        }

        view.findViewById<View>(R.id.dashLogWorkout).setOnClickListener { showLogSheet(null) }
        listenForPickedExercise()
        view.findViewById<View>(R.id.dashFindClub).setOnClickListener { findNavController().navigate(R.id.clubsFragment) }

        val statMonth = view.findViewById<TextView>(R.id.statMonth)
        val statStreak = view.findViewById<TextView>(R.id.statStreak)
        val statReps = view.findViewById<TextView>(R.id.statReps)
        val goalText = view.findViewById<TextView>(R.id.dashGoalText)
        val goalBar = view.findViewById<LinearProgressIndicator>(R.id.dashGoalBar)

        // My clubs as horizontal cards (or the join-a-club prompt).
        val clubsList = view.findViewById<RecyclerView>(R.id.dashClubsList)
        val clubsPrompt = view.findViewById<TextView>(R.id.dashClubsPrompt)
        val clubAdapter = ClubAdapter(R.layout.item_club_card) { openClub(it.id) }
        clubsList.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        clubsList.adapter = clubAdapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    clubsViewModel.state.collect { clubsState ->
                        val has = clubsState.clubs.isNotEmpty()
                        clubAdapter.submitList(clubsState.clubs)
                        clubsList.visibility = if (has) View.VISIBLE else View.GONE
                        clubsPrompt.visibility = if (has) View.GONE else View.VISIBLE
                    }
                }
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

}
