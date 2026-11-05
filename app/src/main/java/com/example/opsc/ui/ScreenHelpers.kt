package com.example.opsc.ui

import android.content.Intent
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.example.opsc.app.AppActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.util.Locale

/**
 * Shows or hides a loading state on a button: while busy the button is disabled, shows
 * [busyLabel], and a thin progress bar appears under it. Prevents double submits.
 */
fun setBusy(
    button: MaterialButton,
    progress: LinearProgressIndicator,
    busy: Boolean,
    @StringRes idleLabel: Int,
    @StringRes busyLabel: Int
) {
    button.isEnabled = !busy
    button.setText(if (busy) busyLabel else idleLabel)
    if (busy) progress.show() else progress.hide()
}

/** Opens the main app (bottom navigation) and closes the login screens. */
fun Fragment.openMainApp() {
    startActivity(Intent(requireContext(), AppActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    })
    requireActivity().finish()
}

/** Every country as (ISO code, display name in the current language), sorted by name. */
fun countryList(locale: Locale = Locale.getDefault()): List<Pair<String, String>> =
    Locale.getISOCountries()
        .map { code -> code to Locale("", code).getDisplayCountry(locale) }
        .filter { it.second.isNotBlank() }
        .sortedBy { it.second }
