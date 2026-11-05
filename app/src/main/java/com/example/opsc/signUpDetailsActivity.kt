package com.example.opsc

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.opsc.auth.AuthOutcome
import com.example.opsc.app.AppActivity
import com.example.opsc.auth.AuthRepository
import com.example.opsc.auth.FormField
import com.example.opsc.auth.RegistrationData
import com.example.opsc.auth.SimpleDate
import com.example.opsc.auth.Validators
import com.example.opsc.auth.messageRes
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Second sign-up step. Collects name, surname, birth date, username and password,
 * validates every field at once, then creates the Firebase account and Firestore profile.
 */
class signUpDetailsActivity : AppCompatActivity() {
    private val repository = AuthRepository()
    private lateinit var emailDisplay: EditText
    private lateinit var nameInput: EditText
    private lateinit var surnameInput: EditText
    private lateinit var usernameInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var birthDateInput: EditText
    private lateinit var confirmedPasswordInput: EditText
    private lateinit var signUpButton: Button
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_sign_up_details)
        emailDisplay = findViewById(R.id.textEmailDetail)
        nameInput = findViewById(R.id.textNameDetail)
        surnameInput = findViewById(R.id.textSurnameDetail)
        birthDateInput = findViewById(R.id.textBirthDate)
        usernameInput = findViewById(R.id.textUserName)
        passwordInput = findViewById(R.id.textPasswordDetails)
        confirmedPasswordInput = findViewById(R.id.textConfirmPassword)
        signUpButton = findViewById(R.id.signUpbutton)

        emailDisplay.setText(intent.getStringExtra("USER_EMAIL").orEmpty())

        // Birth date is chosen with a date picker rather than typed, so the format is always valid.
        birthDateInput.isFocusable = false
        birthDateInput.isFocusableInTouchMode = false
        birthDateInput.setOnClickListener { showDate() }

        signUpButton.setOnClickListener { validateAndSubmit() }
    }

    private fun showDate() {
        val calendar = Calendar.getInstance()
        val picker = DatePickerDialog(
            this,
            { _, year, monthOfYear, dayOfMonth ->
                birthDateInput.setText(String.format("%04d-%02d-%02d", year, monthOfYear + 1, dayOfMonth))
                birthDateInput.error = null
            },
            calendar.get(Calendar.YEAR) - 18,
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        picker.datePicker.maxDate = calendar.timeInMillis
        picker.show()
    }

    private fun validateAndSubmit() {
        if (busy) return
        val data = RegistrationData(
            email = emailDisplay.text.toString().trim(),
            password = passwordInput.text.toString(),
            name = nameInput.text.toString().trim(),
            surname = surnameInput.text.toString().trim(),
            username = usernameInput.text.toString().trim(),
            birthDate = birthDateInput.text.toString().trim()
        )
        val cal = Calendar.getInstance()
        val today = SimpleDate(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))

        val errors = Validators.validateRegistration(
            data.email, data.name, data.surname, data.birthDate,
            data.username, data.password, confirmedPasswordInput.text.toString(), today
        )
        // Clear old errors, then show every current one.
        listOf(emailDisplay, nameInput, surnameInput, birthDateInput, usernameInput, passwordInput, confirmedPasswordInput)
            .forEach { it.error = null }
        if (errors.isNotEmpty()) {
            Log.d(TAG, "Registration form invalid: ${errors.keys}")
            var firstBad: EditText? = null
            for ((field, error) in errors) {
                val view = viewFor(field)
                view.error = getString(error.messageRes())
                if (firstBad == null) firstBad = view
            }
            firstBad?.requestFocus()
            return
        }

        busy = true
        signUpButton.isEnabled = false
        signUpButton.setText(R.string.msg_signing_up)
        lifecycleScope.launch {
            val outcome = repository.register(data)
            busy = false
            signUpButton.isEnabled = true
            signUpButton.setText(R.string.signupButton)
            when (outcome) {
                is AuthOutcome.Success -> {
                    Toast.makeText(this@signUpDetailsActivity, getString(R.string.msg_welcome_user, data.username), Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@signUpDetailsActivity, AppActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                    finish()
                }
                is AuthOutcome.Failure ->
                    Toast.makeText(this@signUpDetailsActivity, outcome.error.messageRes, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun viewFor(field: FormField): EditText = when (field) {
        FormField.EMAIL -> emailDisplay
        FormField.NAME -> nameInput
        FormField.SURNAME -> surnameInput
        FormField.BIRTH_DATE -> birthDateInput
        FormField.USERNAME -> usernameInput
        FormField.PASSWORD -> passwordInput
        FormField.CONFIRM_PASSWORD -> confirmedPasswordInput
    }

    private companion object {
        const val TAG = "SignUpDetails"
    }
}
