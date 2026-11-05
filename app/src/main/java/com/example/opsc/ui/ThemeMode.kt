package com.example.opsc.ui

/** The look the user picked: dark (default), light, or follow the phone. Plain Kotlin so it can be unit tested. */
enum class ThemeMode(val prefValue: String) {
    DARK("dark"),
    LIGHT("light"),
    SYSTEM("system");

    companion object {
        /** Dark is the standard look. */
        val DEFAULT = DARK

        /** Turns a saved value into a mode. Unknown or missing gives the default. */
        fun fromPref(value: String?): ThemeMode = entries.firstOrNull { it.prefValue == value } ?: DEFAULT
    }
}
