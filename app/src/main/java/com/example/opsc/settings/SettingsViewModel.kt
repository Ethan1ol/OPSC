package com.example.opsc.settings

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.opsc.auth.AuthError
import com.example.opsc.auth.AuthOutcome
import com.example.opsc.auth.AuthRepository
import com.example.opsc.auth.UserProfile
import com.example.opsc.profile.ProfileRepository
import com.example.opsc.profile.Units
import com.example.opsc.ui.ConnectivityMonitor
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** What the settings screen is showing while it loads the profile. */
sealed interface SettingsState {
    data object Loading : SettingsState
    data class Ready(val profile: UserProfile) : SettingsState
    data object Failed : SettingsState
}

/** One-off results the settings screens react to. */
sealed interface SettingsEvent {
    /** Settings were saved. [offline] means they are queued until the phone is online again. */
    data class Saved(val offline: Boolean) : SettingsEvent
    data object PasswordChanged : SettingsEvent
    data object AccountDeleted : SettingsEvent
    data class Failed(val error: AuthError) : SettingsEvent
}

/**
 * Loads the user's profile for the settings screens and saves changes to Firestore (queued by
 * Firestore when the phone is offline). Also runs password changes and account deletion.
 * Lives as long as the settings screen so typed values and results survive rotation.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val auth = AuthRepository()
    private val profiles = ProfileRepository()
    private val connectivity = ConnectivityMonitor(application)

    private val _state = MutableStateFlow<SettingsState>(SettingsState.Loading)
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    private val _busy = MutableStateFlow(false)

    /** True while a password change or account deletion is running. */
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events: Flow<SettingsEvent> = _events.receiveAsFlow()

    /** True once the form fields have been filled from the profile (so rotation does not overwrite typing). */
    var fieldsFilled = false

    /** The units currently shown in the height and weight fields. */
    var units: Units = Units.METRIC

    /** The push-notification preference currently shown. */
    var pushEnabled: Boolean = true

    /** The country picked in the form (ISO code). */
    var country: String = ""

    val usesPassword: Boolean get() = auth.usesPasswordSignIn()

    init {
        load()
    }

    fun load() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            _state.value = SettingsState.Failed
            return
        }
        _state.value = SettingsState.Loading
        viewModelScope.launch {
            val profile = profiles.load(uid)
            if (profile == null) {
                _state.value = SettingsState.Failed
            } else {
                if (!fieldsFilled) {
                    units = Units.fromPref(profile.units)
                    pushEnabled = profile.pushEnabled
                    country = profile.country
                }
                _state.value = SettingsState.Ready(profile)
            }
        }
    }

    /** Saves the account fields. Height and weight are passed in centimetres and kilograms. */
    fun saveAccount(username: String, heightCm: Double?, weightKg: Double?, country: String) {
        this.country = country
        save(
            mapOf(
                "username" to username,
                "heightCm" to heightCm,
                "weightKg" to weightKg,
                "country" to country,
                "units" to units.prefValue
            )
        )
        auth.updateDisplayName(username)
    }

    /** Saves one or more preference fields (language, units, push) straight away. */
    fun savePreference(fields: Map<String, Any?>) = save(fields)

    private fun save(fields: Map<String, Any?>) {
        val uid = auth.currentUser?.uid ?: return
        profiles.save(uid, fields)
        _events.trySend(SettingsEvent.Saved(offline = !connectivity.isOnlineNow()))
    }

    fun changePassword(current: String, new: String) = runAccountTask("changePassword") {
        when (val outcome = auth.changePassword(current, new)) {
            is AuthOutcome.Success -> SettingsEvent.PasswordChanged
            is AuthOutcome.Failure -> SettingsEvent.Failed(outcome.error)
        }
    }

    /** Re-checks the password, then deletes the account and its data. */
    fun deleteWithPassword(password: String) = runAccountTask("deleteWithPassword") {
        val check = auth.reauthenticateWithPassword(password)
        if (check is AuthOutcome.Failure) SettingsEvent.Failed(check.error) else delete()
    }

    /** Re-checks the Google account (from a fresh ID token), then deletes the account and its data. */
    fun deleteWithGoogle(idToken: String) = runAccountTask("deleteWithGoogle") {
        val check = auth.reauthenticateWithGoogle(idToken)
        if (check is AuthOutcome.Failure) SettingsEvent.Failed(check.error) else delete()
    }

    private suspend fun delete(): SettingsEvent = when (val outcome = auth.deleteAccount()) {
        is AuthOutcome.Success -> SettingsEvent.AccountDeleted
        is AuthOutcome.Failure -> SettingsEvent.Failed(outcome.error)
    }

    private fun runAccountTask(label: String, block: suspend () -> SettingsEvent) {
        if (_busy.value) return
        _busy.value = true
        Log.d(TAG, "Starting $label")
        viewModelScope.launch {
            val event = block()
            _busy.value = false
            _events.send(event)
        }
    }

    /** Called by the screen when the user can start another account task (for example after cancelling Google). */
    fun reportFailure(error: AuthError) {
        _events.trySend(SettingsEvent.Failed(error))
    }

    private companion object {
        const val TAG = "SettingsViewModel"
    }
}
