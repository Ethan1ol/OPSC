package com.example.opsc.auth

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

/**
 * Wires a "Continue with Google" button: opens the Google account picker, then hands the result to
 * the [AuthViewModel]. Used by both the login and the sign-up screens so they behave identically.
 */
fun Fragment.bindGoogleButton(button: MaterialButton, viewModel: AuthViewModel) {
    button.setOnClickListener {
        if (viewModel.loading.value) return@setOnClickListener
        viewModel.googleStarted()
        viewLifecycleOwner.lifecycleScope.launch {
            val outcome = GoogleSignInHelper.signIn(requireActivity(), viewModel.repository)
            viewModel.googleFinished(outcome)
        }
    }
    // If the screen is destroyed while the account picker is open, do not leave the form stuck on "busy".
    viewLifecycleOwner.lifecycle.addObserver(LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_DESTROY && viewModel.googleInFlight) viewModel.googleAbandoned()
    })
}

/**
 * Called when a sign-in succeeded. A user with no profile yet goes to the "finish your profile"
 * screen; everyone else goes into the app.
 */
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

/**
 * Shows a failure from the view model. Cancelling Google sign-in is not an error, so it gets a
 * plain message; other problems get a red one.
 */
fun showAuthFailure(root: View, error: AuthError) {
    if (error == AuthError.GOOGLE_CANCELLED) UiMessages.info(root, error.messageRes)
    else UiMessages.error(root, error.messageRes)
}
