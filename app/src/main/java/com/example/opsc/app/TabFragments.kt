package com.example.opsc.app

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.opsc.auth.logoutToLogin
import androidx.lifecycle.lifecycleScope
import com.example.opsc.BuildConfig
import com.example.opsc.R
import com.example.opsc.auth.AuthRepository
import com.example.opsc.profile.LanguageManager
import com.example.opsc.ui.ConfirmDialog
import com.example.opsc.ui.StateLayout
import kotlinx.coroutines.launch

/** Profile tab: account summary, settings, design gallery (debug builds only) and log out. */
class ProfileFragment : Fragment(R.layout.fragment_profile) {
    private val auth = AuthRepository()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val user = auth.currentUser ?: return
        view.findViewById<TextView>(R.id.textProfileName).text = user.displayName ?: user.email.orEmpty()
        view.findViewById<TextView>(R.id.textProfileEmail).text = user.email.orEmpty()

        view.findViewById<View>(R.id.buttonSettings).setOnClickListener {
            findNavController().navigate(R.id.settingsFragment)
        }

        // The design gallery only exists in debug builds, so it is opened by class name.
        if (BuildConfig.DEBUG) {
            view.findViewById<View>(R.id.buttonGallery).apply {
                visibility = View.VISIBLE
                setOnClickListener {
                    val intent = Intent().setComponent(
                        ComponentName(requireContext().packageName, "com.example.opsc.DesignGalleryActivity")
                    )
                    startActivity(intent)
                }
            }
        }

        view.findViewById<View>(R.id.buttonLogout).setOnClickListener {
            ConfirmDialog.show(requireContext(), R.string.logout_title, R.string.logout_message, R.string.btn_logout) {
                logoutToLogin()
            }
        }
    }

    private companion object {
        const val TAG = "ProfileFragment"
    }
}
