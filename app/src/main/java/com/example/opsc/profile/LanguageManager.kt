package com.example.opsc.profile

import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * Switches the app language between English, Afrikaans and isiZulu using per-app locales.
 * AppCompat saves the choice and recreates open screens, so no manual restart is needed.
 */
object LanguageManager {
    private const val TAG = "LanguageManager"

    /** Language codes the app is translated into. English is the default. */
    val SUPPORTED_CODES = listOf("en", "af", "zu")

    /** Returns [tag] if the app supports it, otherwise "en". Pure function, unit tested. */
    fun normalise(tag: String?): String {
        val language = tag?.substringBefore('-')?.substringBefore('_')?.lowercase(Locale.ROOT)
        return if (language != null && language in SUPPORTED_CODES) language else "en"
    }

    /** The language the app is currently showing. */
    fun current(): String {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        val tag = if (!appLocales.isEmpty) appLocales[0]?.language else Locale.getDefault().language
        return normalise(tag)
    }

    // Adapted from: Android Developers (2026) Per-app language preferences.
    // Available at: https://developer.android.com/guide/topics/resources/app-languages
    /** Applies [code]. Does nothing if it is already the current language. */
    fun apply(code: String) {
        val target = normalise(code)
        if (target == current()) return
        Log.i(TAG, "Switching app language to $target")
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(target))
    }
}
