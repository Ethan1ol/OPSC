package com.example.opsc.profile

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.opsc.BuildConfig
import com.example.opsc.R
import com.example.opsc.auth.Validators
import com.example.opsc.auth.logoutToLogin
import com.example.opsc.auth.messageRes
import com.example.opsc.notifications.PushTokenManager
import com.example.opsc.ui.ConfirmDialog
import com.example.opsc.ui.ConnectivityMonitor
import com.example.opsc.ui.StateLayout
import com.example.opsc.ui.UiMessages
import com.example.opsc.ui.showPicture
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import java.io.File

/**
 * The Profile tab: banner, avatar, name, bio and three numbers (workouts, wins, clubs), with
 * buttons for Edit profile, Settings and Log out. Pictures come from the phone's cache, so they
 * show straight away and work offline.
 */
class ProfileFragment : Fragment(R.layout.fragment_profile) {
    private val viewModel: ProfileViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val stateLayout = view.findViewById<StateLayout>(R.id.profileState)

        view.findViewById<View>(R.id.buttonEditProfile).setOnClickListener { findNavController().navigate(R.id.editProfileFragment) }
        view.findViewById<View>(R.id.buttonSettings).setOnClickListener { findNavController().navigate(R.id.settingsFragment) }
        view.findViewById<View>(R.id.buttonLogout).setOnClickListener {
            ConfirmDialog.show(requireContext(), R.string.logout_title, R.string.logout_message, R.string.btn_logout) { logoutToLogin() }
        }

        // Debug-only helpers (not in release builds).
        if (BuildConfig.DEBUG) {
            view.findViewById<View>(R.id.buttonGallery).apply {
                visibility = View.VISIBLE
                setOnClickListener {
                    startActivity(Intent().setComponent(ComponentName(requireContext().packageName, "com.example.opsc.DesignGalleryActivity")))
                }
            }
            view.findViewById<View>(R.id.buttonCopyToken).apply {
                visibility = View.VISIBLE
                setOnClickListener { PushTokenManager.copyTokenToClipboard(requireContext(), view) }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    val profile = state.profile
                    when {
                        state.loading && profile == null -> { stateLayout.showLoading(); return@collect }
                        profile == null -> { stateLayout.showError(R.string.profile_load_error) { viewModel.load() }; return@collect }
                    }
                    profile!!
                    stateLayout.showContent()
                    val fullName = "${profile.name} ${profile.surname}".trim()
                    view.findViewById<TextView>(R.id.profileDisplayName).text = fullName.ifEmpty { profile.username }
                    view.findViewById<TextView>(R.id.profileUsername).text = "@${profile.username}"
                    view.findViewById<TextView>(R.id.profileBio).apply {
                        text = profile.bio
                        visibility = if (profile.bio.isBlank()) View.GONE else View.VISIBLE
                    }
                    view.findViewById<TextView>(R.id.profileStatWorkouts).text = state.workouts.toString()
                    view.findViewById<TextView>(R.id.profileStatWins).text = state.wins.toString()
                    view.findViewById<TextView>(R.id.profileStatClubs).text = state.clubs.toString()
                    view.findViewById<ShapeableImageView>(R.id.profileAvatar).showPicture(state.avatar, avatar = true)
                    view.findViewById<ShapeableImageView>(R.id.profileBanner).showPicture(state.banner, avatar = false)
                }
            }
        }
    }
}

/**
 * Edit profile: change the banner and profile picture (from the gallery or the camera; they are
 * centre-cropped and compressed before saving), and the name and bio.
 */
class EditProfileFragment : Fragment(R.layout.fragment_edit_profile) {
    private val viewModel: ProfileViewModel by activityViewModels()

    // The photo picker needs no permission, and neither does taking a photo into our own file.
    private val pickImage = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.upload(viewModel.pendingTarget, uri)
    }
    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) viewModel.pendingCameraUri?.let { viewModel.upload(viewModel.pendingTarget, it) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val progress = view.findViewById<LinearProgressIndicator>(R.id.editProgress)
        val progressText = view.findViewById<TextView>(R.id.editProgressText)
        val avatar = view.findViewById<ShapeableImageView>(R.id.editAvatar)
        val banner = view.findViewById<ShapeableImageView>(R.id.editBanner)
        val changeAvatar = view.findViewById<MaterialButton>(R.id.editAvatarChange)
        val changeBanner = view.findViewById<MaterialButton>(R.id.editBannerChange)
        val nameLayout = view.findViewById<TextInputLayout>(R.id.editNameLayout)
        val surnameLayout = view.findViewById<TextInputLayout>(R.id.editSurnameLayout)
        val bioLayout = view.findViewById<TextInputLayout>(R.id.editBioLayout)
        val name = view.findViewById<TextInputEditText>(R.id.editName)
        val surname = view.findViewById<TextInputEditText>(R.id.editSurname)
        val bio = view.findViewById<TextInputEditText>(R.id.editBio)
        val save = view.findViewById<MaterialButton>(R.id.editSave)

        name.doAfterTextChanged { nameLayout.error = null }
        surname.doAfterTextChanged { surnameLayout.error = null }
        bio.doAfterTextChanged { bioLayout.error = null }

        changeAvatar.setOnClickListener { chooseSource(view, MediaTarget.AVATAR) }
        changeBanner.setOnClickListener { chooseSource(view, MediaTarget.BANNER) }

        fun saveDetails() {
            val nameError = Validators.validateName(name.text.toString())
            val surnameError = Validators.validateSurname(surname.text.toString())
            val bioError = if (bio.text.toString().trim().length > MAX_BIO) R.string.err_bio_long else null
            nameLayout.error = nameError?.let { getString(it.messageRes()) }
            surnameLayout.error = surnameError?.let { getString(it.messageRes()) }
            bioLayout.error = bioError?.let { getString(it) }
            if (nameError == null && surnameError == null && bioError == null) {
                viewModel.saveDetails(name.text.toString(), surname.text.toString(), bio.text.toString())
            }
        }
        save.setOnClickListener { saveDetails() }

        var filled = savedInstanceState != null // after rotation the typed text is restored by the system
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.state.collect { state ->
                        val busy = state.uploading != null
                        progress.visibility = if (busy) View.VISIBLE else View.INVISIBLE
                        progressText.text = if (busy) getString(R.string.upload_working) else ""
                        listOf(changeAvatar, changeBanner, save).forEach { it.isEnabled = !busy }
                        avatar.showPicture(state.avatar, avatar = true)
                        banner.showPicture(state.banner, avatar = false)
                        val profile = state.profile
                        if (profile != null && !filled) {
                            filled = true
                            name.setText(profile.name)
                            surname.setText(profile.surname)
                            bio.setText(profile.bio)
                        }
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is ProfileEvent.UploadDone -> UiMessages.success(view, R.string.msg_picture_saved)
                            is ProfileEvent.Removed -> UiMessages.info(view, R.string.msg_picture_removed)
                            ProfileEvent.DetailsSaved -> {
                                UiMessages.success(view, R.string.msg_profile_saved)
                                findNavController().popBackStack()
                            }
                            is ProfileEvent.UploadFailed -> {
                                val message = if (event.reason == UploadFailure.UNREADABLE) R.string.err_picture_unreadable else R.string.err_picture_upload
                                UiMessages.error(view, message, onRetry = { viewModel.retryUpload() })
                            }
                        }
                    }
                }
            }
        }
    }

    /** Asks where the picture should come from: gallery, camera, or remove the current one. */
    private fun chooseSource(root: View, target: MediaTarget) {
        viewModel.pendingTarget = target
        val hasPicture = (if (target == MediaTarget.AVATAR) viewModel.state.value.avatar else viewModel.state.value.banner) != null
        val labels = buildList {
            add(getString(R.string.source_gallery))
            add(getString(R.string.source_camera))
            if (hasPicture) add(getString(R.string.source_remove))
        }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (target == MediaTarget.AVATAR) R.string.btn_change_photo else R.string.btn_change_banner)
            .setItems(labels) { _, which ->
                when (which) {
                    0 -> if (online(root)) pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    1 -> if (online(root)) launchCamera(root)
                    2 -> if (online(root)) viewModel.remove(target)
                }
            }
            .show()
    }

    /** Pictures are saved to the online profile, so a connection is needed. */
    private fun online(root: View): Boolean {
        val online = ConnectivityMonitor(requireContext()).isOnlineNow()
        if (!online) UiMessages.error(root, R.string.club_err_network)
        return online
    }

    private fun launchCamera(root: View) {
        val directory = File(requireContext().cacheDir, "camera").apply { mkdirs() }
        val file = File.createTempFile("photo_", ".jpg", directory)
        val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", file)
        viewModel.pendingCameraUri = uri
        try {
            takePhoto.launch(uri)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No camera app", e)
            UiMessages.error(root, R.string.err_no_camera)
        }
    }

    private companion object {
        const val TAG = "EditProfileFragment"
        const val MAX_BIO = 150
    }
}
