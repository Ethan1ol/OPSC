package com.example.opsc.clubs

import android.app.Application
import android.net.Uri
import com.example.opsc.media.ImageProcessor
import com.example.opsc.media.ImageSpec
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.opsc.ui.ConnectivityMonitor
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ------------------------------------------------------------------ clubs list

/** What the clubs list screen shows. */
data class ClubsUiState(val loading: Boolean = true, val clubs: List<Club> = emptyList(), val error: Boolean = false)

/** One-off results for the clubs list screen. */
sealed interface ClubsEvent {
    data class Created(val clubId: String) : ClubsEvent
    data class Joined(val clubId: String) : ClubsEvent
    data class Failed(val failure: ClubFailure) : ClubsEvent
}

/** Loads the user's clubs live and runs "create club" and "join with code". */
@OptIn(ExperimentalCoroutinesApi::class)
class ClubsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ClubRepository()
    private val connectivity = ConnectivityMonitor(application)
    private val uid: String? = FirebaseAuth.getInstance().currentUser?.uid
    private val retryTrigger = MutableStateFlow(0)

    val state: StateFlow<ClubsUiState> = retryTrigger.flatMapLatest {
        if (uid == null) flowOf(ClubsUiState(loading = false))
        else repository.observeMyClubs(uid)
            .map { ClubsUiState(loading = false, clubs = it) }
            .catch { emit(ClubsUiState(loading = false, error = true)) }
            .onStart { emit(ClubsUiState(loading = true)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ClubsUiState())

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _events = Channel<ClubsEvent>(Channel.BUFFERED)
    val events: Flow<ClubsEvent> = _events.receiveAsFlow()

    fun retry() {
        retryTrigger.value += 1
    }

    fun create(name: String, description: String) = act { owner ->
        when (val result = repository.createClub(owner, name, description)) {
            is ClubResult.Success -> ClubsEvent.Created(result.value)
            is ClubResult.Failure -> ClubsEvent.Failed(result.failure)
        }
    }

    fun join(code: String) = act { member ->
        when (val result = repository.joinByCode(member, code)) {
            is ClubResult.Success -> ClubsEvent.Joined(result.value)
            is ClubResult.Failure -> ClubsEvent.Failed(result.failure)
        }
    }

    /** These actions need a connection (they must not be queued for later), so check first. */
    private fun act(block: suspend (String) -> ClubsEvent) {
        val user = uid ?: return
        if (_busy.value) return
        if (!connectivity.isOnlineNow()) {
            _events.trySend(ClubsEvent.Failed(ClubFailure.NETWORK))
            return
        }
        _busy.value = true
        viewModelScope.launch {
            val event = block(user)
            _busy.value = false
            _events.send(event)
        }
    }
}

// ------------------------------------------------------------------ club detail

/** One row of the leaderboard with the player's name. */
data class RankedRow(val rank: Int, val row: StandingRow, val name: String, val isMe: Boolean)

/** One fixture with all names resolved, and whether the signed-in user may enter its result. */
data class FixtureView(val fixture: Fixture, val player1Name: String, val player2Name: String, val refereeName: String, val canReferee: Boolean) {
    val hasProof get() = !fixture.proofUrl.isNullOrBlank()
}

/** A member of the club. */
data class MemberView(val uid: String, val name: String, val isOwner: Boolean, val isMe: Boolean)

/** Everything the club detail screen shows. */
data class ClubDetailState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val club: Club? = null,
    val myUid: String = "",
    val leaderboard: List<RankedRow> = emptyList(),
    val fixtures: List<FixtureView> = emptyList(),
    val members: List<MemberView> = emptyList(),
    val leagueComplete: Boolean = false,
    val championNames: List<String> = emptyList(),
    val canStart: Boolean = false,
    val isOwner: Boolean = false
)

/** One-off results for the club detail screen. */
sealed interface ClubDetailEvent {
    data object LeagueStarted : ClubDetailEvent
    data object ResultSaved : ClubDetailEvent
    data class Failed(val failure: ClubFailure) : ClubDetailEvent
}

/**
 * Live state of one club: members, fixtures and the league table (worked out from the verified
 * fixtures, so it is always consistent). Also starts the league (owner) and saves results (referee).
 */
class ClubDetailViewModel(application: Application, savedStateHandle: SavedStateHandle) : AndroidViewModel(application) {
    private val repository = ClubRepository()
    private val connectivity = ConnectivityMonitor(application)
    val clubId: String = savedStateHandle["clubId"] ?: ""
    private val myUid: String = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()

    private val names = MutableStateFlow<Map<String, String>>(emptyMap())
    private val retryTrigger = MutableStateFlow(0)
    private val clubFlow: Flow<Club?> = repository.observeClub(clubId)

    init {
        // Whenever the member list changes, look up their usernames.
        viewModelScope.launch {
            clubFlow.filterNotNull().map { it.memberIds }.distinctUntilChanged().catch { }.collect { ids ->
                names.value = names.value + repository.usernames(ids)
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<ClubDetailState> = retryTrigger.flatMapLatest {
        combine(clubFlow, repository.observeFixtures(clubId), names) { club, fixtures, nameMap -> build(club, fixtures, nameMap) }
            .catch { emit(ClubDetailState(loading = false, error = true)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ClubDetailState())

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _events = Channel<ClubDetailEvent>(Channel.BUFFERED)
    val events: Flow<ClubDetailEvent> = _events.receiveAsFlow()

    fun retry() {
        retryTrigger.value += 1
    }

    fun startLeague() = act {
        val club = state.value.club ?: return@act ClubDetailEvent.Failed(ClubFailure.NOT_FOUND)
        when (val result = repository.startLeague(club, myUid)) {
            is ClubResult.Success -> ClubDetailEvent.LeagueStarted
            is ClubResult.Failure -> ClubDetailEvent.Failed(result.failure)
        }
    }

    fun saveResult(fixtureId: String, player1Score: Int, player2Score: Int, proof: Uri? = null) = act {
        val club = state.value.club ?: return@act ClubDetailEvent.Failed(ClubFailure.NOT_FOUND)
        // Shrink the proof photo first (a Firestore document is limited to 1 MiB).
        val proofBytes = proof?.let { ImageProcessor.process(getApplication(), it, ImageSpec.PROOF) }
        if (proof != null && proofBytes == null) return@act ClubDetailEvent.Failed(ClubFailure.PROOF_FAILED)
        when (val result = repository.verifyFixture(club, fixtureId, player1Score, player2Score, proofBytes, names.value, myUid)) {
            is ClubResult.Success -> ClubDetailEvent.ResultSaved
            is ClubResult.Failure -> ClubDetailEvent.Failed(result.failure)
        }
    }

    private fun act(block: suspend () -> ClubDetailEvent) {
        if (_busy.value) return
        if (!connectivity.isOnlineNow()) {
            _events.trySend(ClubDetailEvent.Failed(ClubFailure.NETWORK))
            return
        }
        _busy.value = true
        viewModelScope.launch {
            val event = block()
            _busy.value = false
            _events.send(event)
        }
    }

    private fun build(club: Club?, fixtures: List<Fixture>, nameMap: Map<String, String>): ClubDetailState {
        if (club == null) return ClubDetailState(loading = false, error = true)
        fun name(uid: String) = nameMap[uid] ?: "?"
        val results = fixtures.map { MatchResult(it.player1Id, it.player2Id, it.player1Score, it.player2Score, it.status == FixtureStatus.VERIFIED) }
        val table = LeagueEngine.computeStandings(club.memberIds, results)
        val ranks = LeagueEngine.ranks(table)
        val verified = fixtures.count { it.status == FixtureStatus.VERIFIED }
        val complete = LeagueEngine.isComplete(fixtures.size, verified)
        return ClubDetailState(
            loading = false,
            club = club,
            myUid = myUid,
            leaderboard = table.mapIndexed { i, row -> RankedRow(ranks[i], row, name(row.uid), row.uid == myUid) },
            fixtures = fixtures.map {
                FixtureView(it, name(it.player1Id), name(it.player2Id), name(it.refereeId), canReferee = it.refereeId == myUid && it.status != FixtureStatus.VERIFIED)
            },
            members = club.memberIds.map { MemberView(it, name(it), it == club.ownerId, it == myUid) },
            leagueComplete = complete,
            championNames = if (complete) LeagueEngine.champions(table).map { name(it.uid) } else emptyList(),
            canStart = club.isOpen && myUid == club.ownerId && LeagueEngine.canStart(club.memberIds.size),
            isOwner = myUid == club.ownerId
        )
    }
}
