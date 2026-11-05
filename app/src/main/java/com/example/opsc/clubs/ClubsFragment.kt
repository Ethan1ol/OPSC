package com.example.opsc.clubs

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.opsc.R
import com.example.opsc.ui.StateLayout
import com.example.opsc.ui.UiMessages
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

/** Opens the club detail screen. */
fun Fragment.openClub(clubId: String) {
    findNavController().navigate(R.id.clubDetailFragment, bundleOf("clubId" to clubId))
}

/**
 * The Clubs tab: your clubs, a "Join with code" box and a "Create club" button.
 * Clubs update live; joining and creating need an internet connection.
 */
class ClubsFragment : Fragment(R.layout.fragment_clubs) {
    private val viewModel: ClubsViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val stateLayout = view.findViewById<StateLayout>(R.id.clubsState)
        val list = view.findViewById<RecyclerView>(R.id.clubsList)
        val codeLayout = view.findViewById<TextInputLayout>(R.id.joinCodeLayout)
        val code = view.findViewById<TextInputEditText>(R.id.joinCode)
        val joinButton = view.findViewById<MaterialButton>(R.id.joinButton)
        val createButton = view.findViewById<MaterialButton>(R.id.createClubButton)
        val progress = view.findViewById<LinearProgressIndicator>(R.id.clubsProgress)

        val adapter = ClubAdapter(R.layout.item_club) { openClub(it.id) }
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = adapter

        code.doAfterTextChanged { codeLayout.error = null }
        fun join() {
            val typed = InviteCode.normalise(code.text.toString())
            if (!InviteCode.isValid(typed)) {
                codeLayout.error = getString(R.string.club_err_code_invalid)
                return
            }
            viewModel.join(typed)
        }
        joinButton.setOnClickListener { join() }
        code.setOnEditorActionListener { _, _, _ -> join(); true }
        createButton.setOnClickListener {
            if (childFragmentManager.findFragmentByTag(SHEET_TAG) == null) CreateClubSheet().show(childFragmentManager, SHEET_TAG)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.state.collect { state ->
                        when {
                            state.loading -> stateLayout.showLoading()
                            state.error -> stateLayout.showError(R.string.clubs_load_error) { viewModel.retry() }
                            state.clubs.isEmpty() -> stateLayout.showEmpty(R.drawable.ic_nav_clubs, R.string.clubs_empty)
                            else -> {
                                adapter.submitList(state.clubs)
                                stateLayout.showContent()
                            }
                        }
                    }
                }
                launch {
                    viewModel.busy.collect { busy ->
                        joinButton.isEnabled = !busy
                        createButton.isEnabled = !busy
                        if (busy) progress.show() else progress.hide()
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is ClubsEvent.Created -> { code.text = null; openClub(event.clubId) }
                            is ClubsEvent.Joined -> { code.text = null; openClub(event.clubId) }
                            is ClubsEvent.Failed -> UiMessages.error(view, event.failure.messageRes)
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val SHEET_TAG = "create_club_sheet"
    }
}

/** Bottom sheet to create a club (name and optional description). */
class CreateClubSheet : BottomSheetDialogFragment() {
    private val viewModel: ClubsViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.sheet_create_club, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val nameLayout = view.findViewById<TextInputLayout>(R.id.clubNameLayout)
        val descLayout = view.findViewById<TextInputLayout>(R.id.clubDescLayout)
        val name = view.findViewById<TextInputEditText>(R.id.clubNameInput)
        val desc = view.findViewById<TextInputEditText>(R.id.clubDescInput)
        name.doAfterTextChanged { nameLayout.error = null }
        desc.doAfterTextChanged { descLayout.error = null }

        fun save() {
            val nameError = ClubValidator.validateName(name.text.toString())
            val descError = ClubValidator.validateDescription(desc.text.toString())
            nameLayout.error = nameError?.let { getString(if (it == ClubError.NAME_TOO_SHORT) R.string.club_err_name_short else R.string.club_err_name_long) }
            descLayout.error = descError?.let { getString(R.string.club_err_desc_long) }
            if (nameError != null || descError != null) {
                Log.d(TAG, "Create-club form invalid")
                return
            }
            viewModel.create(name.text.toString(), desc.text.toString())
            dismiss()
        }
        view.findViewById<View>(R.id.clubCreateSave).setOnClickListener { save() }
        desc.setOnEditorActionListener { _, _, _ -> save(); true }
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.behavior?.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private companion object {
        const val TAG = "CreateClubSheet"
    }
}
