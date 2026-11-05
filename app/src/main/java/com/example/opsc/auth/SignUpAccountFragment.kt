package com.example.opsc.auth

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.opsc.R
import com.google.android.material.color.MaterialColors
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * Sign-up step 1 of 2: email, password and confirmation. Shows a live strength hint while the
 * user types. The Firebase account is only created at the end of step 2, so abandoning the
 * sign-up leaves nothing behind.
 */
class SignUpAccountFragment : Fragment(R.layout.fragment_signup_account) {
    private val viewModel: AuthViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val emailLayout = view.findViewById<TextInputLayout>(R.id.signupEmailLayout)
        val passwordLayout = view.findViewById<TextInputLayout>(R.id.signupPasswordLayout)
        val confirmLayout = view.findViewById<TextInputLayout>(R.id.signupConfirmLayout)
        val email = view.findViewById<TextInputEditText>(R.id.signupEmail)
        val password = view.findViewById<TextInputEditText>(R.id.signupPassword)
        val confirm = view.findViewById<TextInputEditText>(R.id.signupConfirm)
        val strengthBar = view.findViewById<LinearProgressIndicator>(R.id.signupStrengthBar)
        val strengthText = view.findViewById<TextView>(R.id.signupStrengthText)

        // Coming back from step 2 (or rotating): refill from the saved answers.
        if (email.text.isNullOrEmpty() && viewModel.pendingEmail.isNotEmpty()) email.setText(viewModel.pendingEmail)
        if (password.text.isNullOrEmpty() && viewModel.pendingPassword.isNotEmpty()) {
            password.setText(viewModel.pendingPassword)
            confirm.setText(viewModel.pendingPassword)
        }

        // Step 2 reported that the email is already taken.
        if (viewModel.emailAlreadyUsed) {
            emailLayout.error = getString(R.string.auth_err_email_in_use)
            viewModel.emailAlreadyUsed = false
        }

        email.doAfterTextChanged { emailLayout.error = null }
        confirm.doAfterTextChanged { confirmLayout.error = null }
        password.doAfterTextChanged {
            passwordLayout.error = null
            showStrength(view, strengthBar, strengthText, PasswordStrengthRater.rate(it?.toString().orEmpty()))
        }
        showStrength(view, strengthBar, strengthText, PasswordStrengthRater.rate(password.text.toString()))

        fun next() {
            val emailText = email.text.toString().trim()
            val passwordText = password.text.toString()
            val emailError = Validators.validateEmail(emailText)
            val passwordError = Validators.validateNewPassword(passwordText)
            val confirmError = if (passwordError == null) Validators.validateConfirmPassword(passwordText, confirm.text.toString()) else null
            emailLayout.error = emailError?.let { getString(it.messageRes()) }
            passwordLayout.error = passwordError?.let { getString(it.messageRes()) }
            confirmLayout.error = confirmError?.let { getString(it.messageRes()) }
            if (emailError == null && passwordError == null && confirmError == null) {
                viewModel.pendingEmail = emailText
                viewModel.pendingPassword = passwordText
                Log.d(TAG, "Step 1 valid, moving to step 2")
                findNavController().navigate(R.id.signUpProfileFragment)
            } else {
                Log.d(TAG, "Step 1 invalid")
            }
        }
        view.findViewById<View>(R.id.signupNext).setOnClickListener { next() }
        confirm.setOnEditorActionListener { _, _, _ -> next(); true }
    }

    /** Updates the strength bar (red, orange, green) and the hint text under the password field. */
    private fun showStrength(root: View, bar: LinearProgressIndicator, text: TextView, strength: PasswordStrength) {
        val (progress, colorAttr, label) = when (strength) {
            PasswordStrength.EMPTY -> Triple(0, com.google.android.material.R.attr.colorOutline, R.string.strength_empty)
            PasswordStrength.WEAK -> Triple(33, androidx.appcompat.R.attr.colorError, R.string.strength_weak)
            PasswordStrength.FAIR -> Triple(66, androidx.appcompat.R.attr.colorPrimary, R.string.strength_fair)
            PasswordStrength.STRONG -> Triple(100, R.attr.colorSuccess, R.string.strength_strong)
        }
        bar.setIndicatorColor(MaterialColors.getColor(root, colorAttr))
        bar.setProgressCompat(progress, true)
        text.setText(label)
    }

    private companion object {
        const val TAG = "SignUpAccount"
    }
}
