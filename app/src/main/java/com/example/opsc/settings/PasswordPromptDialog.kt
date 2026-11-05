package com.example.opsc.settings

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import com.example.opsc.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/** Asks for the password again before a risky action (deleting the account). Survives rotation. The password is returned through the Fragment Result API and never stored. */
class PasswordPromptDialog : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val content = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_password_prompt, null)
        val layout = content.findViewById<TextInputLayout>(R.id.promptLayout)
        val field = content.findViewById<TextInputEditText>(R.id.promptPassword)
        field.doAfterTextChanged { layout.error = null }

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.reauth_title)
            .setMessage(R.string.reauth_message)
            .setView(content)
            .setNegativeButton(R.string.dialog_cancel, null)
            .setPositiveButton(R.string.settings_delete_account, null) // set below so an empty password does not close the dialog
            .create()
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as AlertDialog
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val layout = dialog.findViewById<TextInputLayout>(R.id.promptLayout)
            val password = dialog.findViewById<TextInputEditText>(R.id.promptPassword)?.text?.toString().orEmpty()
            if (password.isEmpty()) {
                layout?.error = getString(R.string.err_password_empty)
            } else {
                parentFragmentManager.setFragmentResult(REQUEST_KEY, bundleOf(RESULT_PASSWORD to password))
                dismiss()
            }
        }
    }

    companion object {
        const val TAG = "password_prompt"
        const val REQUEST_KEY = "password_prompt_result"
        const val RESULT_PASSWORD = "password"
    }
}
