package com.example.opsc.auth

import android.util.Log
import androidx.annotation.StringRes
import com.example.opsc.R
import com.example.opsc.profile.LanguageManager
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ServerTimestamp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Date

/** What the user typed on the sign-up form. */
data class RegistrationData(
    val email: String,
    val password: String,
    val name: String,
    val surname: String,
    val username: String,
    val birthDate: String,
    val heightCm: Double? = null,
    val weightKg: Double? = null,
    /** Country code, for example ZA. */
    val country: String = ""
)

/** The user's profile document at users/{uid}. */
data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val username: String = "",
    val name: String = "",
    val surname: String = "",
    val birthDate: String = "",
    val photoUrl: String = "",
    val language: String = "en",
    val country: String = "",
    val pushEnabled: Boolean = true,
    val heightCm: Double? = null,
    val weightKg: Double? = null,
    /** metric or imperial (always stored in cm and kg). */
    val units: String = "metric",
    /** Workouts per month the user aims for. */
    val monthlyGoal: Int = 12,
    /** Short about-me text. */
    val bio: String = "",
    /** Changes when the picture changes, so other phones download it again. */
    val avatarVersion: Long = 0,
    val bannerVersion: Long = 0,
    /** This phone's push token. */
    val fcmToken: String = "",
    val fcmUpdatedAt: Long = 0,
    @ServerTimestamp val createdAt: Date? = null
)

/** Reasons sign-in can fail, with the message to show. */
enum class AuthError(@StringRes val messageRes: Int) {
    NETWORK(R.string.auth_err_network),
    WRONG_CREDENTIALS(R.string.auth_err_wrong_credentials),
    EMAIL_IN_USE(R.string.auth_err_email_in_use),
    WEAK_PASSWORD(R.string.auth_err_weak_password),
    ACCOUNT_DISABLED(R.string.auth_err_disabled),
    TOO_MANY_REQUESTS(R.string.auth_err_too_many),
    GOOGLE_CANCELLED(R.string.auth_err_google_cancelled),
    GOOGLE_UNAVAILABLE(R.string.auth_err_google_unavailable),
    GOOGLE_WRONG_ACCOUNT(R.string.auth_err_google_wrong_account),
    PROFILE_SAVE_FAILED(R.string.auth_err_profile_save),
    UNKNOWN(R.string.auth_err_unknown)
}

/** Result of a login, sign-up or Google attempt. */
sealed interface AuthOutcome {
    /** True if the user must still create a profile. */
    data class Success(val isNewUser: Boolean, val needsProfile: Boolean = false) : AuthOutcome
    data class Failure(val error: AuthError) : AuthOutcome
}

/** Talks to Firebase Auth and Firestore. Passwords are never stored by the app. */
class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    /** The signed-in user, or null. */
    val currentUser: FirebaseUser? get() = auth.currentUser

    // Adapted from: Firebase (2026) Get Started with Firebase Authentication on Android (password-based accounts).
    // Available at: https://firebase.google.com/docs/auth/android/password-auth
    suspend fun register(data: RegistrationData): AuthOutcome = try {
        Log.d(TAG, "Registering ${data.email}")
        val result = auth.createUserWithEmailAndPassword(data.email.trim(), data.password).await()
        val user = result.user ?: return AuthOutcome.Failure(AuthError.UNKNOWN)
        runCatching {
            user.updateProfile(
                UserProfileChangeRequest.Builder().setDisplayName(data.username.trim()).build()
            ).await()
        }.onFailure { Log.w(TAG, "Could not set display name", it) }
        saveNewProfile(
            UserProfile(
                uid = user.uid,
                email = data.email.trim(),
                username = data.username.trim(),
                name = data.name.trim(),
                surname = data.surname.trim(),
                birthDate = data.birthDate,
                heightCm = data.heightCm,
                weightKg = data.weightKg,
                country = data.country,
                language = LanguageManager.current()
            )
        )
        Log.i(TAG, "Registration succeeded for uid=${user.uid}")
        AuthOutcome.Success(isNewUser = true)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "Registration failed", e)
        AuthOutcome.Failure(mapError(e))
    }

    suspend fun login(email: String, password: String): AuthOutcome = try {
        Log.d(TAG, "Logging in $email")
        val result = auth.signInWithEmailAndPassword(email.trim(), password).await()
        val needsProfile = result.user?.let { profileStatus(it.uid) == ProfileStatus.MISSING } ?: false
        Log.i(TAG, "Login succeeded for uid=${result.user?.uid}, needsProfile=$needsProfile")
        AuthOutcome.Success(isNewUser = false, needsProfile = needsProfile)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "Login failed", e)
        AuthOutcome.Failure(mapError(e))
    }

    // Adapted from: Firebase (2026) Authenticate with Google on Android.
    // Available at: https://firebase.google.com/docs/auth/android/google-signin
    suspend fun signInWithGoogleIdToken(idToken: String): AuthOutcome = try {
        Log.d(TAG, "Exchanging Google ID token with Firebase")
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = auth.signInWithCredential(credential).await()
        val needsProfile = result.user?.let { profileStatus(it.uid) == ProfileStatus.MISSING } ?: false
        Log.i(TAG, "Google sign-in succeeded for uid=${result.user?.uid}, needsProfile=$needsProfile")
        AuthOutcome.Success(isNewUser = result.additionalUserInfo?.isNewUser == true, needsProfile = needsProfile)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "Google sign-in failed", e)
        AuthOutcome.Failure(mapError(e))
    }

    /** Sends a password reset email. It always looks successful, so accounts cannot be guessed. */
    suspend fun sendPasswordReset(email: String): AuthOutcome = try {
        auth.sendPasswordResetEmail(email.trim()).await()
        Log.i(TAG, "Password reset email requested")
        AuthOutcome.Success(isNewUser = false)
    } catch (e: CancellationException) {
        throw e
    } catch (e: FirebaseAuthInvalidUserException) {
        Log.i(TAG, "Password reset requested for unknown account (hidden from user)")
        AuthOutcome.Success(isNewUser = false)
    } catch (e: Exception) {
        Log.e(TAG, "Password reset failed", e)
        AuthOutcome.Failure(mapError(e))
    }

    /** True if the account uses an email and password. */
    fun usesPasswordSignIn(): Boolean =
        auth.currentUser?.providerData?.any { it.providerId == EmailAuthProvider.PROVIDER_ID } == true

    /** Keeps the display name the same as the username. */
    fun updateDisplayName(name: String) {
        auth.currentUser?.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name).build())
            ?.addOnFailureListener { Log.w(TAG, "Could not update display name", it) }
    }

    /** Checks the password again before a sensitive change. */
    suspend fun reauthenticateWithPassword(password: String): AuthOutcome = try {
        val user = auth.currentUser
        val email = user?.email
        if (user == null || email == null) AuthOutcome.Failure(AuthError.UNKNOWN)
        else {
            user.reauthenticate(EmailAuthProvider.getCredential(email, password)).await()
            AuthOutcome.Success(isNewUser = false)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Re-authentication failed", e)
        AuthOutcome.Failure(mapError(e))
    }

    /** Checks a Google user again. Fails if a different account was chosen. */
    suspend fun reauthenticateWithGoogle(idToken: String): AuthOutcome = try {
        val user = auth.currentUser
        if (user == null) AuthOutcome.Failure(AuthError.UNKNOWN)
        else {
            user.reauthenticate(GoogleAuthProvider.getCredential(idToken, null)).await()
            AuthOutcome.Success(isNewUser = false)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Google re-authentication failed", e)
        AuthOutcome.Failure(mapError(e))
    }

    /** Changes the password after checking the old one. */
    suspend fun changePassword(currentPassword: String, newPassword: String): AuthOutcome {
        val check = reauthenticateWithPassword(currentPassword)
        if (check is AuthOutcome.Failure) return check
        return try {
            auth.currentUser?.updatePassword(newPassword)?.await()
            Log.i(TAG, "Password changed")
            AuthOutcome.Success(isNewUser = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Password change failed", e)
            AuthOutcome.Failure(mapError(e))
        }
    }

    /** Deletes the user's data, then the account. Call right after re-checking the password. If the data cannot be deleted (for example offline), the account stays. */
    suspend fun deleteAccount(): AuthOutcome = try {
        val user = auth.currentUser ?: return AuthOutcome.Failure(AuthError.UNKNOWN)
        val uid = user.uid
        val deleted = withTimeoutOrNull(DELETE_TIMEOUT_MS) {
            for (sub in USER_SUBCOLLECTIONS) deleteCollection("$USERS/$uid/$sub")
            db.collection(USERS).document(uid).delete().await()
            true
        }
        if (deleted != true) {
            Log.w(TAG, "Deleting account data timed out")
            AuthOutcome.Failure(AuthError.NETWORK)
        } else {
            user.delete().await()
            Log.i(TAG, "Account deleted uid=$uid")
            AuthOutcome.Success(isNewUser = false)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "Account deletion failed", e)
        AuthOutcome.Failure(if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.UNAVAILABLE) AuthError.NETWORK else mapError(e))
    }

    /** Deletes every document in a collection, 400 at a time. */
    private suspend fun deleteCollection(path: String) {
        while (true) {
            val snapshot = db.collection(path).limit(400).get().await()
            if (snapshot.isEmpty) return
            val batch = db.batch()
            snapshot.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
    }

    fun logout() {
        Log.i(TAG, "Logging out uid=${auth.currentUser?.uid}")
        auth.signOut()
    }

    /** Reads the profile, or null if it is missing or the phone is offline with no cache. */
    suspend fun loadProfile(uid: String): UserProfile? = try {
        db.collection(USERS).document(uid).get().await().toObject(UserProfile::class.java)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Could not load profile", e)
        null
    }

    /** Saves the profile in the background. Firestore queues the write when offline. */
    // Adapted from: Firebase (2026) Add data to Cloud Firestore.
    // Available at: https://firebase.google.com/docs/firestore/manage-data/add-data
    private fun saveNewProfile(profile: UserProfile) {
        db.collection(USERS).document(profile.uid).set(profile)
            .addOnSuccessListener { Log.i(TAG, "Profile saved for uid=${profile.uid}") }
            .addOnFailureListener { Log.e(TAG, "Profile save failed", it) }
    }

    /** Checks if the user has a profile. UNKNOWN (offline) lets the user in. */
    suspend fun profileStatus(uid: String): ProfileStatus = try {
        val snapshot = withTimeoutOrNull(PROFILE_CHECK_TIMEOUT_MS) { db.collection(USERS).document(uid).get().await() }
        when {
            snapshot == null -> ProfileStatus.UNKNOWN
            snapshot.exists() && !snapshot.getString("username").isNullOrBlank() -> ProfileStatus.EXISTS
            else -> ProfileStatus.MISSING
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Could not check profile", e)
        ProfileStatus.UNKNOWN
    }

    /** Builds a starting profile from the Google account. */
    fun suggestedProfile(): UserProfile? {
        val user = auth.currentUser ?: return null
        val email = user.email.orEmpty()
        val displayName = user.displayName.orEmpty()
        return UserProfile(
            uid = user.uid,
            email = email,
            username = Validators.usernameFromEmail(email.ifEmpty { displayName }),
            name = displayName.substringBefore(' '),
            surname = displayName.substringAfter(' ', ""),
            photoUrl = user.photoUrl?.toString().orEmpty(),
            language = LanguageManager.current()
        )
    }

    /** Saves the profile and waits for the server, so a failure is shown. */
    suspend fun saveProfile(profile: UserProfile): AuthOutcome = try {
        val done = withTimeoutOrNull(PROFILE_SAVE_TIMEOUT_MS) {
            db.collection(USERS).document(profile.uid).set(profile).await()
            true
        }
        if (done == true) {
            Log.i(TAG, "Profile created for uid=${profile.uid}")
            AuthOutcome.Success(isNewUser = true)
        } else {
            Log.w(TAG, "Profile save timed out (offline?)")
            AuthOutcome.Failure(AuthError.NETWORK)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: FirebaseFirestoreException) {
        Log.e(TAG, "Profile save failed: ${e.code}", e)
        AuthOutcome.Failure(if (e.code == FirebaseFirestoreException.Code.UNAVAILABLE) AuthError.NETWORK else AuthError.PROFILE_SAVE_FAILED)
    } catch (e: Exception) {
        Log.e(TAG, "Profile save failed", e)
        AuthOutcome.Failure(AuthError.PROFILE_SAVE_FAILED)
    }

    /** Turns a Firebase error into one of our errors. */
    private fun mapError(e: Exception): AuthError = when (e) {
        is FirebaseNetworkException -> AuthError.NETWORK
        is FirebaseAuthUserCollisionException -> AuthError.EMAIL_IN_USE
        is FirebaseAuthWeakPasswordException -> AuthError.WEAK_PASSWORD
        is FirebaseAuthInvalidUserException ->
            if (e.errorCode == "ERROR_USER_DISABLED") AuthError.ACCOUNT_DISABLED else AuthError.WRONG_CREDENTIALS
        is FirebaseAuthInvalidCredentialsException ->
            if (e.errorCode == "ERROR_USER_MISMATCH") AuthError.GOOGLE_WRONG_ACCOUNT else AuthError.WRONG_CREDENTIALS
        else ->
            if (e.message?.contains("TOO_MANY", ignoreCase = true) == true ||
                e.message?.contains("blocked all requests", ignoreCase = true) == true
            ) AuthError.TOO_MANY_REQUESTS else AuthError.UNKNOWN
    }

    private companion object {
        const val TAG = "AuthRepository"
        const val USERS = "users"
        const val PROFILE_CHECK_TIMEOUT_MS = 5000L
        const val PROFILE_SAVE_TIMEOUT_MS = 10000L
        const val DELETE_TIMEOUT_MS = 20000L
        val USER_SUBCOLLECTIONS = listOf("workouts", "notifications")
    }
}
