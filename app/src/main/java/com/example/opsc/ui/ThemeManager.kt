package com.example.opsc.ui

import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate

/** Saves the chosen [ThemeMode] and applies it to the whole app. Dark is the default. */
object ThemeManager {
    private const val TAG = "ThemeManager"
    private const val PREFS = "battle_fitness_settings"
    private const val KEY_THEME = "theme_mode"

    /** The saved mode, or dark if the user has never chosen one. */
    fun current(context: Context): ThemeMode =
        ThemeMode.fromPref(prefs(context).getString(KEY_THEME, null))

    /** Applies the saved mode. Called once when the app starts. */
    fun apply(context: Context) = AppCompatDelegate.setDefaultNightMode(nightModeFor(current(context)))

    /** Saves and applies a new mode; open screens recreate themselves in the new look. */
    fun set(context: Context, mode: ThemeMode) {
        Log.i(TAG, "Theme mode set to ${mode.prefValue}")
        prefs(context).edit().putString(KEY_THEME, mode.prefValue).apply()
        AppCompatDelegate.setDefaultNightMode(nightModeFor(mode))
    }

    private fun nightModeFor(mode: ThemeMode) = when (mode) {
        ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
        ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
        ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
