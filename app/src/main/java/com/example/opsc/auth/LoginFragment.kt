package com.example.opsc.auth

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.opsc.R
import com.example.opsc.ui.setBusy
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

/** Login screen: email and password, Google sign-in, forgot password and a sign-up link. */
class LoginFragment : Fragment(R.layout.fragment_login) {
    private val viewModel: AuthViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val emailLayout = view.findViewById<TextInputLayout>(R.id.loginEmailLayout)
        val passwordLayout = view.findViewById<TextInputLayout>(R.id.loginPasswordLayout)
        val email = view.findViewById<TextInputEditText>(R.id.loginEmail)
        val password = view.findViewById<TextInputEditText>(R.id.loginPassword)
        val loginButton = view.findViewById<MaterialButton>(R.id.loginButton)
        val googleButton = view.findViewById<MaterialButton>(R.id.loginGoogle)
        val progress = view.findViewById<LinearProgressIndicator>(R.id.loginProgress)

        // Clear a field error when the user types.
        email.doAfterTextChanged { emailLayout.error = null }
        password.doAfterTextChanged { passwordLayout.error = null }

        fun submit() {
            val emailText = email.text.toString().trim()
            val passwordText = password.text.toString()
            val emailError = Validators.validateEmail(emailText)
            val passwordError = Validators.validateLoginPassword(passwordText)
            emailLayout.error = emailError?.let { getString(it.messageRes()) }
            passwordLayout.error = passwordError?.let { getString(it.messageRes()) }
            if (emailError == null && passwordError == null) {
                viewModel.login(emailText, passwordText)
            } else {
                Log.d(TAG, "Login form invalid")
            }
        }

        loginButton.setOnClickListener { submit() }
        password.setOnEditorActionListener { _, _, _ -> submit(); true }

        view.findViewById<View>(R.id.loginForgot).setOnClickListener {
            findNavController().navigate(R.id.forgotPasswordFragment, bundleOf(ForgotPasswordFragment.ARG_EMAIL to email.text.toString().trim()))
        }
        view.findViewById<View>(R.id.loginCreateAccount).setOnClickListener {
            findNavController().navigate(R.id.signUpAccountFragment)
        }

        bindGoogleButton(googleButton, viewModel)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.loading.collect { busy ->
                        setBusy(loginButton, progress, busy, R.string.btn_login, R.string.msg_logging_in)
                        googleButton.isEnabled = !busy
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is AuthEvent.LoggedIn -> handleLoggedIn(event)
                            is AuthEvent.Failed -> showError(view, passwordLayout, event.error)
                            AuthEvent.ResetSent -> Unit
                        }
                    }
                }
            }
        }
    }

    private fun showError(root: View, passwordLayout: TextInputLayout, error: AuthError) {
        if (error == AuthError.WRONG_CREDENTIALS) {
            // The message does not say which part was wrong, so accounts cannot be guessed.
            passwordLayout.error = getString(error.messageRes)
        } else {
            showAuthFailure(root, error)
        }
    }

    private companion object {
        const val TAG = "LoginFragment"
    }
}
