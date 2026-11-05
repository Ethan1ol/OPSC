package com.example.opsc.auth

import android.content.Context

/** Whether the signed-in user has a profile in Firestore. */
enum class ProfileStatus { EXISTS, MISSING, UNKNOWN }

/** Where to send a signed-in user. */
enum class ProfileRoute { OPEN_APP, FINISH_PROFILE }

/** Decides where a signed-in user goes. Only a confirmed missing profile goes to "finish profile"; if we cannot check (offline), they enter the app. */
fun routeFor(status: ProfileStatus): ProfileRoute =
    if (status == ProfileStatus.MISSING) ProfileRoute.FINISH_PROFILE else ProfileRoute.OPEN_APP

/** Remembers on this phone that the profile exists, so the app can open straight to Home. */
object ProfileGate {
    private const val PREFS = "battle_fitness_profile_gate"

    fun isKnownComplete(context: Context, uid: String): Boolean =
        prefs(context).getBoolean(uid, false)

    fun markComplete(context: Context, uid: String) {
        prefs(context).edit().putBoolean(uid, true).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
