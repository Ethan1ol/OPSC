package com.example.opsc

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
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
 * First sign-up step. The user enters an email and continues to the details form,
 * or signs up instantly with Google SSO.
 */
class signUpSection : AppCompatActivity() {
    private val repository = AuthRepository()
    private lateinit var emailInput: EditText
    private lateinit var signUpButton: Button
    private lateinit var googleLoginButton: Button
    private lateinit var loginText: TextView
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_sign_up)

        emailInput = findViewById(R.id.signUpEmailAddress)
        signUpButton = findViewById(R.id.signUpbutton)
        googleLoginButton = findViewById(R.id.button2)
        loginText = findViewById(R.id.textView4)

        // Carry over any email already typed on the login screen.
        intent.getStringExtra("USER_EMAIL")?.takeIf { it.isNotEmpty() }?.let { emailInput.setText(it) }

        signUpButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            Validators.validateEmail(email)?.let {
                emailInput.error = getString(it.messageRes())
                emailInput.requestFocus()
                return@setOnClickListener
            }
            startActivity(Intent(this, signUpDetailsActivity::class.java).putExtra("USER_EMAIL", email))
        }

        loginText.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        googleLoginButton.setOnClickListener {
            if (busy) return@setOnClickListener
            busy = true
            googleLoginButton.isEnabled = false
            lifecycleScope.launch {
                val outcome = GoogleSignInHelper.signIn(this@signUpSection, repository)
                busy = false
                googleLoginButton.isEnabled = true
                when (outcome) {
                    is AuthOutcome.Success -> {
                        startActivity(Intent(this@signUpSection, AppActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        })
                        finish()
                    }
                    is AuthOutcome.Failure ->
                        Toast.makeText(this@signUpSection, outcome.error.messageRes, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
