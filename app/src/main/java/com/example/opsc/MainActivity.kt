package com.example.opsc

import android.content.Intent
import android.os.Bundle
import android.text.SpannableString
import android.text.style.UnderlineSpan
import android.util.Log
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.opsc.auth.AuthOutcome
import com.example.opsc.app.AppActivity
import com.example.opsc.auth.AuthRepository
import com.example.opsc.auth.GoogleSignInHelper
import com.example.opsc.auth.Validators
import com.example.opsc.auth.messageRes
import kotlinx.coroutines.launch

/**
 * Login screen. Signs in with email and password or Google SSO through [AuthRepository],
 * sends password reset emails, and skips straight to Home if a session already exists.
 */
class MainActivity : AppCompatActivity() {
    private val repository = AuthRepository()
    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var loginButton: Button
    private lateinit var googleButton: Button
    private lateinit var forgotPassword: TextView
    private lateinit var signUpText: TextView
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Session persistence: Firebase remembers the user between app launches.
        if (repository.currentUser != null) {
            Log.i(TAG, "Existing session found, going to Home")
            navigateToHome()
            return
        }

        setContentView(R.layout.activity_main)

        forgotPassword = findViewById(R.id.textView4)
        loginButton = findViewById(R.id.loginbutton)
        googleButton = findViewById(R.id.button2)
        signUpText = findViewById(R.id.textView7)
        passwordInput = findViewById(R.id.editTextTextPassword)
        emailInput = findViewById(R.id.editTextTextEmailAddress)

        forgotPassword.text = underline(getString(R.string.forgot_password))
        signUpText.text = underline(getString(R.string.signUp_link))

        forgotPassword.setOnClickListener { sendPasswordReset() }

        signUpText.setOnClickListener {
            val intent = Intent(this, signUpSection::class.java).apply {
                putExtra("USER_EMAIL", emailInput.text.toString().trim())
            }
            startActivity(intent)
        }

        loginButton.setOnClickListener { attemptLogin() }
        googleButton.setOnClickListener { attemptGoogleLogin() }

        passwordInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptLogin()
                true
            } else false
        }
        passwordInput.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                attemptLogin()
                true
            } else false
        }
    }

    private fun underline(text: String) = SpannableString(text).apply {
        setSpan(UnderlineSpan(), 0, length, 0)
    }

    private fun attemptLogin() {
        if (busy) return
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString()

        Validators.validateEmail(email)?.let {
            emailInput.error = getString(it.messageRes())
            emailInput.requestFocus()
            return
        }
        Validators.validateLoginPassword(password)?.let {
            passwordInput.error = getString(it.messageRes())
            passwordInput.requestFocus()
            return
        }

        setBusy(true, R.string.msg_logging_in)
        lifecycleScope.launch {
            val outcome = repository.login(email, password)
            setBusy(false)
            handleOutcome(outcome)
        }
    }

    private fun attemptGoogleLogin() {
        if (busy) return
        setBusy(true, R.string.msg_logging_in)
        lifecycleScope.launch {
            val outcome = GoogleSignInHelper.signIn(this@MainActivity, repository)
            setBusy(false)
            handleOutcome(outcome)
        }
    }

    private fun sendPasswordReset() {
        if (busy) return
        val email = emailInput.text.toString().trim()
        if (Validators.validateEmail(email) != null) {
            Toast.makeText(this, R.string.msg_enter_email_first, Toast.LENGTH_LONG).show()
            emailInput.requestFocus()
            return
        }
        setBusy(true, R.string.msg_logging_in)
        lifecycleScope.launch {
            val outcome = repository.sendPasswordReset(email)
            setBusy(false)
            when (outcome) {
                is AuthOutcome.Success -> Toast.makeText(this@MainActivity, R.string.msg_reset_sent, Toast.LENGTH_LONG).show()
                is AuthOutcome.Failure -> Toast.makeText(this@MainActivity, outcome.error.messageRes, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun handleOutcome(outcome: AuthOutcome) {
        when (outcome) {
            is AuthOutcome.Success -> navigateToHome()
            is AuthOutcome.Failure -> Toast.makeText(this, outcome.error.messageRes, Toast.LENGTH_LONG).show()
        }
    }

    /** Disables the buttons while a request is running so the user cannot double-submit. */
    private fun setBusy(isBusy: Boolean, labelRes: Int = R.string.login) {
        busy = isBusy
        loginButton.isEnabled = !isBusy
        googleButton.isEnabled = !isBusy
        loginButton.setText(if (isBusy) labelRes else R.string.login)
    }

    private fun navigateToHome() {
        val intent = Intent(this, AppActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}
