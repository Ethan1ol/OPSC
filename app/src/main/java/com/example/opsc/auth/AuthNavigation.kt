package com.example.opsc.auth

import android.content.Intent
import android.util.Log
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.example.opsc.R
import com.example.opsc.ui.UiMessages
import com.example.opsc.ui.openMainApp
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

private const val TAG = "AuthNavigation"

/** Sets up the Google button. Used by both login and sign-up. */
fun Fragment.bindGoogleButton(button: MaterialButton, viewModel: AuthViewModel) {
    button.setOnClickListener {
        if (viewModel.loading.value) return@setOnClickListener
        viewModel.googleStarted()
        viewLifecycleOwner.lifecycleScope.launch {
            val outcome = GoogleSignInHelper.signIn(requireActivity(), viewModel.repository)
            viewModel.googleFinished(outcome)
        }
    }
    // If the screen closes while the picker is open, stop showing busy.
    viewLifecycleOwner.lifecycle.addObserver(LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_DESTROY && viewModel.googleInFlight) viewModel.googleAbandoned()
    })
}

/** After sign-in: new Google users finish their profile, everyone else enters the app. */
fun Fragment.handleLoggedIn(event: AuthEvent.LoggedIn) {
    if (event.needsProfile) {
        Log.i(TAG, "Signed in but no profile yet: opening finish-profile")
        findNavController().navigate(
            R.id.finishProfileFragment, null,
            navOptions { popUpTo(R.id.loginFragment) { inclusive = true } }
        )
    } else {
        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            ?.let { ProfileGate.markComplete(requireContext(), it) }
        openMainApp()
    }
}

/** Signs out and returns to login. */
fun Fragment.logoutToLogin() {
    val activity = requireActivity()
    activity.lifecycleScope.launch {
        AuthRepository().logout()
        GoogleSignInHelper.clearState(activity)
        activity.startActivity(Intent(activity, AuthActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        activity.finish()
    }
}

/** Shows an error. Cancelling Google sign-in gets a plain message. */
fun showAuthFailure(root: View, error: AuthError) {
    if (error == AuthError.GOOGLE_CANCELLED) UiMessages.info(root, error.messageRes)
    else UiMessages.error(root, error.messageRes)
}
