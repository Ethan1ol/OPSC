package com.example.opsc.auth

import android.util.Log
import androidx.annotation.StringRes
import com.example.opsc.R
import com.example.opsc.profile.LanguageManager
import com.google.firebase.FirebaseNetworkException
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

/** What the user typed on the registration form (already validated by [Validators]). */
data class RegistrationData(
    val email: String,
    val password: String,
    val name: String,
    val surname: String,
    val username: String,
    val birthDate: String,
    val heightCm: Double? = null,
    val weightKg: Double? = null,
    /** ISO 3166 country code such as "ZA". */
    val country: String = ""
)

/**
 * The user profile document stored at `users/{uid}` in Firestore.
 * Field names match firestore.rules. All fields have defaults so Firestore can rebuild it.
 */
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
    @ServerTimestamp val createdAt: Date? = null
)

/** Reasons authentication can fail, each with the string shown to the user. */
enum class AuthError(@StringRes val messageRes: Int) {
    NETWORK(R.string.auth_err_network),
    WRONG_CREDENTIALS(R.string.auth_err_wrong_credentials),
    EMAIL_IN_USE(R.string.auth_err_email_in_use),
    WEAK_PASSWORD(R.string.auth_err_weak_password),
    ACCOUNT_DISABLED(R.string.auth_err_disabled),
    TOO_MANY_REQUESTS(R.string.auth_err_too_many),
    GOOGLE_CANCELLED(R.string.auth_err_google_cancelled),
    GOOGLE_UNAVAILABLE(R.string.auth_err_google_unavailable),
    PROFILE_SAVE_FAILED(R.string.auth_err_profile_save),
    UNKNOWN(R.string.auth_err_unknown)
}

/** The result of a login, registration or SSO attempt. Errors are values, so the UI never crashes. */
sealed interface AuthOutcome {
    /** [needsProfile] is true when the user is signed in but has no profile document yet (first Google sign-in). */
    data class Success(val isNewUser: Boolean, val needsProfile: Boolean = false) : AuthOutcome
    data class Failure(val error: AuthError) : AuthOutcome
}

/**
 * Talks to Firebase Authentication and Firestore for login, registration, Google SSO,
 * password reset, logout and the user profile document.
 * Passwords are never stored by the app: Firebase Auth hashes them (scrypt) on the server.
 */
class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    /** The signed-in user, or null. Firebase restores this automatically after the app restarts. */
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

    /** Emails a password reset link. Succeeds silently for unknown emails so accounts cannot be probed. */
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

    fun logout() {
        Log.i(TAG, "Logging out uid=${auth.currentUser?.uid}")
        auth.signOut()
    }

    /** Reads the profile document. Returns null if it does not exist or the device is offline and has no cache. */
    suspend fun loadProfile(uid: String): UserProfile? = try {
        db.collection(USERS).document(uid).get().await().toObject(UserProfile::class.java)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Could not load profile", e)
        null
    }

    /**
     * Writes the profile in the background. Firestore queues the write if the phone is offline
     * and sends it later, so we do not wait for the server (that would freeze the UI offline).
     */
    // Adapted from: Firebase (2026) Add data to Cloud Firestore.
    // Available at: https://firebase.google.com/docs/firestore/manage-data/add-data
    private fun saveNewProfile(profile: UserProfile) {
        db.collection(USERS).document(profile.uid).set(profile)
            .addOnSuccessListener { Log.i(TAG, "Profile saved for uid=${profile.uid}") }
            .addOnFailureListener { Log.e(TAG, "Profile save failed", it) }
    }

    /**
     * Does the signed-in user have a profile document with a username? [ProfileStatus.UNKNOWN] means
     * we could not find out (offline, timeout); callers then let the user in rather than trap them.
     */
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

    /** A starting point for the "finish your profile" screen, built from the Google account. */
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

    /**
     * Creates the profile document and waits for the server to accept it, so a rejected write
     * (Firestore rules) or a missing connection is reported instead of silently lost.
     */
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

    /** Converts any Firebase exception into one of our friendly [AuthError]s. */
    private fun mapError(e: Exception): AuthError = when (e) {
        is FirebaseNetworkException -> AuthError.NETWORK
        is FirebaseAuthUserCollisionException -> AuthError.EMAIL_IN_USE
        is FirebaseAuthWeakPasswordException -> AuthError.WEAK_PASSWORD
        is FirebaseAuthInvalidUserException ->
            if (e.errorCode == "ERROR_USER_DISABLED") AuthError.ACCOUNT_DISABLED else AuthError.WRONG_CREDENTIALS
        is FirebaseAuthInvalidCredentialsException -> AuthError.WRONG_CREDENTIALS
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
    }
}
