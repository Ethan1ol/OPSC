package com.example.opsc.auth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** One-off results the screens react to (open the app, show an error, show the "email sent" message). */
sealed interface AuthEvent {
    data class LoggedIn(val isNewUser: Boolean) : AuthEvent
    data object ResetSent : AuthEvent
    data class Failed(val error: AuthError) : AuthEvent
}

/**
 * Shared by the login, sign-up and forgot-password screens. It runs the Firebase calls in
 * [viewModelScope], so a request is not lost when the phone is rotated, and it remembers
 * the answers from sign-up step 1 while the user fills in step 2.
 */
class AuthViewModel : ViewModel() {
    /** The repository, also used by the Google sign-in helper. */
    val repository = AuthRepository()

    private val _loading = MutableStateFlow(false)

    /** True while a request is running, so screens can show progress and block double taps. */
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _events = Channel<AuthEvent>(Channel.BUFFERED)

    /** Results of requests. Each event is delivered once. */
    val events: Flow<AuthEvent> = _events.receiveAsFlow()

    // Sign-up step 1 answers, kept in memory only (never written to disk).
    var pendingEmail = ""
    var pendingPassword = ""

    /** The country picked on sign-up step 2 (ISO code), kept across rotation. */
    var selectedCountry = DEFAULT_COUNTRY

    /** Set when registration failed because the email is taken, so step 1 can show it inline. */
    var emailAlreadyUsed = false

    /** True once a reset email has been requested, so the forgot-password screen keeps showing the result. */
    var resetSent = false

    fun login(email: String, password: String) = run("login") { repository.login(email, password) }

    fun register(data: RegistrationData) = run("register") { repository.register(data) }

    fun sendPasswordReset(email: String) {
        if (_loading.value) return
        _loading.value = true
        viewModelScope.launch {
            val outcome = repository.sendPasswordReset(email)
            _loading.value = false
            when (outcome) {
                is AuthOutcome.Success -> {
                    resetSent = true
                    _events.send(AuthEvent.ResetSent)
                }
                is AuthOutcome.Failure -> _events.send(AuthEvent.Failed(outcome.error))
            }
        }
    }

    /** Marks a Google sign-in as started (the account picker runs in the screen, not here). */
    fun googleStarted() {
        _loading.value = true
    }

    /** Hands the result of Google sign-in (or its cancellation) back to the screens. */
    fun googleFinished(outcome: AuthOutcome) {
        _loading.value = false
        _events.trySend(outcome.toEvent())
    }

    /** Clears the busy flag, for example if a screen is destroyed while the Google picker is open. */
    fun googleAbandoned() {
        _loading.value = false
    }

    private fun run(label: String, block: suspend () -> AuthOutcome) {
        if (_loading.value) return
        _loading.value = true
        Log.d(TAG, "Starting $label")
        viewModelScope.launch {
            val outcome = block()
            _loading.value = false
            _events.send(outcome.toEvent())
        }
    }

    private fun AuthOutcome.toEvent(): AuthEvent = when (this) {
        is AuthOutcome.Success -> AuthEvent.LoggedIn(isNewUser)
        is AuthOutcome.Failure -> AuthEvent.Failed(error)
    }

    companion object {
        private const val TAG = "AuthViewModel"
        const val DEFAULT_COUNTRY = "ZA"
    }
}
