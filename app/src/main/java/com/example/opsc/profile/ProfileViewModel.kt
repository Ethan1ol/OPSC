package com.example.opsc.profile

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.opsc.auth.UserProfile
import com.example.opsc.clubs.ClubRepository
import com.example.opsc.media.ImageProcessor
import com.example.opsc.media.ImageSpec
import com.example.opsc.media.MediaRepository
import com.example.opsc.workouts.WorkoutRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/** Which picture is being changed. */
enum class MediaTarget(val spec: ImageSpec) { AVATAR(ImageSpec.AVATAR), BANNER(ImageSpec.BANNER) }

/** Everything the profile screens show. */
data class ProfileUiState(
    val loading: Boolean = true,
    val failed: Boolean = false,
    val profile: UserProfile? = null,
    val avatar: File? = null,
    val banner: File? = null,
    val workouts: Int = 0,
    val wins: Int = 0,
    val clubs: Int = 0,
    /** The picture being uploaded now, if any. */
    val uploading: MediaTarget? = null
)

/** One-off results for the profile screens. */
sealed interface ProfileEvent {
    data class UploadFailed(val target: MediaTarget, val reason: UploadFailure) : ProfileEvent
    data class UploadDone(val target: MediaTarget) : ProfileEvent
    data object DetailsSaved : ProfileEvent
    data class Removed(val target: MediaTarget) : ProfileEvent
}

/** Why a picture could not be saved. */
enum class UploadFailure { UNREADABLE, UPLOAD }

/** The user's profile: details, pictures (cached on the phone) and the numbers (workouts, wins, clubs). Also runs picture uploads. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val uid: String? = FirebaseAuth.getInstance().currentUser?.uid
    private val profiles = ProfileRepository()
    private val media = MediaRepository.get(application)
    private val clubRepository = ClubRepository()

    private val profile = MutableStateFlow<UserProfile?>(null)
    private val loadFailed = MutableStateFlow(false)
    private val loading = MutableStateFlow(true)
    private val avatar = MutableStateFlow<File?>(uid?.let { media.cached(MediaRepository.avatarPath(it)) })
    private val banner = MutableStateFlow<File?>(uid?.let { media.cached(MediaRepository.bannerPath(it)) })
    private val uploading = MutableStateFlow<MediaTarget?>(null)

    private val workoutCount: Flow<Int> =
        if (uid == null) flowOf(0) else WorkoutRepository.get(application).observe(uid).map { it.size }
    private val myClubs = if (uid == null) flowOf(emptyList()) else clubRepository.observeMyClubs(uid).catch { emit(emptyList()) }
    private val wins: Flow<Int> = myClubs.mapLatest { clubs -> uid?.let { clubRepository.countWins(it, clubs.map { c -> c.id }) } ?: 0 }

    val state: StateFlow<ProfileUiState> = combine(
        listOf(profile, loadFailed, loading, avatar, banner, uploading, workoutCount, wins, myClubs.map { it.size })
    ) { values ->
        ProfileUiState(
            loading = values[2] as Boolean,
            failed = values[1] as Boolean,
            profile = values[0] as UserProfile?,
            avatar = values[3] as File?,
            banner = values[4] as File?,
            uploading = values[5] as MediaTarget?,
            workouts = values[6] as Int,
            wins = values[7] as Int,
            clubs = values[8] as Int
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())

    private val _events = Channel<ProfileEvent>(Channel.BUFFERED)
    val events: Flow<ProfileEvent> = _events.receiveAsFlow()

    /** Which picture the next image is for. */
    var pendingTarget: MediaTarget = MediaTarget.AVATAR

    /** Where the camera saves a photo. */
    var pendingCameraUri: Uri? = null

    private var lastFailedUpload: Pair<MediaTarget, Uri>? = null

    init {
        load()
    }

    fun load() {
        val owner = uid
        if (owner == null) {
            loading.value = false
            loadFailed.value = true
            return
        }
        loading.value = true
        loadFailed.value = false
        viewModelScope.launch {
            val loaded = profiles.load(owner)
            profile.value = loaded
            loadFailed.value = loaded == null
            loading.value = false
            if (loaded != null) refreshPictures(owner, loaded)
        }
    }

    /** Downloads a picture only if the server has a newer version. */
    private suspend fun refreshPictures(owner: String, loaded: UserProfile) {
        media.ensureLocal(MediaRepository.avatarPath(owner), loaded.avatarVersion)?.let { avatar.value = it }
        media.ensureLocal(MediaRepository.bannerPath(owner), loaded.bannerVersion)?.let { banner.value = it }
    }

    /** Saves name, surname and bio (queued by Firestore when offline). */
    fun saveDetails(name: String, surname: String, bio: String) {
        val owner = uid ?: return
        profiles.save(owner, mapOf("name" to name.trim(), "surname" to surname.trim(), "bio" to bio.trim()))
        profile.value = profile.value?.copy(name = name.trim(), surname = surname.trim(), bio = bio.trim())
        _events.trySend(ProfileEvent.DetailsSaved)
    }

    /** Crops, compresses and uploads a picture. Needs a connection. */
    fun upload(target: MediaTarget, source: Uri) {
        val owner = uid ?: return
        if (uploading.value != null) return
        uploading.value = target
        lastFailedUpload = null
        viewModelScope.launch {
            val bytes = ImageProcessor.process(getApplication(), source, target.spec)
            if (bytes == null) {
                fail(target, source, UploadFailure.UNREADABLE)
                return@launch
            }
            val version = System.currentTimeMillis()
            val path = if (target == MediaTarget.AVATAR) MediaRepository.avatarPath(owner) else MediaRepository.bannerPath(owner)
            val file = media.upload(path, bytes, version)
            if (file == null) {
                fail(target, source, UploadFailure.UPLOAD)
                return@launch
            }
            // Save the new version, so other phones download the picture.
            profiles.save(owner, mapOf(if (target == MediaTarget.AVATAR) "avatarVersion" to version else "bannerVersion" to version))
            profile.value = profile.value?.let { if (target == MediaTarget.AVATAR) it.copy(avatarVersion = version) else it.copy(bannerVersion = version) }
            if (target == MediaTarget.AVATAR) avatar.value = file else banner.value = file
            uploading.value = null
            _events.send(ProfileEvent.UploadDone(target))
        }
    }

    /** Tries the last failed upload again. */
    fun retryUpload() {
        lastFailedUpload?.let { (target, uri) -> upload(target, uri) }
    }

    /** Deletes a picture and goes back to the default. */
    fun remove(target: MediaTarget) {
        val owner = uid ?: return
        if (uploading.value != null) return
        uploading.value = target
        viewModelScope.launch {
            val path = if (target == MediaTarget.AVATAR) MediaRepository.avatarPath(owner) else MediaRepository.bannerPath(owner)
            val ok = media.remove(path)
            if (ok) {
                profiles.save(owner, mapOf(if (target == MediaTarget.AVATAR) "avatarVersion" to 0L else "bannerVersion" to 0L))
                profile.value = profile.value?.let { if (target == MediaTarget.AVATAR) it.copy(avatarVersion = 0) else it.copy(bannerVersion = 0) }
                if (target == MediaTarget.AVATAR) avatar.value = null else banner.value = null
                _events.send(ProfileEvent.Removed(target))
            } else {
                _events.send(ProfileEvent.UploadFailed(target, UploadFailure.UPLOAD))
            }
            uploading.value = null
        }
    }

    private suspend fun fail(target: MediaTarget, source: Uri, reason: UploadFailure) {
        Log.w(TAG, "Upload of $target failed: $reason")
        lastFailedUpload = target to source
        uploading.value = null
        _events.send(ProfileEvent.UploadFailed(target, reason))
    }

    private companion object {
        const val TAG = "ProfileViewModel"
    }
}
