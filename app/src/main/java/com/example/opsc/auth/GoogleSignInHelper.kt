package com.example.opsc.auth

import android.app.Activity
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.opsc.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.CancellationException

/**
 * Google single sign-on using Android Credential Manager. It shows the Google account picker,
 * gets a Google ID token, and hands it to [AuthRepository] to sign in to Firebase.
 */
object GoogleSignInHelper {
    private const val TAG = "GoogleSignIn"

    // Adapted from: Android Developers (2026) Authenticate users with Sign in with Google.
    // Available at: https://developer.android.com/identity/sign-in/credential-manager-siwg
    suspend fun signIn(activity: Activity, repository: AuthRepository): AuthOutcome {
        return try {
            val option = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                // Generated at build time from google-services.json (the web OAuth client).
                .setServerClientId(activity.getString(R.string.default_web_client_id))
                .build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val response = CredentialManager.create(activity).getCredential(activity, request)
            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
                repository.signInWithGoogleIdToken(token)
            } else {
                Log.e(TAG, "Unexpected credential type: ${credential.type}")
                AuthOutcome.Failure(AuthError.UNKNOWN)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialCancellationException) {
            Log.i(TAG, "User cancelled Google sign-in")
            AuthOutcome.Failure(AuthError.GOOGLE_CANCELLED)
        } catch (e: NoCredentialException) {
            Log.w(TAG, "No Google account available", e)
            AuthOutcome.Failure(AuthError.GOOGLE_UNAVAILABLE)
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Credential Manager error: ${e.type}", e)
            AuthOutcome.Failure(AuthError.UNKNOWN)
        } catch (e: GoogleIdTokenParsingException) {
            Log.e(TAG, "Could not parse Google ID token", e)
            AuthOutcome.Failure(AuthError.UNKNOWN)
        } catch (e: Exception) {
            Log.e(TAG, "Google sign-in failed", e)
            AuthOutcome.Failure(AuthError.UNKNOWN)
        }
    }

    /** Forgets the chosen Google account so the picker appears again after logout. */
    suspend fun clearState(activity: Activity) {
        try {
            CredentialManager.create(activity).clearCredentialState(ClearCredentialStateRequest())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not clear credential state", e)
        }
    }
}
