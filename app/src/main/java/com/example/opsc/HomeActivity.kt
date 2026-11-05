package com.example.opsc

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.opsc.auth.AuthRepository
import com.example.opsc.auth.GoogleSignInHelper
import kotlinx.coroutines.launch

/**
 * Landing screen after login. For now it greets the user from their Firestore profile and
 * offers Settings and Log out; the dashboard (clubs, routines, workouts) is added in later pieces.
 */
class HomeActivity : AppCompatActivity() {
    private val repository = AuthRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Guard: if the session is gone (e.g. account deleted), return to the login screen.
        val user = repository.currentUser
        if (user == null) {
            Log.w(TAG, "No signed-in user, returning to login")
            goToLogin()
            return
        }

        enableEdgeToEdge()
        setContentView(R.layout.activity_home)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val welcome = findViewById<TextView>(R.id.textWelcomeHome)
        welcome.text = getString(R.string.msg_welcome_user, user.displayName ?: user.email.orEmpty())
        lifecycleScope.launch {
            // Prefer the username saved in the Firestore profile once it loads.
            repository.loadProfile(user.uid)?.username?.takeIf { it.isNotEmpty() }?.let {
                welcome.text = getString(R.string.msg_welcome_user, it)
            }
        }

        findViewById<Button>(R.id.buttonSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<Button>(R.id.buttonLogout).setOnClickListener { logout() }
    }

    private fun logout() {
        lifecycleScope.launch {
            repository.logout()
            GoogleSignInHelper.clearState(this@HomeActivity)
            goToLogin()
        }
    }

    private fun goToLogin() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    private companion object {
        const val TAG = "HomeActivity"
    }
}
