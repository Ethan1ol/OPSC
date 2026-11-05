package com.example.opsc.ui

/**
 * The look the user picked: dark (the app default), light, or follow the phone setting.
 * Pure Kotlin so the saved-value logic can be unit tested.
 */
enum class ThemeMode(val prefValue: String) {
    DARK("dark"),
    LIGHT("light"),
    SYSTEM("system");

    companion object {
        /** Dark is the standard look of Battle Fitness. */
        val DEFAULT = DARK

        /** Turns a saved value back into a mode; anything unknown or missing gives [DEFAULT]. */
        fun fromPref(value: String?): ThemeMode = entries.firstOrNull { it.prefValue == value } ?: DEFAULT
    }
}
