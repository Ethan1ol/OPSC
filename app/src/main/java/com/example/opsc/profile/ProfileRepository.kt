package com.example.opsc.profile

import android.util.Log
import com.example.opsc.auth.UserProfile
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/** Reads and updates the user's profile at users/{uid}. */
class ProfileRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    /** Loads the profile, or null if missing or offline with no cache. */
    suspend fun load(uid: String): UserProfile? = try {
        db.collection(USERS).document(uid).get().await().toObject(UserProfile::class.java)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Could not load profile", e)
        null
    }

    /** Merges the fields into the profile. Firestore queues the write when offline, so this returns at once. */
    fun save(uid: String, fields: Map<String, Any?>) {
        Log.d(TAG, "Saving profile fields ${fields.keys}")
        db.collection(USERS).document(uid).set(fields, SetOptions.merge())
            .addOnSuccessListener { Log.i(TAG, "Profile saved to server") }
            .addOnFailureListener { Log.e(TAG, "Profile save failed", it) }
    }

    private companion object {
        const val TAG = "ProfileRepository"
        const val USERS = "users"
    }
}
