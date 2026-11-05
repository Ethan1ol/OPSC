package com.example.opsc.settings

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.opsc.R
import com.example.opsc.auth.AuthError
import com.example.opsc.auth.PasswordStrength
import com.example.opsc.auth.PasswordStrengthRater
import com.example.opsc.auth.Validators
import com.example.opsc.auth.messageRes
import com.example.opsc.auth.showAuthFailure
import com.example.opsc.ui.setBusy
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

/** Change password for email accounts. Checks the current password with Firebase, then sets a new one with a live strength hint. */
class ChangePasswordFragment : Fragment(R.layout.fragment_change_password) {
    private val viewModel: SettingsViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val currentLayout = view.findViewById<TextInputLayout>(R.id.cpCurrentLayout)
        val newLayout = view.findViewById<TextInputLayout>(R.id.cpNewLayout)
        val confirmLayout = view.findViewById<TextInputLayout>(R.id.cpConfirmLayout)
        val current = view.findViewById<TextInputEditText>(R.id.cpCurrent)
        val newPassword = view.findViewById<TextInputEditText>(R.id.cpNew)
        val confirm = view.findViewById<TextInputEditText>(R.id.cpConfirm)
        val strengthBar = view.findViewById<LinearProgressIndicator>(R.id.cpStrengthBar)
        val strengthText = view.findViewById<TextView>(R.id.cpStrengthText)
        val update = view.findViewById<MaterialButton>(R.id.cpUpdate)
        val progress = view.findViewById<LinearProgressIndicator>(R.id.cpProgress)

        current.doAfterTextChanged { currentLayout.error = null }
        confirm.doAfterTextChanged { confirmLayout.error = null }
        newPassword.doAfterTextChanged {
            newLayout.error = null
            showStrength(view, strengthBar, strengthText, PasswordStrengthRater.rate(it?.toString().orEmpty()))
        }
        showStrength(view, strengthBar, strengthText, PasswordStrengthRater.rate(newPassword.text.toString()))

        fun submit() {
            val currentText = current.text.toString()
            val newText = newPassword.text.toString()
            val currentError = Validators.validateLoginPassword(currentText)
            val newError = Validators.validateChangedPassword(newText, currentText)
            val confirmError = if (newError == null) Validators.validateConfirmPassword(newText, confirm.text.toString()) else null
            currentLayout.error = currentError?.let { getString(it.messageRes()) }
            newLayout.error = newError?.let { getString(it.messageRes()) }
            confirmLayout.error = confirmError?.let { getString(it.messageRes()) }
            if (currentError == null && newError == null && confirmError == null) {
                viewModel.changePassword(currentText, newText)
            } else {
                Log.d(TAG, "Change-password form invalid")
            }
        }
        update.setOnClickListener { submit() }
        confirm.setOnEditorActionListener { _, _, _ -> submit(); true }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.busy.collect { busy ->
                        setBusy(update, progress, busy, R.string.btn_update_password, R.string.msg_saving)
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            SettingsEvent.PasswordChanged -> {
                                Toast.makeText(requireContext(), R.string.msg_password_changed, Toast.LENGTH_SHORT).show()
                                findNavController().popBackStack()
                            }
                            is SettingsEvent.Failed ->
                                if (event.error == AuthError.WRONG_CREDENTIALS) currentLayout.error = getString(R.string.auth_err_wrong_credentials)
                                else showAuthFailure(view, event.error)
                            else -> Unit
                        }
                    }
                }
            }
        }
    }

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
        const val TAG = "ChangePassword"
    }
}
