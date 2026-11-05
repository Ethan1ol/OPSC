package com.example.opsc.workouts

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.example.opsc.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/** Dialog to change the monthly goal (1 to 100). Survives rotation. */
class GoalDialogFragment : DialogFragment() {
    private val viewModel: WorkoutsViewModel by activityViewModels()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val content = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_goal, null)
        val layout = content.findViewById<TextInputLayout>(R.id.goalLayout)
        val input = content.findViewById<TextInputEditText>(R.id.goalInput)
        if (savedInstanceState == null) input.setText(viewModel.state.value.stats.goal.toString())
        input.doAfterTextChanged { layout.error = null }

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.workouts_goal_title)
            .setView(content)
            .setNegativeButton(R.string.dialog_cancel, null)
            .setPositiveButton(R.string.btn_save_goal, null) // set below so a bad number does not close the dialog
            .create()
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as AlertDialog
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val layout = dialog.findViewById<TextInputLayout>(R.id.goalLayout)
            val value = dialog.findViewById<TextInputEditText>(R.id.goalInput)?.text?.toString()?.trim()?.toIntOrNull()
            if (value == null || value !in WorkoutStats.GOAL_RANGE) {
                layout?.error = getString(R.string.err_goal_range)
            } else {
                viewModel.setGoal(value)
                dismiss()
            }
        }
    }
}
