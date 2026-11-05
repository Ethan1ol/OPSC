package com.example.opsc

import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.opsc.auth.AuthRepository
import com.example.opsc.auth.Validators
import com.example.opsc.auth.messageRes
import com.example.opsc.profile.LanguageManager
import com.example.opsc.profile.ProfileRepository
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Settings screen: choose the app language, country and push-notification preference,
 * and edit name, username, height and weight. Changes are validated, then saved to Firestore
 * (queued automatically when offline). The chosen language is applied straight away.
 */
class SettingsActivity : AppCompatActivity() {
    private val profiles = ProfileRepository()
    private val auth = AuthRepository()

    private lateinit var languageSpinner: Spinner
    private lateinit var countrySpinner: Spinner
    private lateinit var pushSwitch: SwitchCompat
    private lateinit var nameInput: EditText
    private lateinit var surnameInput: EditText
    private lateinit var usernameInput: EditText
    private lateinit var heightInput: EditText
    private lateinit var weightInput: EditText
    private lateinit var saveButton: Button

    /** Country list as (ISO code, name in the current app language), sorted by name. */
    private lateinit var countries: List<Pair<String, String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val user = auth.currentUser
        if (user == null) {
            finish()
            return
        }

        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        languageSpinner = findViewById(R.id.spinnerLanguage)
        countrySpinner = findViewById(R.id.spinnerCountry)
        pushSwitch = findViewById(R.id.switchPush)
        nameInput = findViewById(R.id.inputName)
        surnameInput = findViewById(R.id.inputSurname)
        usernameInput = findViewById(R.id.inputUsername)
        heightInput = findViewById(R.id.inputHeight)
        weightInput = findViewById(R.id.inputWeight)
        saveButton = findViewById(R.id.buttonSave)
        findViewById<TextView>(R.id.buttonBack).setOnClickListener { finish() }

        setUpSpinners()

        // Only fetch on first open; after a rotation or language change Android restores the typed values.
        if (savedInstanceState == null) loadProfile(user.uid)

        saveButton.setOnClickListener { save(user.uid) }
    }

    private fun setUpSpinners() {
        val languageNames = listOf(R.string.lang_english, R.string.lang_afrikaans, R.string.lang_zulu).map { getString(it) }
        languageSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, languageNames)
        languageSpinner.setSelection(LanguageManager.SUPPORTED_CODES.indexOf(LanguageManager.current()).coerceAtLeast(0))

        val locale = Locale.getDefault()
        countries = Locale.getISOCountries()
            .map { code -> code to Locale("", code).getDisplayCountry(locale) }
            .filter { it.second.isNotBlank() }
            .sortedBy { it.second }
        countrySpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, countries.map { it.second }
        )
        selectCountry(DEFAULT_COUNTRY)
    }

    private fun selectCountry(code: String) {
        val index = countries.indexOfFirst { it.first.equals(code, ignoreCase = true) }
        if (index >= 0) countrySpinner.setSelection(index)
    }

    private fun loadProfile(uid: String) {
        lifecycleScope.launch {
            val profile = profiles.load(uid)
            if (profile == null) {
                Log.w(TAG, "No profile loaded; leaving fields empty")
                return@launch
            }
            nameInput.setText(profile.name)
            surnameInput.setText(profile.surname)
            usernameInput.setText(profile.username)
            heightInput.setText(profile.heightCm?.let { trimZero(it) }.orEmpty())
            weightInput.setText(profile.weightKg?.let { trimZero(it) }.orEmpty())
            pushSwitch.isChecked = profile.pushEnabled
            if (profile.country.isNotEmpty()) selectCountry(profile.country)
        }
    }

    private fun save(uid: String) {
        listOf(nameInput, surnameInput, usernameInput, heightInput, weightInput).forEach { it.error = null }

        val checks = listOf(
            nameInput to Validators.validateName(nameInput.text.toString()),
            surnameInput to Validators.validateSurname(surnameInput.text.toString()),
            usernameInput to Validators.validateUsername(usernameInput.text.toString()),
            heightInput to Validators.validateHeightCm(heightInput.text.toString()),
            weightInput to Validators.validateWeightKg(weightInput.text.toString())
        ).filter { it.second != null }

        if (checks.isNotEmpty()) {
            Log.d(TAG, "Settings invalid: ${checks.size} field(s)")
            checks.forEach { (view, error) -> view.error = getString(error!!.messageRes()) }
            checks.first().first.requestFocus()
            return
        }

        val languageCode = LanguageManager.SUPPORTED_CODES[languageSpinner.selectedItemPosition]
        val countryCode = countries.getOrNull(countrySpinner.selectedItemPosition)?.first.orEmpty()
        profiles.save(
            uid, mapOf(
                "name" to nameInput.text.toString().trim(),
                "surname" to surnameInput.text.toString().trim(),
                "username" to usernameInput.text.toString().trim(),
                "heightCm" to Validators.parseDecimal(heightInput.text.toString()),
                "weightKg" to Validators.parseDecimal(weightInput.text.toString()),
                "country" to countryCode,
                "language" to languageCode,
                "pushEnabled" to pushSwitch.isChecked
            )
        )
        Toast.makeText(this, R.string.msg_saved, Toast.LENGTH_LONG).show()
        // Applying a new language recreates this screen, so do it last.
        LanguageManager.apply(languageCode)
    }

    /** 175.0 -> "175", 72.5 -> "72.5". */
    private fun trimZero(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

    private companion object {
        const val TAG = "SettingsActivity"
        const val DEFAULT_COUNTRY = "ZA"
    }
}
