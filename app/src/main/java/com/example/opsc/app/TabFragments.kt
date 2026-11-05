package com.example.opsc.app

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.opsc.BuildConfig
import com.example.opsc.MainActivity
import com.example.opsc.R
import com.example.opsc.SettingsActivity
import com.example.opsc.auth.AuthRepository
import com.example.opsc.auth.GoogleSignInHelper
import com.example.opsc.profile.LanguageManager
import com.example.opsc.ui.ConfirmDialog
import com.example.opsc.ui.StateLayout
import kotlinx.coroutines.launch

/**
 * Base for tabs whose real screen is built in a later feature. It shows the shared
 * empty state so the tab is not blank and the navigation can be tried end to end.
 */
abstract class PlaceholderFragment(@StringRes private val messageRes: Int) :
    Fragment(R.layout.fragment_state_placeholder) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<StateLayout>(R.id.stateLayout).showEmpty(R.drawable.ic_state_empty, messageRes)
    }
}

/** Clubs tab (real screen: clubs and league feature). */
class ClubsFragment : PlaceholderFragment(R.string.placeholder_clubs)

/** Workouts tab (real screen: Workout Logger feature). */
class WorkoutsFragment : PlaceholderFragment(R.string.placeholder_workouts)

/** Home tab: greeting now; stats, clubs and next fixture arrive with the dashboard feature. */
class HomeFragment : Fragment(R.layout.fragment_home) {
    private val auth = AuthRepository()

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
    }
}

/** Profile tab: account summary, settings, design gallery (debug builds only) and log out. */
class ProfileFragment : Fragment(R.layout.fragment_profile) {
    private val auth = AuthRepository()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val user = auth.currentUser ?: return
        view.findViewById<TextView>(R.id.textProfileName).text = user.displayName ?: user.email.orEmpty()
        view.findViewById<TextView>(R.id.textProfileEmail).text = user.email.orEmpty()

        view.findViewById<View>(R.id.buttonSettings).setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }

        // The design gallery only exists in debug builds, so it is opened by class name.
        if (BuildConfig.DEBUG) {
            view.findViewById<View>(R.id.buttonGallery).apply {
                visibility = View.VISIBLE
                setOnClickListener {
                    val intent = Intent().setComponent(
                        ComponentName(requireContext().packageName, "com.example.opsc.DesignGalleryActivity")
                    )
                    startActivity(intent)
                }
            }
        }

        view.findViewById<View>(R.id.buttonLogout).setOnClickListener {
            ConfirmDialog.show(requireContext(), R.string.logout_title, R.string.logout_message, R.string.btn_logout) {
                logout()
            }
        }
    }

    private fun logout() {
        viewLifecycleOwner.lifecycleScope.launch {
            Log.i(TAG, "User confirmed log out")
            auth.logout()
            GoogleSignInHelper.clearState(requireActivity())
            startActivity(Intent(requireContext(), MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            requireActivity().finish()
        }
    }

    private companion object {
        const val TAG = "ProfileFragment"
    }
}
