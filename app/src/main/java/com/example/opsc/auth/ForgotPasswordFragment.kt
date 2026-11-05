package com.example.opsc.auth

import android.os.Bundle
import android.view.View
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.opsc.R
import com.example.opsc.ui.UiMessages
import com.example.opsc.ui.setBusy
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

/** Forgot password: sends a reset email and shows the same message whether or not the account exists. */
class ForgotPasswordFragment : Fragment(R.layout.fragment_forgot_password) {
    private val viewModel: AuthViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val emailLayout = view.findViewById<TextInputLayout>(R.id.forgotEmailLayout)
        val email = view.findViewById<TextInputEditText>(R.id.forgotEmail)
        val sendButton = view.findViewById<MaterialButton>(R.id.forgotSend)
        val progress = view.findViewById<LinearProgressIndicator>(R.id.forgotProgress)
        val form = view.findViewById<View>(R.id.forgotForm)
        val sentCard = view.findViewById<View>(R.id.forgotSentCard)

        // Fill in the email from the login screen.
        if (email.text.isNullOrEmpty()) email.setText(arguments?.getString(ARG_EMAIL).orEmpty())
        email.doAfterTextChanged { emailLayout.error = null }

        fun showSent(sent: Boolean) {
            form.visibility = if (sent) View.GONE else View.VISIBLE
            sentCard.visibility = if (sent) View.VISIBLE else View.GONE
        }
        showSent(viewModel.resetSent)

        fun send() {
            val text = email.text.toString().trim()
            val error = Validators.validateEmail(text)
            emailLayout.error = error?.let { getString(it.messageRes()) }
            if (error == null) viewModel.sendPasswordReset(text)
        }
        sendButton.setOnClickListener { send() }
        email.setOnEditorActionListener { _, _, _ -> send(); true }
        view.findViewById<View>(R.id.forgotBack).setOnClickListener {
            viewModel.resetSent = false
            findNavController().popBackStack()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.loading.collect { busy ->
                        setBusy(sendButton, progress, busy, R.string.btn_send_reset, R.string.msg_sending)
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            AuthEvent.ResetSent -> showSent(true)
                            is AuthEvent.Failed -> UiMessages.error(view, event.error.messageRes)
                            is AuthEvent.LoggedIn -> Unit
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        // Reset the sent state when leaving.
        if (isRemoving) viewModel.resetSent = false
        super.onDestroy()
    }

    companion object {
        const val ARG_EMAIL = "email"
    }
}
