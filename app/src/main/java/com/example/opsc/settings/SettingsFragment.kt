package com.example.opsc.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.opsc.BuildConfig
import com.example.opsc.R
import com.example.opsc.auth.AuthActivity
import com.example.opsc.auth.AuthError
import com.example.opsc.auth.GoogleSignInHelper
import com.example.opsc.auth.UserProfile
import com.example.opsc.auth.Validators
import com.example.opsc.auth.logoutToLogin
import com.example.opsc.auth.messageRes
import com.example.opsc.auth.showAuthFailure
import com.example.opsc.profile.LanguageManager
import com.example.opsc.profile.ProfileValidators
import com.example.opsc.profile.UnitConverter
import com.example.opsc.profile.Units
import com.example.opsc.ui.ConfirmDialog
import com.example.opsc.ui.ConnectivityMonitor
import com.example.opsc.ui.StateLayout
import com.example.opsc.ui.ThemeManager
import com.example.opsc.ui.ThemeMode
import com.example.opsc.ui.UiMessages
import com.example.opsc.ui.countryList
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Settings screen in three cards: Account (profile fields, change password, log out, delete
 * account), Preferences (language, light/dark, units, push notifications) and About (version,
 * credits, privacy policy). Preferences save as soon as they are changed; account fields save
 * with the Save button after validation. Everything goes to the user's Firestore profile.
 */
class SettingsFragment : Fragment(R.layout.fragment_settings) {
    private val viewModel: SettingsViewModel by viewModels()
    private lateinit var countries: List<Pair<String, String>>

    private lateinit var stateLayout: StateLayout
    private lateinit var usernameLayout: TextInputLayout
    private lateinit var heightLayout: TextInputLayout
    private lateinit var weightLayout: TextInputLayout
    private lateinit var countryLayout: TextInputLayout
    private lateinit var username: TextInputEditText
    private lateinit var height: TextInputEditText
    private lateinit var weight: TextInputEditText
    private lateinit var country: MaterialAutoCompleteTextView
    private lateinit var unitsToggle: MaterialButtonToggleGroup
    private lateinit var pushSwitch: MaterialSwitch

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        stateLayout = view.findViewById(R.id.settingsState)
        usernameLayout = view.findViewById(R.id.settingsUsernameLayout)
        heightLayout = view.findViewById(R.id.settingsHeightLayout)
        weightLayout = view.findViewById(R.id.settingsWeightLayout)
        countryLayout = view.findViewById(R.id.settingsCountryLayout)
        username = view.findViewById(R.id.settingsUsername)
        height = view.findViewById(R.id.settingsHeight)
        weight = view.findViewById(R.id.settingsWeight)
        country = view.findViewById(R.id.settingsCountry)
        unitsToggle = view.findViewById(R.id.settingsUnitsToggle)
        pushSwitch = view.findViewById(R.id.settingsPush)

        listOf(usernameLayout to username, heightLayout to height, weightLayout to weight)
            .forEach { (layout, edit) -> edit.doAfterTextChanged { layout.error = null } }

        setUpCountry()
        setUpLanguage(view)
        setUpTheme(view)
        setUpAbout(view)
        setUpAccountButtons(view)

        // The password typed in the delete-account dialog comes back here.
        childFragmentManager.setFragmentResultListener(PasswordPromptDialog.REQUEST_KEY, viewLifecycleOwner) { _, bundle ->
            bundle.getString(PasswordPromptDialog.RESULT_PASSWORD)?.let { viewModel.deleteWithPassword(it) }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect { render(it, view) } }
                launch { viewModel.events.collect { handle(it, view) } }
                launch {
                    viewModel.busy.collect { busy ->
                        if (busy) stateLayout.showLoading()
                        else if (viewModel.state.value is SettingsState.Ready) stateLayout.showContent()
                    }
                }
            }
        }
    }

    // ---------- loading states ----------

    private fun render(state: SettingsState, root: View) {
        when (state) {
            SettingsState.Loading -> stateLayout.showLoading()
            SettingsState.Failed -> {
                if (ConnectivityMonitor(requireContext()).isOnlineNow()) stateLayout.showError { viewModel.load() }
                else stateLayout.showOffline { viewModel.load() }
            }
            is SettingsState.Ready -> {
                fill(state.profile)
                if (!viewModel.busy.value) stateLayout.showContent()
            }
        }
    }

    /** Fills the form from the profile, once; later renders (rotation) keep what the user typed. */
    private fun fill(profile: UserProfile) {
        if (!viewModel.fieldsFilled) {
            username.setText(profile.username)
            height.setText(profile.heightCm?.let { UnitConverter.format(UnitConverter.cmToDisplay(it, viewModel.units)) }.orEmpty())
            weight.setText(profile.weightKg?.let { UnitConverter.format(UnitConverter.kgToDisplay(it, viewModel.units)) }.orEmpty())
            viewModel.fieldsFilled = true
        }
        applyUnitLabels()
        country.setText(countries.firstOrNull { it.first == viewModel.country }?.second.orEmpty(), false)
        setUpUnitsToggle()
        setUpPush()
    }

    // ---------- account ----------

    private fun setUpCountry() {
        countries = countryList(Locale.getDefault())
        country.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, countries.map { it.second }))
        country.setOnItemClickListener { _, _, position, _ ->
            val shown = country.adapter.getItem(position) as String
            viewModel.country = countries.first { it.second == shown }.first
            countryLayout.error = null
        }
    }

    private fun setUpAccountButtons(view: View) {
        val changePassword = view.findViewById<MaterialButton>(R.id.settingsChangePassword)
        val googleNote = view.findViewById<TextView>(R.id.settingsGoogleNote)
        if (viewModel.usesPassword) {
            changePassword.setOnClickListener { findNavController().navigate(R.id.changePasswordFragment) }
        } else {
            changePassword.visibility = View.GONE
            googleNote.visibility = View.VISIBLE
        }

        view.findViewById<View>(R.id.settingsSave).setOnClickListener { saveAccount() }
        weight.setOnEditorActionListener { _, _, _ -> saveAccount(); true }

        view.findViewById<View>(R.id.settingsLogout).setOnClickListener {
            ConfirmDialog.show(requireContext(), R.string.logout_title, R.string.logout_message, R.string.btn_logout) { logoutToLogin() }
        }
        view.findViewById<View>(R.id.settingsDelete).setOnClickListener {
            ConfirmDialog.show(requireContext(), R.string.delete_title, R.string.delete_message, R.string.btn_continue) { startDelete() }
        }
    }

    private fun saveAccount() {
        val usernameText = username.text.toString().trim()
        val heightText = height.text.toString()
        val weightText = weight.text.toString()
        val units = viewModel.units
        val checks = listOf(
            usernameLayout to Validators.validateUsername(usernameText),
            heightLayout to ProfileValidators.validateHeight(heightText, units),
            weightLayout to ProfileValidators.validateWeight(weightText, units)
        )
        checks.forEach { (layout, error) -> layout.error = error?.let { getString(it.messageRes()) } }
        val countryMissing = countries.none { it.first == viewModel.country }
        countryLayout.error = if (countryMissing) getString(R.string.err_country_required) else null
        val firstBad = checks.firstOrNull { it.second != null }?.first ?: countryLayout.takeIf { countryMissing }
        if (firstBad != null) {
            Log.d(TAG, "Settings invalid")
            firstBad.requestFocus()
            return
        }
        viewModel.saveAccount(
            username = usernameText,
            heightCm = ProfileValidators.heightToCm(heightText, units),
            weightKg = ProfileValidators.weightToKg(weightText, units),
            country = viewModel.country
        )
    }

    /** Deleting needs the user to prove who they are first: password users type it, Google users pick their account again. */
    private fun startDelete() {
        if (viewModel.usesPassword) {
            if (childFragmentManager.findFragmentByTag(PasswordPromptDialog.TAG) == null) {
                PasswordPromptDialog().show(childFragmentManager, PasswordPromptDialog.TAG)
            }
        } else {
            viewLifecycleOwner.lifecycleScope.launch {
                when (val result = GoogleSignInHelper.fetchIdToken(requireActivity())) {
                    is GoogleSignInHelper.TokenResult.Token -> viewModel.deleteWithGoogle(result.idToken)
                    is GoogleSignInHelper.TokenResult.Error -> viewModel.reportFailure(result.error)
                }
            }
        }
    }

    // ---------- preferences ----------

    private fun setUpLanguage(root: View) {
        val language = root.findViewById<MaterialAutoCompleteTextView>(R.id.settingsLanguage)
        val names = listOf(R.string.lang_english, R.string.lang_afrikaans, R.string.lang_zulu).map { getString(it) }
        language.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, names))
        language.setText(names[LanguageManager.SUPPORTED_CODES.indexOf(LanguageManager.current()).coerceAtLeast(0)], false)
        language.setOnItemClickListener { _, _, position, _ ->
            val code = LanguageManager.SUPPORTED_CODES[position]
            // Save first: switching language recreates the screen straight away.
            viewModel.savePreference(mapOf("language" to code))
            LanguageManager.apply(code)
        }
    }

    private fun setUpTheme(root: View) {
        val toggle = root.findViewById<MaterialButtonToggleGroup>(R.id.settingsThemeToggle)
        val ids = mapOf(ThemeMode.DARK to R.id.themeDark, ThemeMode.LIGHT to R.id.themeLight, ThemeMode.SYSTEM to R.id.themeSystem)
        toggle.check(ids.getValue(ThemeManager.current(requireContext())))
        toggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            val mode = ids.entries.firstOrNull { it.value == checkedId }?.key
            if (isChecked && mode != null && mode != ThemeManager.current(requireContext())) {
                ThemeManager.set(requireContext(), mode)
            }
        }
    }

    private fun setUpUnitsToggle() {
        unitsToggle.clearOnButtonCheckedListeners()
        unitsToggle.check(if (viewModel.units == Units.METRIC) R.id.unitsMetric else R.id.unitsImperial)
        unitsToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val newUnits = if (checkedId == R.id.unitsMetric) Units.METRIC else Units.IMPERIAL
            if (newUnits == viewModel.units) return@addOnButtonCheckedListener
            // Convert what is typed so the same person is still shown, then remember the choice.
            height.setText(UnitConverter.convertTypedHeight(height.text.toString(), viewModel.units, newUnits))
            weight.setText(UnitConverter.convertTypedWeight(weight.text.toString(), viewModel.units, newUnits))
            viewModel.units = newUnits
            applyUnitLabels()
            viewModel.savePreference(mapOf("units" to newUnits.prefValue))
        }
    }

    private fun applyUnitLabels() {
        val metric = viewModel.units == Units.METRIC
        heightLayout.hint = getString(if (metric) R.string.field_height else R.string.field_height_in)
        weightLayout.hint = getString(if (metric) R.string.field_weight else R.string.field_weight_lb)
    }

    private fun setUpPush() {
        pushSwitch.setOnCheckedChangeListener(null)
        pushSwitch.isChecked = viewModel.pushEnabled
        pushSwitch.setOnCheckedChangeListener { _, checked ->
            viewModel.pushEnabled = checked
            viewModel.savePreference(mapOf("pushEnabled" to checked))
        }
    }

    // ---------- about ----------

    private fun setUpAbout(root: View) {
        root.findViewById<TextView>(R.id.settingsVersion).text =
            getString(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE.toString())
        root.findViewById<View>(R.id.settingsPrivacy).setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.privacy_policy_url))))
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "No app can open the privacy policy link", e)
                UiMessages.error(root, R.string.err_open_link)
            }
        }
    }

    // ---------- results ----------

    private fun handle(event: SettingsEvent, root: View) {
        when (event) {
            is SettingsEvent.Saved ->
                UiMessages.success(root, if (event.offline) R.string.msg_saved_offline else R.string.msg_settings_saved)
            SettingsEvent.AccountDeleted -> {
                Toast.makeText(requireContext(), R.string.msg_account_deleted, Toast.LENGTH_LONG).show()
                startActivity(Intent(requireContext(), AuthActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                requireActivity().finish()
            }
            SettingsEvent.PasswordChanged -> Unit
            is SettingsEvent.Failed -> showAuthFailure(root, event.error)
        }
    }

    private companion object {
        const val TAG = "SettingsFragment"
    }
}
