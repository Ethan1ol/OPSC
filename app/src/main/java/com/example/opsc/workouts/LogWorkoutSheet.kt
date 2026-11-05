package com.example.opsc.workouts

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.opsc.exercises.ExercisePicker
import com.example.opsc.exercises.ExerciseRepository
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.opsc.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

/**
 * The "Log a workout" bottom sheet: exercise (with suggestions from past workouts), sets, reps,
 * duration and notes. Every field is validated with inline messages. Saving writes to the phone
 * first, so it works with no internet.
 */
class LogWorkoutSheet : BottomSheetDialogFragment() {
    private val viewModel: WorkoutsViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.sheet_log_workout, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val exerciseLayout = view.findViewById<TextInputLayout>(R.id.logExerciseLayout)
        val setsLayout = view.findViewById<TextInputLayout>(R.id.logSetsLayout)
        val repsLayout = view.findViewById<TextInputLayout>(R.id.logRepsLayout)
        val durationLayout = view.findViewById<TextInputLayout>(R.id.logDurationLayout)
        val notesLayout = view.findViewById<TextInputLayout>(R.id.logNotesLayout)
        val exercise = view.findViewById<MaterialAutoCompleteTextView>(R.id.logExercise)
        val sets = view.findViewById<TextInputEditText>(R.id.logSets)
        val reps = view.findViewById<TextInputEditText>(R.id.logReps)
        val duration = view.findViewById<TextInputEditText>(R.id.logDuration)
        val notes = view.findViewById<TextInputEditText>(R.id.logNotes)
        val save = view.findViewById<MaterialButton>(R.id.logSave)

        listOf(exerciseLayout to exercise, setsLayout to sets, repsLayout to reps, durationLayout to duration, notesLayout to notes)
            .forEach { (layout, edit) -> edit.doAfterTextChanged { layout.error = null } }

        // Chosen from the library or the detail screen? Fill it in.
        if (savedInstanceState == null) arguments?.getString(ARG_EXERCISE)?.let { exercise.setText(it, false) }

        // Browse the full library, then come back here with the chosen exercise.
        view.findViewById<View>(R.id.logBrowse).setOnClickListener {
            parentFragment?.findNavController()?.navigate(R.id.exerciseLibraryFragment, bundleOf(ExercisePicker.ARG_PICK_MODE to true))
            dismiss()
        }

        // Suggestions: exercises you logged before, then the library (or a few common ones if it is not downloaded yet).
        val common = resources.getStringArray(R.array.common_exercises).toList()
        var libraryNames: List<String> = emptyList()
        viewLifecycleOwner.lifecycleScope.launch { libraryNames = ExerciseRepository.get(requireContext()).allNames() }
        val suggestions = ArrayAdapter<String>(requireContext(), android.R.layout.simple_list_item_1)
        exercise.setAdapter(suggestions)
        exercise.threshold = 1
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.recentExercises.collect { recent ->
                    suggestions.clear()
                    suggestions.addAll((recent + libraryNames.ifEmpty { common }).distinctBy { it.lowercase() })
                }
            }
        }

        fun submit() {
            val errors = WorkoutValidator.validate(
                exercise.text.toString(), sets.text.toString(), reps.text.toString(), duration.text.toString(), notes.text.toString()
            )
            val layouts = mapOf(
                WorkoutField.EXERCISE to exerciseLayout, WorkoutField.SETS to setsLayout, WorkoutField.REPS to repsLayout,
                WorkoutField.DURATION to durationLayout, WorkoutField.NOTES to notesLayout
            )
            layouts.forEach { (field, layout) -> layout.error = errors[field]?.let { getString(it.messageRes()) } }
            if (errors.isNotEmpty()) {
                Log.d(TAG, "Workout form invalid: ${errors.keys}")
                layouts.entries.first { it.key in errors }.value.requestFocus()
                return
            }
            viewModel.add(
                NewWorkout(
                    exercise = exercise.text.toString(),
                    sets = sets.text.toString().trim().toInt(),
                    reps = reps.text.toString().trim().toInt(),
                    durationMinutes = duration.text.toString().trim().toInt(),
                    notes = notes.text.toString()
                )
            )
            dismiss()
        }
        save.setOnClickListener { submit() }
        notes.setOnEditorActionListener { _, _, _ -> submit(); true }
    }

    override fun onStart() {
        super.onStart()
        // Open fully so the keyboard never hides the Save button.
        (dialog as? BottomSheetDialog)?.behavior?.state = BottomSheetBehavior.STATE_EXPANDED
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    companion object {
        private const val TAG = "LogWorkoutSheet"
        const val SHEET_TAG = "log_workout_sheet"
        const val ARG_EXERCISE = "exercise"

        fun newInstance(exercise: String?) = LogWorkoutSheet().apply {
            arguments = bundleOf(ARG_EXERCISE to exercise)
        }
    }
}

/** Opens the log-workout sheet, optionally with an exercise already filled in. */
fun Fragment.showLogSheet(exercise: String?) {
    if (childFragmentManager.findFragmentByTag(LogWorkoutSheet.SHEET_TAG) == null) {
        LogWorkoutSheet.newInstance(exercise).show(childFragmentManager, LogWorkoutSheet.SHEET_TAG)
    }
}

/** When an exercise is chosen in the library, reopen the log sheet with it filled in. */
fun Fragment.listenForPickedExercise() {
    parentFragmentManager.setFragmentResultListener(ExercisePicker.RESULT_KEY, viewLifecycleOwner) { _, bundle ->
        showLogSheet(bundle.getString(ExercisePicker.RESULT_NAME))
    }
}
