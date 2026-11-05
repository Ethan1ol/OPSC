package com.example.opsc.clubs

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import coil3.load
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import android.widget.ImageView
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.navigation.fragment.findNavController
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.opsc.R
import com.example.opsc.ui.ConfirmDialog
import com.example.opsc.ui.StateLayout
import com.example.opsc.ui.UiMessages
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

/** One club: invite code, Start league button for the owner, and tabs for Leaderboard, Fixtures and Members. Updates live. */
class ClubDetailFragment : Fragment(R.layout.fragment_club_detail) {
    private val viewModel: ClubDetailViewModel by viewModels()
    private val standings = StandingAdapter()
    private val members = MemberAdapter { member ->
        findNavController().navigate(R.id.memberProfileFragment, bundleOf("uid" to member.uid))
    }
    private lateinit var fixtures: FixtureAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val stateLayout = view.findViewById<StateLayout>(R.id.clubDetailState)
        val tabs = view.findViewById<TabLayout>(R.id.detailTabs)
        val tabViews = listOf<View>(view.findViewById(R.id.tabLeaderboard), view.findViewById(R.id.tabFixtures), view.findViewById(R.id.tabMembers))

        fixtures = FixtureAdapter({ item -> openResultSheet(item) }, { item -> openProof(item) })
        setUpList(view.findViewById(R.id.leaderboardList), standings)
        setUpList(view.findViewById(R.id.fixturesList), fixtures)
        setUpList(view.findViewById(R.id.membersList), members)

        listOf(R.string.tab_leaderboard, R.string.tab_fixtures, R.string.tab_members).forEach { tabs.addTab(tabs.newTab().setText(it)) }
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = tabViews.forEachIndexed { i, v -> v.visibility = if (i == tab.position) View.VISIBLE else View.GONE }
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
        // After rotation, show the section for the selected tab.
        tabViews.forEachIndexed { i, v -> v.visibility = if (i == tabs.selectedTabPosition.coerceAtLeast(0)) View.VISIBLE else View.GONE }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect { render(view, stateLayout, it) } }
                launch {
                    viewModel.busy.collect { busy -> view.findViewById<MaterialButton>(R.id.startLeague).isEnabled = !busy }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            ClubDetailEvent.LeagueStarted -> UiMessages.success(view, R.string.club_league_started)
                            ClubDetailEvent.ResultSaved -> UiMessages.success(view, R.string.club_result_saved)
                            is ClubDetailEvent.Failed -> UiMessages.error(view, event.failure.messageRes)
                        }
                    }
                }
            }
        }
    }

    private fun setUpList(list: RecyclerView, adapter: RecyclerView.Adapter<*>) {
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = adapter
    }

    private fun render(root: View, stateLayout: StateLayout, state: ClubDetailState) {
        val club = state.club
        when {
            state.loading -> { stateLayout.showLoading(); return }
            state.error || club == null -> { stateLayout.showError(R.string.club_load_error) { viewModel.retry() }; return }
        }
        club!!
        stateLayout.showContent()
        root.findViewById<TextView>(R.id.detailClubName).text = club.name
        root.findViewById<TextView>(R.id.detailClubDescription).apply { text = club.description; visibility = if (club.description.isBlank()) View.GONE else View.VISIBLE }
        root.findViewById<TextView>(R.id.detailInviteCode).text = club.inviteCode
        root.findViewById<View>(R.id.detailShare).setOnClickListener { shareInvite(club) }

        // Start league card (owner, before the league starts).
        val startCard = root.findViewById<MaterialCardView>(R.id.startCard)
        startCard.visibility = if (state.isOwner && club.isOpen) View.VISIBLE else View.GONE
        root.findViewById<TextView>(R.id.startHint).text =
            if (state.canStart) getString(R.string.start_hint_ready, club.memberIds.size, LeagueEngine.fixtureCount(club.memberIds.size))
            else getString(R.string.start_hint_need_more, LeagueEngine.MIN_MEMBERS, club.memberIds.size)
        val startButton = root.findViewById<MaterialButton>(R.id.startLeague)
        startButton.isEnabled = state.canStart && !viewModel.busy.value
        startButton.setOnClickListener {
            ConfirmDialog.show(requireContext(), R.string.start_confirm_title, R.string.start_confirm_message, R.string.btn_start_league) { viewModel.startLeague() }
        }

        // League finished banner.
        val winnerCard = root.findViewById<MaterialCardView>(R.id.winnerCard)
        winnerCard.visibility = if (state.leagueComplete) View.VISIBLE else View.GONE
        if (state.leagueComplete) {
            root.findViewById<TextView>(R.id.winnerText).text = getString(R.string.league_complete, state.championNames.joinToString(" & "))
        }

        // Leaderboard: podium and the rest.
        val started = !club.isOpen
        root.findViewById<View>(R.id.podium).visibility = if (started) View.VISIBLE else View.GONE
        root.findViewById<View>(R.id.leaderboardHeader).visibility = if (started) View.VISIBLE else View.GONE
        root.findViewById<View>(R.id.leaderboardEmpty).visibility = if (started) View.GONE else View.VISIBLE
        val top = state.leaderboard
        listOf(1 to (R.id.podium1 to (R.id.podiumName1 to R.id.podiumPts1)), 2 to (R.id.podium2 to (R.id.podiumName2 to R.id.podiumPts2)), 3 to (R.id.podium3 to (R.id.podiumName3 to R.id.podiumPts3)))
            .forEach { (place, ids) ->
                val row = top.getOrNull(place - 1)
                root.findViewById<View>(ids.first).visibility = if (row != null) View.VISIBLE else View.INVISIBLE
                root.findViewById<TextView>(ids.second.first).text = row?.name.orEmpty()
                root.findViewById<TextView>(ids.second.second).text = row?.let { getString(R.string.points_short, it.row.points) }.orEmpty()
            }
        standings.submitList(if (started) state.leaderboard else emptyList())

        // Fixtures and members.
        fixtures.submitList(groupByRound(state.fixtures))
        root.findViewById<View>(R.id.fixturesEmpty).visibility = if (state.fixtures.isEmpty()) View.VISIBLE else View.GONE
        members.submitList(state.members)
    }

    private fun shareInvite(club: Club) {
        val text = getString(R.string.share_invite_text, club.name, club.inviteCode)
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, getString(R.string.btn_share)))
    }

    private fun openProof(item: FixtureView) {
        val club = viewModel.state.value.club ?: return
        if (childFragmentManager.findFragmentByTag(PROOF_TAG) == null) {
            ProofDialogFragment.newInstance(club.id, item.fixture.id).show(childFragmentManager, PROOF_TAG)
        }
    }

    private fun openResultSheet(item: FixtureView) {
        if (childFragmentManager.findFragmentByTag(RESULT_TAG) != null) return
        ResultSheet.newInstance(item).show(childFragmentManager, RESULT_TAG)
    }

    private companion object {
        const val RESULT_TAG = "result_sheet"
        const val PROOF_TAG = "proof_dialog"
    }
}

/** Bottom sheet where the referee enters both scores, can attach a proof photo and confirms the result. */
class ResultSheet : BottomSheetDialogFragment() {
    private val viewModel: ClubDetailViewModel by viewModels(ownerProducer = { requireParentFragment() })
    private var proofUri: Uri? = null
    private var cameraUri: Uri? = null
    private var thumbnail: ImageView? = null
    private var removeButton: View? = null

    private val pickProof = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) setProof(uri) }
    private val takeProof = registerForActivityResult(ActivityResultContracts.TakePicture()) { saved -> if (saved) cameraUri?.let { setProof(it) } }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.sheet_result, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        proofUri = savedInstanceState?.getParcelable(STATE_PROOF)
        cameraUri = savedInstanceState?.getParcelable(STATE_CAMERA)
        val args = requireArguments()
        val fixtureId = args.getString(ARG_ID).orEmpty()
        val name1 = args.getString(ARG_NAME1).orEmpty()
        val name2 = args.getString(ARG_NAME2).orEmpty()
        view.findViewById<TextView>(R.id.resultMatch).text = getString(R.string.fixture_title, name1, name2)

        val layout1 = view.findViewById<TextInputLayout>(R.id.resultScore1Layout).apply { hint = getString(R.string.result_score_for, name1) }
        val layout2 = view.findViewById<TextInputLayout>(R.id.resultScore2Layout).apply { hint = getString(R.string.result_score_for, name2) }
        val score1 = view.findViewById<TextInputEditText>(R.id.resultScore1)
        val score2 = view.findViewById<TextInputEditText>(R.id.resultScore2)
        score1.doAfterTextChanged { layout1.error = null }
        score2.doAfterTextChanged { layout2.error = null }

        thumbnail = view.findViewById(R.id.resultProofThumb)
        removeButton = view.findViewById<View>(R.id.resultProofRemove).apply { setOnClickListener { proofUri = null; showProof() } }
        view.findViewById<View>(R.id.resultProofAttach).setOnClickListener { chooseProofSource() }
        showProof()

        fun save() {
            val s1 = score1.text.toString().trim().toIntOrNull()
            val s2 = score2.text.toString().trim().toIntOrNull()
            layout1.error = if (LeagueEngine.isValidScore(s1)) null else getString(R.string.result_err_score)
            layout2.error = if (LeagueEngine.isValidScore(s2)) null else getString(R.string.result_err_score)
            if (s1 == null || s2 == null || layout1.error != null || layout2.error != null) return
            viewModel.saveResult(fixtureId, s1, s2, proofUri)
            dismiss()
        }
        view.findViewById<MaterialButton>(R.id.resultSave).setOnClickListener { save() }
        score2.setOnEditorActionListener { _, _, _ -> save(); true }
    }

    private fun setProof(uri: Uri) {
        proofUri = uri
        showProof()
    }

    private fun showProof() {
        val uri = proofUri
        thumbnail?.visibility = if (uri == null) View.GONE else View.VISIBLE
        removeButton?.visibility = if (uri == null) View.GONE else View.VISIBLE
        if (uri != null) thumbnail?.load(uri)
    }

    private fun chooseProofSource() {
        val labels = arrayOf(getString(R.string.source_gallery), getString(R.string.source_camera))
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.result_attach_proof).setItems(labels) { _, which ->
            if (which == 0) {
                pickProof.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            } else {
                val directory = File(requireContext().cacheDir, "camera").apply { mkdirs() }
                val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", File.createTempFile("proof_", ".jpg", directory))
                cameraUri = uri
                try { takeProof.launch(uri) } catch (e: ActivityNotFoundException) { view?.let { UiMessages.error(it, R.string.err_no_camera) } }
            }
        }.show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(STATE_PROOF, proofUri)
        outState.putParcelable(STATE_CAMERA, cameraUri)
    }

    override fun onDestroyView() {
        thumbnail = null
        removeButton = null
        super.onDestroyView()
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.behavior?.state = BottomSheetBehavior.STATE_EXPANDED
    }

    companion object {
        private const val ARG_ID = "fixtureId"
        private const val ARG_NAME1 = "name1"
        private const val ARG_NAME2 = "name2"
        private const val STATE_PROOF = "proofUri"
        private const val STATE_CAMERA = "cameraUri"

        fun newInstance(item: FixtureView) = ResultSheet().apply {
            arguments = bundleOf(ARG_ID to item.fixture.id, ARG_NAME1 to item.player1Name, ARG_NAME2 to item.player2Name)
        }
    }
}
