package com.example.opsc.auth

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.example.opsc.R
import com.example.opsc.ui.countryList
import com.example.opsc.ui.setBusy
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import java.util.Locale

/** Shown after the first Google sign-in. Name and photo come from Google; the user picks a username and can add height, weight and country. */
class FinishProfileFragment : Fragment(R.layout.fragment_finish_profile) {
    private val viewModel: AuthViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val usernameLayout = view.findViewById<TextInputLayout>(R.id.finishUsernameLayout)
        val heightLayout = view.findViewById<TextInputLayout>(R.id.finishHeightLayout)
        val weightLayout = view.findViewById<TextInputLayout>(R.id.finishWeightLayout)
        val countryLayout = view.findViewById<TextInputLayout>(R.id.finishCountryLayout)
        val username = view.findViewById<TextInputEditText>(R.id.finishUsername)
        val height = view.findViewById<TextInputEditText>(R.id.finishHeight)
        val weight = view.findViewById<TextInputEditText>(R.id.finishWeight)
        val country = view.findViewById<MaterialAutoCompleteTextView>(R.id.finishCountry)
        val saveButton = view.findViewById<MaterialButton>(R.id.finishSave)
        val progress = view.findViewById<LinearProgressIndicator>(R.id.finishProgress)

        val suggestion = viewModel.repository.suggestedProfile()
        view.findViewById<TextView>(R.id.finishSubtitle).text =
            getString(R.string.finish_profile_subtitle, suggestion?.email.orEmpty())
        if (username.text.isNullOrEmpty()) username.setText(suggestion?.username.orEmpty())

        listOf(usernameLayout to username, heightLayout to height, weightLayout to weight)
            .forEach { (layout, edit) -> edit.doAfterTextChanged { layout.error = null } }

        val countries = countryList(Locale.getDefault())
        country.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, countries.map { it.second }))
        country.setText(countries.firstOrNull { it.first == viewModel.selectedCountry }?.second.orEmpty(), false)
        country.setOnItemClickListener { _, _, position, _ ->
            val shown = country.adapter.getItem(position) as String
            viewModel.selectedCountry = countries.first { it.second == shown }.first
            countryLayout.error = null
        }

        fun save() {
            val usernameText = username.text.toString().trim()
            val heightText = height.text.toString()
            val weightText = weight.text.toString()
            val checks = listOf(
                usernameLayout to Validators.validateUsername(usernameText),
                heightLayout to Validators.validateHeightCm(heightText),
                weightLayout to Validators.validateWeightKg(weightText)
            )
            checks.forEach { (layout, error) -> layout.error = error?.let { getString(it.messageRes()) } }
            val firstBad = checks.firstOrNull { it.second != null }?.first
            if (firstBad != null) {
                Log.d(TAG, "Finish-profile form invalid")
                firstBad.requestFocus()
                return
            }
            viewModel.finishProfile(
                username = usernameText,
                heightCm = Validators.parseDecimal(heightText),
                weightKg = Validators.parseDecimal(weightText),
                country = viewModel.selectedCountry
            )
        }
        saveButton.setOnClickListener { save() }
        weight.setOnEditorActionListener { _, _, _ -> save(); true }

        // Wrong account? Sign out and go to login.
        view.findViewById<View>(R.id.finishOtherAccount).setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                viewModel.repository.logout()
                GoogleSignInHelper.clearState(requireActivity())
                findNavController().navigate(
                    R.id.loginFragment, null,
                    navOptions { popUpTo(R.id.finishProfileFragment) { inclusive = true } }
                )
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.loading.collect { busy ->
                        setBusy(saveButton, progress, busy, R.string.btn_finish_profile, R.string.msg_saving)
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is AuthEvent.LoggedIn -> handleLoggedIn(event)
                            is AuthEvent.Failed -> showAuthFailure(view, event.error)
                            AuthEvent.ResetSent -> Unit
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val TAG = "FinishProfile"
    }
}
