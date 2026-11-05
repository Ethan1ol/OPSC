package com.example.opsc.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navOptions
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.example.opsc.R
import com.example.opsc.app.AppActivity
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.launch

/**
 * The first screen of the app (launcher). If the user is already signed in it goes straight
 * to the main app; otherwise it hosts the login, two-step sign-up and password-reset screens.
 */
class AuthActivity : AppCompatActivity() {
    private lateinit var appBarConfiguration: AppBarConfiguration

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Session persistence: Firebase remembers the user between launches. If we already know this
        // user's profile exists, go straight into the app; otherwise check it before showing anything.
        val repository = AuthRepository()
        val signedInUid = repository.currentUser?.uid
        if (signedInUid != null && ProfileGate.isKnownComplete(this, signedInUid)) {
            Log.i(TAG, "Existing session with a known profile, opening the app")
            openApp(remember = false)
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
        appBarConfiguration = AppBarConfiguration(setOf(R.id.loginFragment, R.id.finishProfileFragment))
        setupActionBarWithNavController(navController, appBarConfiguration)

        // The login screen has its own big title, so the top bar is only shown on the other screens.
        val appBar = findViewById<AppBarLayout>(R.id.authAppBar)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            appBar.visibility = if (destination.id == R.id.loginFragment) View.GONE else View.VISIBLE
        }

        // Signed in, but we have not confirmed the profile on this phone: check it, hiding the
        // screen meanwhile so the login form does not flash.
        if (signedInUid != null) {
            val root = findViewById<View>(R.id.authRoot)
            root.visibility = View.INVISIBLE
            lifecycleScope.launch {
                val status = repository.profileStatus(signedInUid)
                val route = routeFor(status)
                Log.i(TAG, "Profile check for launch: status=$status route=$route")
                when (route) {
                    ProfileRoute.OPEN_APP -> {
                        // Remember it only when confirmed; an "unknown" result (offline) is retried next launch.
                        openApp(remember = status == ProfileStatus.EXISTS)
                    }
                    ProfileRoute.FINISH_PROFILE -> {
                        if (navController.currentDestination?.id != R.id.finishProfileFragment) {
                            navController.navigate(
                                R.id.finishProfileFragment, null,
                                navOptions { popUpTo(R.id.loginFragment) { inclusive = true } }
                            )
                        }
                        root.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun openApp(remember: Boolean) {
        if (remember) AuthRepository().currentUser?.uid?.let { ProfileGate.markComplete(this, it) }
        // Keep any extras (a tapped notification may say which club to open).
        startActivity(Intent(this, AppActivity::class.java).apply { intent?.extras?.let { putExtras(it) } })
        finish()
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = (supportFragmentManager.findFragmentById(R.id.authNavHost) as NavHostFragment).navController
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    private companion object {
        const val TAG = "AuthActivity"
    }
}
