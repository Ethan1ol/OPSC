package com.example.opsc.profile

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.opsc.auth.UserProfile
import com.example.opsc.media.MediaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

/** What another member's profile screen shows. */
data class MemberProfileUiState(
    val loading: Boolean = true,
    val profile: UserProfile? = null,
    val avatar: File? = null,
    val banner: File? = null
)

/**
 * Loads another member's profile (from the `uid` navigation argument): the text first, then their
 * pictures, which are cached on the phone so they open instantly next time and work offline.
 */
class MemberProfileViewModel(application: Application, savedState: SavedStateHandle) : AndroidViewModel(application) {
    private val uid: String? = savedState["uid"]
    private val profiles = ProfileRepository()
    private val media = MediaRepository.get(application)

    private val _state = MutableStateFlow(
        MemberProfileUiState(
            avatar = uid?.let { media.cached(MediaRepository.avatarPath(it)) },
            banner = uid?.let { media.cached(MediaRepository.bannerPath(it)) }
        )
    )
    val state: StateFlow<MemberProfileUiState> = _state

    init {
        load()
    }

    /** Loads (or reloads) the profile and its pictures. */
    fun load() {
        val owner = uid
        if (owner == null) {
            _state.value = _state.value.copy(loading = false, profile = null)
            return
        }
        _state.value = _state.value.copy(loading = true)
        viewModelScope.launch {
            val loaded = profiles.load(owner)
            Log.d(TAG, "Loaded member profile: found=${loaded != null}")
            _state.value = _state.value.copy(loading = false, profile = loaded)
            if (loaded != null) {
                media.ensureLocal(MediaRepository.avatarPath(owner), loaded.avatarVersion)?.let { _state.value = _state.value.copy(avatar = it) }
                media.ensureLocal(MediaRepository.bannerPath(owner), loaded.bannerVersion)?.let { _state.value = _state.value.copy(banner = it) }
            }
        }
    }

    private companion object {
        const val TAG = "MemberProfileViewModel"
    }
}
