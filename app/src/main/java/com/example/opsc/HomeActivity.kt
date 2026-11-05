package com.example.opsc

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.opsc.auth.AuthRepository
import com.example.opsc.auth.GoogleSignInHelper
import com.example.opsc.profile.LanguageManager
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
            val profile = repository.loadProfile(user.uid) ?: return@launch
            profile.username.takeIf { it.isNotEmpty() }?.let {
                welcome.text = getString(R.string.msg_welcome_user, it)
            }
            // Restore the language saved in the profile (e.g. after logging in on a new phone).
            if (profile.language.isNotEmpty()) LanguageManager.apply(profile.language)
        }

        // Dashboard tiles. Screens that are not built yet open the "Coming soon" page.
        findViewById<View>(R.id.tileSettings).setOnClickListener { open(SettingsActivity::class.java) }
        comingSoon(R.id.tileWorkouts, R.string.tile_workouts)
        comingSoon(R.id.tileExercises, R.string.tile_exercises)
        comingSoon(R.id.tileClubs, R.string.tile_clubs)
        comingSoon(R.id.tileProfile, R.string.tile_profile)
        comingSoon(R.id.tileNotifications, R.string.tile_notifications)
        findViewById<View>(R.id.buttonLogout).setOnClickListener { logout() }
    }

    private fun open(target: Class<*>) = startActivity(Intent(this, target))

    private fun comingSoon(tileId: Int, titleRes: Int) {
        findViewById<View>(tileId).setOnClickListener {
            startActivity(Intent(this, FeaturePlaceholderActivity::class.java)
                .putExtra(FeaturePlaceholderActivity.EXTRA_TITLE_RES, titleRes))
        }
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
