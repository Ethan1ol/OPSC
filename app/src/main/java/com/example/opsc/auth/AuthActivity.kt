package com.example.opsc.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.example.opsc.R
import com.example.opsc.app.AppActivity
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar

/**
 * The first screen of the app (launcher). If the user is already signed in it goes straight
 * to the main app; otherwise it hosts the login, two-step sign-up and password-reset screens.
 */
class AuthActivity : AppCompatActivity() {
    private lateinit var appBarConfiguration: AppBarConfiguration

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Session persistence: Firebase remembers the user between launches.
        if (AuthRepository().currentUser != null) {
            Log.i(TAG, "Existing session found, opening the app")
            startActivity(Intent(this, AppActivity::class.java))
            finish()
            return
        }

        enableEdgeToEdge()
        setContentView(R.layout.activity_auth)

        // Keep content clear of the system bars and lift it above the keyboard.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.authRoot)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            insets
        }

        setSupportActionBar(findViewById<MaterialToolbar>(R.id.authToolbar))
        val navController = (supportFragmentManager.findFragmentById(R.id.authNavHost) as NavHostFragment).navController
        appBarConfiguration = AppBarConfiguration(setOf(R.id.loginFragment))
        setupActionBarWithNavController(navController, appBarConfiguration)

        // The login screen has its own big title, so the top bar is only shown on the other screens.
        val appBar = findViewById<AppBarLayout>(R.id.authAppBar)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            appBar.visibility = if (destination.id == R.id.loginFragment) View.GONE else View.VISIBLE
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = (supportFragmentManager.findFragmentById(R.id.authNavHost) as NavHostFragment).navController
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    private companion object {
        const val TAG = "AuthActivity"
    }
}
