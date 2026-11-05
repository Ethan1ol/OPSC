package com.example.opsc.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.opsc.R
import com.example.opsc.auth.AuthActivity
import com.example.opsc.auth.AuthRepository
import com.example.opsc.notifications.NotificationHelper
import com.example.opsc.notifications.NotificationLogic
import com.example.opsc.notifications.NotificationsViewModel
import com.example.opsc.notifications.PushTokenManager
import com.example.opsc.ui.ConnectivityMonitor
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.badge.BadgeUtils
import com.google.android.material.badge.ExperimentalBadgeUtils
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch

/**
 * The main shell of the app after login: a top app bar (with the notification bell and its unread
 * badge), an offline banner, the current screen (a fragment hosted by the Navigation component) and
 * a bottom navigation bar with Home, Clubs, Workouts and Profile. Back always returns to Home first,
 * then exits. It also opens the right screen when a notification is tapped.
 */
@androidx.annotation.OptIn(ExperimentalBadgeUtils::class)
class AppActivity : AppCompatActivity() {
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var navController: NavController
    private val notifications: NotificationsViewModel by viewModels()
    private var badge: BadgeDrawable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Without a signed-in user there is nothing to show: go to the login screen.
        if (AuthRepository().currentUser == null) {
            Log.w(TAG, "No signed-in user, returning to login")
            startActivity(Intent(this, AuthActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
            return
        }

        enableEdgeToEdge()
        setContentView(R.layout.activity_app)
        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))

        val navHost = supportFragmentManager.findFragmentById(R.id.navHost) as NavHostFragment
        navController = navHost.navController
        // The four tabs are "top level": they show a title but no back arrow.
        appBarConfiguration = AppBarConfiguration(
            setOf(R.id.homeFragment, R.id.clubsFragment, R.id.workoutsFragment, R.id.profileFragment)
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        findViewById<BottomNavigationView>(R.id.bottomNav).setupWithNavController(navController)

        watchConnectivity(findViewById(R.id.offlineBanner))

        // Keep this phone's push token on the user's profile (when notifications are on).
        lifecycleScope.launch { PushTokenManager.register(this@AppActivity) }

        // Opened by tapping a notification? Go to the right screen.
        if (savedInstanceState == null) handleNotificationIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    /** A tapped notification carries the club id (open that club) or just a type (open the list). */
    private fun handleNotificationIntent(intent: Intent?) {
        val clubId = intent?.getStringExtra(NotificationHelper.EXTRA_CLUB_ID)
        val type = intent?.getStringExtra(NotificationHelper.EXTRA_TYPE)
        when {
            !clubId.isNullOrBlank() -> navController.navigate(R.id.clubDetailFragment, bundleOf("clubId" to clubId))
            !type.isNullOrBlank() -> navController.navigate(R.id.notificationsFragment)
        }
        intent?.removeExtra(NotificationHelper.EXTRA_CLUB_ID)
        intent?.removeExtra(NotificationHelper.EXTRA_TYPE)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.app_toolbar_menu, menu)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        // The badge can only be attached after the toolbar has laid out its menu.
        toolbar.post {
            val newBadge = BadgeDrawable.create(this)
            BadgeUtils.attachBadgeDrawable(newBadge, toolbar, R.id.action_notifications)
            badge = newBadge
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    notifications.state.collect { state ->
                        newBadge.isVisible = state.unread > 0
                        newBadge.number = state.unread.coerceAtMost(99)
                        findViewById<View>(R.id.action_notifications)?.contentDescription =
                            getString(R.string.notifications_title) + if (state.unread > 0) ", " + NotificationLogic.badgeText(state.unread) else ""
                    }
                }
            }
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_notifications -> {
            if (navController.currentDestination?.id != R.id.notificationsFragment) navController.navigate(R.id.notificationsFragment)
            true
        }
        else -> super.onOptionsItemSelected(item)
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

    override fun onSupportNavigateUp(): Boolean =
        navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()

    private companion object {
        const val TAG = "AppActivity"
    }
}
