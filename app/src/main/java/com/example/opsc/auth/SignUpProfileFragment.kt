package com.example.opsc.auth

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.opsc.R
import com.example.opsc.ui.UiMessages
import com.example.opsc.ui.countryList
import com.example.opsc.ui.openMainApp
import com.example.opsc.ui.setBusy
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Sign-up step 2 of 2: username, name, birth date (date picker, at least 13 years old),
 * height, weight and country. Every field is checked at once with inline messages; then the
 * Firebase account and the Firestore profile (users/{uid}) are created.
 */
class SignUpProfileFragment : Fragment(R.layout.fragment_signup_profile) {
    private val viewModel: AuthViewModel by activityViewModels()
    private lateinit var countries: List<Pair<String, String>>

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val usernameLayout = view.findViewById<TextInputLayout>(R.id.profileUsernameLayout)
        val nameLayout = view.findViewById<TextInputLayout>(R.id.profileNameLayout)
        val surnameLayout = view.findViewById<TextInputLayout>(R.id.profileSurnameLayout)
        val birthLayout = view.findViewById<TextInputLayout>(R.id.profileBirthLayout)
        val heightLayout = view.findViewById<TextInputLayout>(R.id.profileHeightLayout)
        val weightLayout = view.findViewById<TextInputLayout>(R.id.profileWeightLayout)
        val countryLayout = view.findViewById<TextInputLayout>(R.id.profileCountryLayout)
        val username = view.findViewById<TextInputEditText>(R.id.profileUsername)
        val name = view.findViewById<TextInputEditText>(R.id.profileName)
        val surname = view.findViewById<TextInputEditText>(R.id.profileSurname)
        val birth = view.findViewById<TextInputEditText>(R.id.profileBirth)
        val height = view.findViewById<TextInputEditText>(R.id.profileHeight)
        val weight = view.findViewById<TextInputEditText>(R.id.profileWeight)
        val country = view.findViewById<MaterialAutoCompleteTextView>(R.id.profileCountry)
        val createButton = view.findViewById<MaterialButton>(R.id.profileCreate)
        val progress = view.findViewById<LinearProgressIndicator>(R.id.profileProgress)

        listOf(usernameLayout to username, nameLayout to name, surnameLayout to surname, heightLayout to height, weightLayout to weight)
            .forEach { (layout, edit) -> edit.doAfterTextChanged { layout.error = null } }

        // Country dropdown. The chosen code lives in the ViewModel so it survives rotation.
        countries = countryList(Locale.getDefault())
        country.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, countries.map { it.second }))
        country.setText(countries.firstOrNull { it.first == viewModel.selectedCountry }?.second.orEmpty(), false)
        country.setOnItemClickListener { _, _, position, _ ->
            val shown = country.adapter.getItem(position) as String
            viewModel.selectedCountry = countries.first { it.second == shown }.first
            countryLayout.error = null
        }

        // Birth date picker (a dialog fragment, so it survives rotation; re-attach its listener).
        val showPicker = { openDatePicker(birth, birthLayout) }
        birth.setOnClickListener { showPicker() }
        birthLayout.setEndIconOnClickListener { showPicker() }
        (childFragmentManager.findFragmentByTag(PICKER_TAG) as? MaterialDatePicker<*>)?.let {
            @Suppress("UNCHECKED_CAST")
            (it as MaterialDatePicker<Long>).addOnPositiveButtonClickListener { millis -> onDatePicked(birth, birthLayout, millis) }
        }

        fun submit() {
            val calendar = Calendar.getInstance()
            val today = SimpleDate(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DAY_OF_MONTH))
            val usernameText = username.text.toString().trim()
            val nameText = name.text.toString().trim()
            val surnameText = surname.text.toString().trim()
            val birthText = birth.text.toString().trim()
            val heightText = height.text.toString()
            val weightText = weight.text.toString()

            val checks = listOf(
                usernameLayout to Validators.validateUsername(usernameText),
                nameLayout to Validators.validateName(nameText),
                surnameLayout to Validators.validateSurname(surnameText),
                birthLayout to Validators.validateBirthDate(birthText, today),
                heightLayout to Validators.validateHeightCm(heightText),
                weightLayout to Validators.validateWeightKg(weightText)
            )
            checks.forEach { (layout, error) -> layout.error = error?.let { getString(it.messageRes()) } }
            val countryMissing = countries.none { it.first == viewModel.selectedCountry }
            countryLayout.error = if (countryMissing) getString(R.string.err_country_required) else null

            val firstBad = checks.firstOrNull { it.second != null }?.first ?: countryLayout.takeIf { countryMissing }
            if (firstBad != null) {
                Log.d(TAG, "Step 2 invalid")
                firstBad.requestFocus()
                return
            }
            viewModel.register(
                RegistrationData(
                    email = viewModel.pendingEmail,
                    password = viewModel.pendingPassword,
                    name = nameText,
                    surname = surnameText,
                    username = usernameText,
                    birthDate = birthText,
                    heightCm = Validators.parseDecimal(heightText),
                    weightKg = Validators.parseDecimal(weightText),
                    country = viewModel.selectedCountry
                )
            )
        }
        createButton.setOnClickListener { submit() }
        weight.setOnEditorActionListener { _, _, _ -> submit(); true }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.loading.collect { busy ->
                        setBusy(createButton, progress, busy, R.string.btn_create_account, R.string.msg_signing_up)
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is AuthEvent.LoggedIn -> {
                                viewModel.pendingPassword = ""
                                openMainApp()
                            }
                            is AuthEvent.Failed -> handleFailure(view, event.error)
                            AuthEvent.ResetSent -> Unit
                        }
                    }
                }
            }
        }
    }

    private fun handleFailure(root: View, error: AuthError) {
        when (error) {
            // The email lives on step 1, so go back there and mark the field.
            AuthError.EMAIL_IN_USE -> {
                viewModel.emailAlreadyUsed = true
                findNavController().popBackStack()
            }
            else -> UiMessages.error(root, error.messageRes)
        }
    }

    private fun openDatePicker(birth: TextInputEditText, birthLayout: TextInputLayout) {
        if (childFragmentManager.findFragmentByTag(PICKER_TAG) != null) return
        // Latest allowed birth date = exactly 13 years ago. Opens at 18 years ago.
        val latest = utcCalendar().apply { add(Calendar.YEAR, -Validators.MIN_AGE_YEARS) }.timeInMillis
        val opening = utcCalendar().apply { add(Calendar.YEAR, -18) }.timeInMillis
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.field_birth_date)
            .setSelection(opening)
            .setCalendarConstraints(
                CalendarConstraints.Builder()
                    .setEnd(latest)
                    .setOpenAt(opening)
                    .setValidator(DateValidatorPointBackward.before(latest + 1))
                    .build()
            )
            .build()
        picker.addOnPositiveButtonClickListener { millis -> onDatePicked(birth, birthLayout, millis) }
        picker.show(childFragmentManager, PICKER_TAG)
    }

    private fun onDatePicked(birth: TextInputEditText, birthLayout: TextInputLayout, millis: Long) {
        val calendar = utcCalendar().apply { timeInMillis = millis }
        birth.setText(
            String.format(Locale.US, "%04d-%02d-%02d", calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DAY_OF_MONTH))
        )
        birthLayout.error = null
    }

    private fun utcCalendar(): Calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))

    private companion object {
        const val TAG = "SignUpProfile"
        const val PICKER_TAG = "birth_date_picker"
    }
}
