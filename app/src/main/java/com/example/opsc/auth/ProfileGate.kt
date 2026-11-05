package com.example.opsc.auth

import android.content.Context

/** Whether the signed-in user has a profile document in Firestore. */
enum class ProfileStatus { EXISTS, MISSING, UNKNOWN }

/** Where to send a signed-in user. */
enum class ProfileRoute { OPEN_APP, FINISH_PROFILE }

/**
 * Decides where a signed-in user goes. Only a confirmed missing profile sends them to the
 * "finish your profile" screen; if we could not check (offline), they are let into the app so a
 * bad connection never traps them. Pure Kotlin, unit tested.
 */
fun routeFor(status: ProfileStatus): ProfileRoute =
    if (status == ProfileStatus.MISSING) ProfileRoute.FINISH_PROFILE else ProfileRoute.OPEN_APP

/**
 * Remembers on this phone that a user's profile is known to exist, so the app can open straight
 * into Home at launch without waiting for a network check every time.
 */
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
