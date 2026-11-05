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

/** Google sign-in using Credential Manager. Gets a Google token and signs in to Firebase. */
object GoogleSignInHelper {
    private const val TAG = "GoogleSignIn"

    /** Result of the account picker. */
    sealed interface TokenResult {
        data class Token(val idToken: String) : TokenResult
        data class Error(val error: AuthError) : TokenResult
    }

    // Adapted from: Android Developers (2026) Authenticate users with Sign in with Google.
    // Available at: https://developer.android.com/identity/sign-in/credential-manager-siwg
    /** Shows the Google account picker and returns a Google ID token, or why it could not. */
    suspend fun fetchIdToken(activity: Activity): TokenResult {
        return try {
            val option = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                // Generated from google-services.json.
                .setServerClientId(activity.getString(R.string.default_web_client_id))
                .build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val response = CredentialManager.create(activity).getCredential(activity, request)
            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                TokenResult.Token(GoogleIdTokenCredential.createFrom(credential.data).idToken)
            } else {
                Log.e(TAG, "Unexpected credential type: ${credential.type}")
                TokenResult.Error(AuthError.UNKNOWN)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialCancellationException) {
            Log.i(TAG, "User cancelled Google sign-in")
            TokenResult.Error(AuthError.GOOGLE_CANCELLED)
        } catch (e: NoCredentialException) {
            Log.w(TAG, "No Google account available", e)
            TokenResult.Error(AuthError.GOOGLE_UNAVAILABLE)
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Credential Manager error: ${e.type}", e)
            TokenResult.Error(AuthError.UNKNOWN)
        } catch (e: GoogleIdTokenParsingException) {
            Log.e(TAG, "Could not parse Google ID token", e)
            TokenResult.Error(AuthError.UNKNOWN)
        } catch (e: Exception) {
            Log.e(TAG, "Google sign-in failed", e)
            TokenResult.Error(AuthError.UNKNOWN)
        }
    }

    /** Shows the picker, then signs in to Firebase. */
    suspend fun signIn(activity: Activity, repository: AuthRepository): AuthOutcome =
        when (val result = fetchIdToken(activity)) {
            is TokenResult.Token -> repository.signInWithGoogleIdToken(result.idToken)
            is TokenResult.Error -> AuthOutcome.Failure(result.error)
        }

    /** Forgets the Google account so the picker shows again. */
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
