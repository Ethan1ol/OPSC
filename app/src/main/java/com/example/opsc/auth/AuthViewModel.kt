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

/** One-off results the screens react to. */
sealed interface AuthEvent {
    /** True if the user must finish their profile first. */
    data class LoggedIn(val isNewUser: Boolean, val needsProfile: Boolean = false) : AuthEvent
    data object ResetSent : AuthEvent
    data class Failed(val error: AuthError) : AuthEvent
}

/** Shared by the login, sign-up, finish-profile and forgot-password screens. Keeps requests alive when the phone rotates and remembers the step 1 answers. */
class AuthViewModel : ViewModel() {
    /** Also used by the Google helper. */
    val repository = AuthRepository()

    private val _loading = MutableStateFlow(false)

    /** True while a request is running. */
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _events = Channel<AuthEvent>(Channel.BUFFERED)

    /** Results, each delivered once. */
    val events: Flow<AuthEvent> = _events.receiveAsFlow()

    // Sign-up step 1 answers, kept in memory only.
    var pendingEmail = ""
    var pendingPassword = ""

    /** The chosen country, kept when the phone rotates. */
    var selectedCountry = DEFAULT_COUNTRY

    /** True if the email is already used. */
    var emailAlreadyUsed = false

    /** True once a reset email was requested. */
    var resetSent = false

    /** True while the Google picker is open. */
    var googleInFlight = false
        private set

    fun login(email: String, password: String) = run("login") { repository.login(email, password) }

    fun register(data: RegistrationData) = run("register") { repository.register(data) }

    /** Creates a profile for a Google user who has none. */
    fun finishProfile(username: String, heightCm: Double?, weightKg: Double?, country: String) {
        val base = repository.suggestedProfile()
        if (base == null) {
            _events.trySend(AuthEvent.Failed(AuthError.UNKNOWN))
            return
        }
        val profile = base.copy(username = username, heightCm = heightCm, weightKg = weightKg, country = country)
        run("finishProfile") { repository.saveProfile(profile) }
    }

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

    /** Google sign-in started. */
    fun googleStarted() {
        googleInFlight = true
        _loading.value = true
    }

    /** Passes the Google result to the screens. */
    fun googleFinished(outcome: AuthOutcome) {
        googleInFlight = false
        _loading.value = false
        _events.trySend(outcome.toEvent())
    }

    /** Clears the busy flag if the screen closed. */
    fun googleAbandoned() {
        googleInFlight = false
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
        is AuthOutcome.Success -> AuthEvent.LoggedIn(isNewUser, needsProfile)
        is AuthOutcome.Failure -> AuthEvent.Failed(error)
    }

    companion object {
        private const val TAG = "AuthViewModel"
        const val DEFAULT_COUNTRY = "ZA"
    }
}
