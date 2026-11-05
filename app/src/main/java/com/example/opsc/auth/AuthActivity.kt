package com.example.opsc.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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

/** First screen (launcher). Goes straight to the app if signed in, otherwise shows login, sign-up and password reset. */
class AuthActivity : AppCompatActivity() {
    private lateinit var appBarConfiguration: AppBarConfiguration

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate.
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Firebase remembers the user. If their profile is already known, open the app straight away.
        val repository = AuthRepository()
        val signedInUid = repository.currentUser?.uid
        if (signedInUid != null && ProfileGate.isKnownComplete(this, signedInUid)) {
            Log.i(TAG, "Existing session with a known profile, opening the app")
            openApp(remember = false)
            return
        }

        enableEdgeToEdge()
        setContentView(R.layout.activity_auth)

        // Keep content clear of the system bars and the keyboard.
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

        // Login has its own title, so hide the top bar there.
        val appBar = findViewById<AppBarLayout>(R.id.authAppBar)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            appBar.visibility = if (destination.id == R.id.loginFragment) View.GONE else View.VISIBLE
        }

        // Signed in but the profile is not checked yet: check it while hiding the screen.
        if (signedInUid != null) {
            val root = findViewById<View>(R.id.authRoot)
            root.visibility = View.INVISIBLE
            lifecycleScope.launch {
                val status = repository.profileStatus(signedInUid)
                val route = routeFor(status)
                Log.i(TAG, "Profile check for launch: status=$status route=$route")
                when (route) {
                    ProfileRoute.OPEN_APP -> {
                        // Only remember a confirmed profile. An offline result is checked again next launch.
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
        // Pass on extras (a notification may name a club to open).
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
