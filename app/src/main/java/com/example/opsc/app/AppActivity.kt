package com.example.opsc.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.opsc.MainActivity
import com.example.opsc.R
import com.example.opsc.auth.AuthRepository
import com.example.opsc.ui.ConnectivityMonitor
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch

/**
 * The main shell of the app after login: a top app bar, an offline banner, the current
 * screen (a fragment hosted by the Navigation component) and a bottom navigation bar
 * with Home, Clubs, Workouts and Profile. Back always returns to Home first, then exits.
 */
class AppActivity : AppCompatActivity() {
    private lateinit var appBarConfiguration: AppBarConfiguration

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Without a signed-in user there is nothing to show: go to the login screen.
        if (AuthRepository().currentUser == null) {
            Log.w(TAG, "No signed-in user, returning to login")
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
            return
        }

        enableEdgeToEdge()
        setContentView(R.layout.activity_app)
        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))

        val navHost = supportFragmentManager.findFragmentById(R.id.navHost) as NavHostFragment
        val navController = navHost.navController
        // The four tabs are "top level": they show a title but no back arrow.
        appBarConfiguration = AppBarConfiguration(
            setOf(R.id.homeFragment, R.id.clubsFragment, R.id.workoutsFragment, R.id.profileFragment)
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        findViewById<BottomNavigationView>(R.id.bottomNav).setupWithNavController(navController)

        watchConnectivity(findViewById(R.id.offlineBanner))
    }

    /** Shows the offline banner whenever the phone has no internet. */
    private fun watchConnectivity(banner: TextView) {
        val monitor = ConnectivityMonitor(this)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                monitor.isOnline.collect { online ->
                    Log.d(TAG, "Connectivity changed: online=$online")
                    banner.visibility = if (online) View.GONE else View.VISIBLE
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = (supportFragmentManager.findFragmentById(R.id.navHost) as NavHostFragment).navController
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    private companion object {
        const val TAG = "AppActivity"
    }
}
