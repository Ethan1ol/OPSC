package com.example.opsc.clubs

import android.util.Log
import androidx.annotation.StringRes
import com.example.opsc.R
import com.example.opsc.media.MediaRepository
import com.example.opsc.notifications.NotificationRepository
import com.example.opsc.notifications.NotificationType
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** A club, stored at clubs/{clubId}. */
data class Club(
    val id: String,
    val name: String,
    val description: String,
    val ownerId: String,
    val inviteCode: String,
    val memberIds: List<String>,
    /** open (people can join) or active (league started). */
    val status: String
) {
    val isOpen get() = status == STATUS_OPEN

    companion object {
        const val STATUS_OPEN = "open"
        const val STATUS_ACTIVE = "active"
    }
}

/** Where a fixture is: scheduled, waiting for review, or confirmed. */
enum class FixtureStatus { SCHEDULED, PENDING, VERIFIED }

/** A fixture, stored at clubs/{clubId}/fixtures/{fixtureId}. */
data class Fixture(
    val id: String,
    val round: Int,
    val player1Id: String,
    val player2Id: String,
    val refereeId: String,
    val status: FixtureStatus,
    val player1Score: Int?,
    val player2Score: Int?,
    val winnerId: String?,
    /** Set when the referee attached a proof photo. */
    val proofUrl: String? = null
)

/** Why a club action failed. */
enum class ClubFailure(@StringRes val messageRes: Int) {
    NETWORK(R.string.club_err_network),
    NOT_FOUND(R.string.club_err_not_found),
    ALREADY_MEMBER(R.string.club_err_already_member),
    LEAGUE_STARTED(R.string.club_err_started),
    CLUB_FULL(R.string.club_err_full),
    NOT_ENOUGH_MEMBERS(R.string.club_err_not_enough),
    NOT_ALLOWED(R.string.club_err_not_allowed),
    PROOF_FAILED(R.string.club_err_proof),
    UNKNOWN(R.string.club_err_unknown)
}

/** Result of a club action. */
sealed interface ClubResult<out T> {
    data class Success<T>(val value: T) : ClubResult<T>
    data class Failure(val failure: ClubFailure) : ClubResult<Nothing>
}

/** Reads and writes clubs, fixtures and standings in Firestore. Lists update live and work from the offline cache. Create, join, start and verify need a connection. */
class ClubRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {
    private val notifications = NotificationRepository(db)
    private val media = MediaRepository.get(FirebaseFirestore.getInstance().app.applicationContext)

    private fun clubs() = db.collection("clubs")

    /** Counts the fixtures the user won in the given clubs. */
    suspend fun countWins(uid: String, clubIds: List<String>): Int {
        var total = 0
        for (id in clubIds) {
            try {
                total += withTimeoutOrNull(10_000L) {
                    clubs().document(id).collection("fixtures").whereEqualTo("winnerId", uid).get().await().size()
                } ?: 0
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not count wins in $id", e)
            }
        }
        return total
    }


    /** The user's clubs, updating live. */
    fun observeMyClubs(uid: String): Flow<List<Club>> = callbackFlow {
        val registration = clubs().whereArrayContains("memberIds", uid).addSnapshotListener { snapshot, error ->
            if (error != null) { close(error); return@addSnapshotListener }
            trySend(snapshot?.documents.orEmpty().mapNotNull { it.toClub() }.sortedBy { it.name.lowercase() })
        }
        awaitClose { registration.remove() }
    }

    fun observeClub(clubId: String): Flow<Club?> = callbackFlow {
        val registration = clubs().document(clubId).addSnapshotListener { snapshot, error ->
            if (error != null) { close(error); return@addSnapshotListener }
            trySend(snapshot?.takeIf { it.exists() }?.toClub())
        }
        awaitClose { registration.remove() }
    }

    fun observeFixtures(clubId: String): Flow<List<Fixture>> = callbackFlow {
        val registration = clubs().document(clubId).collection("fixtures").addSnapshotListener { snapshot, error ->
            if (error != null) { close(error); return@addSnapshotListener }
            trySend(snapshot?.documents.orEmpty().mapNotNull { it.toFixture() }.sortedWith(compareBy({ it.round }, { it.id })))
        }
        awaitClose { registration.remove() }
    }

    fun observeStandings(clubId: String): Flow<List<StandingRow>> = callbackFlow {
        val registration = clubs().document(clubId).collection("standings").addSnapshotListener { snapshot, error ->
            if (error != null) { close(error); return@addSnapshotListener }
            trySend(snapshot?.documents.orEmpty().mapNotNull { it.toStanding() })
        }
        awaitClose { registration.remove() }
    }

    /** Looks up usernames for the given user ids. */
    suspend fun usernames(uids: Collection<String>): Map<String, String> {
        val result = mutableMapOf<String, String>()
        try {
            uids.distinct().chunked(10).forEach { chunk ->
                val snapshot = db.collection("users").whereIn(FieldPath.documentId(), chunk).get().await()
                snapshot.documents.forEach { doc -> doc.getString("username")?.takeIf { it.isNotBlank() }?.let { result[doc.id] = it } }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not load usernames", e)
        }
        return result
    }


    /** Creates a club with a new invite code. The creator is the owner. Returns the club id. */
    suspend fun createClub(uid: String, name: String, description: String): ClubResult<String> = guarded("createClub") {
        var code = InviteCode.generate()
        var attempts = 0
        // Make sure the code is not already used.
        while (attempts++ < 5 && !clubs().whereEqualTo("inviteCode", code).limit(1).get().await().isEmpty) code = InviteCode.generate()
        val ref = clubs().document()
        ref.set(
            mapOf(
                "name" to name.trim(), "description" to description.trim(), "ownerId" to uid, "inviteCode" to code,
                "memberIds" to listOf(uid), "status" to Club.STATUS_OPEN, "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Log.i(TAG, "Club created ${ref.id}")
        ClubResult.Success(ref.id)
    }

    /** Joins the club with this invite code. Returns the club id. */
    suspend fun joinByCode(uid: String, rawCode: String): ClubResult<String> = guarded("joinByCode") {
        val code = InviteCode.normalise(rawCode)
        if (!InviteCode.isValid(code)) return@guarded ClubResult.Failure(ClubFailure.NOT_FOUND)
        val found = clubs().whereEqualTo("inviteCode", code).limit(1).get().await().documents.firstOrNull()?.toClub()
            ?: return@guarded ClubResult.Failure(ClubFailure.NOT_FOUND)
        when {
            uid in found.memberIds -> ClubResult.Failure(ClubFailure.ALREADY_MEMBER)
            !found.isOpen -> ClubResult.Failure(ClubFailure.LEAGUE_STARTED)
            found.memberIds.size >= LeagueEngine.MAX_MEMBERS -> ClubResult.Failure(ClubFailure.CLUB_FULL)
            else -> {
                clubs().document(found.id).update("memberIds", FieldValue.arrayUnion(uid)).await()
                Log.i(TAG, "Joined club ${found.id}")
                ClubResult.Success(found.id)
            }
        }
    }

    /** Starts the league (owner only): creates all fixtures and a standings row per member, then sets the club to active. Fixture ids are fixed, so retrying is safe. Writes go in small batches. */
    suspend fun startLeague(club: Club, uid: String): ClubResult<Unit> {
        if (uid != club.ownerId) return ClubResult.Failure(ClubFailure.NOT_ALLOWED)
        if (!LeagueEngine.canStart(club.memberIds.size)) return ClubResult.Failure(ClubFailure.NOT_ENOUGH_MEMBERS)
        return guarded("startLeague") {
            val specs = LeagueEngine.generateFixtures(club.memberIds)
            val fixtures = clubs().document(club.id).collection("fixtures")
            specs.chunked(FIXTURE_BATCH).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { spec ->
                    batch.set(
                        fixtures.document("r${spec.round}_${spec.player1Id}_${spec.player2Id}"),
                        mapOf(
                            "round" to spec.round, "player1Id" to spec.player1Id, "player2Id" to spec.player2Id,
                            "refereeId" to spec.refereeId, "status" to FixtureStatus.SCHEDULED.name
                        )
                    )
                }
                batch.commit().await()
            }
            val last = db.batch()
            club.memberIds.forEach { member ->
                last.set(
                    clubs().document(club.id).collection("standings").document(member),
                    mapOf("uid" to member, "played" to 0, "won" to 0, "drawn" to 0, "lost" to 0, "points" to 0)
                )
            }
            last.update(clubs().document(club.id), "status", Club.STATUS_ACTIVE)
            // Tell every member the fixtures are ready, and tell each referee their match count.
            club.memberIds.forEach { member ->
                notifications.queue(last, member, uid, NotificationType.FIXTURES_SCHEDULED, club.id, club.name, listOf(specs.size.toString()))
            }
            specs.groupingBy { it.refereeId }.eachCount().forEach { (referee, count) ->
                notifications.queue(last, referee, uid, NotificationType.REFEREE_ASSIGNED, club.id, club.name, listOf(count.toString()))
            }
            last.commit().await()
            Log.i(TAG, "League started for ${club.id}: ${specs.size} fixtures")
            ClubResult.Success(Unit)
        }
    }

    /** The referee confirms a result: saves the scores and winner, marks the fixture verified and updates the standings. */
    suspend fun verifyFixture(
        club: Club, fixtureId: String, player1Score: Int, player2Score: Int,
        proof: ByteArray? = null, names: Map<String, String> = emptyMap(), refereeUid: String = ""
    ): ClubResult<Unit> {
        if (!LeagueEngine.isValidScore(player1Score) || !LeagueEngine.isValidScore(player2Score)) return ClubResult.Failure(ClubFailure.UNKNOWN)
        return guarded("verifyFixture") {
            val fixtureRef = clubs().document(club.id).collection("fixtures").document(fixtureId)
            val all = clubs().document(club.id).collection("fixtures").get(Source.SERVER).await().documents.mapNotNull { it.toFixture() }
            val target = all.firstOrNull { it.id == fixtureId } ?: return@guarded ClubResult.Failure(ClubFailure.NOT_FOUND)
            if (target.status == FixtureStatus.VERIFIED) return@guarded ClubResult.Failure(ClubFailure.NOT_ALLOWED)

            // Save the proof photo first, so a result is never confirmed without it.
            if (proof != null && media.upload(MediaRepository.proofPath(club.id, fixtureId), proof, 1L) == null) {
                return@guarded ClubResult.Failure(ClubFailure.PROOF_FAILED)
            }

            val updated = all.map { if (it.id == fixtureId) it.copy(status = FixtureStatus.VERIFIED, player1Score = player1Score, player2Score = player2Score) else it }
            val table = LeagueEngine.computeStandings(
                club.memberIds, updated.map { MatchResult(it.player1Id, it.player2Id, it.player1Score, it.player2Score, it.status == FixtureStatus.VERIFIED) }
            )
            val batch = db.batch()
            batch.update(
                fixtureRef, mapOf(
                    "player1Score" to player1Score, "player2Score" to player2Score,
                    "winnerId" to LeagueEngine.winnerId(target.player1Id, target.player2Id, player1Score, player2Score),
                    "status" to FixtureStatus.VERIFIED.name, "verifiedAt" to FieldValue.serverTimestamp()
                ) + (if (proof != null) mapOf("proofUrl" to "inline") else emptyMap())
            )
            // Players hear about their result; everyone else hears the table changed.
            val p1 = names[target.player1Id].orEmpty()
            val p2 = names[target.player2Id].orEmpty()
            club.memberIds.filter { it != refereeUid }.forEach { member ->
                if (member == target.player1Id || member == target.player2Id) {
                    notifications.queue(batch, member, refereeUid, NotificationType.RESULT_VERIFIED, club.id, club.name,
                        listOf(p1, player1Score.toString(), player2Score.toString(), p2))
                } else {
                    notifications.queue(batch, member, refereeUid, NotificationType.LEADERBOARD_CHANGED, club.id, club.name, emptyList())
                }
            }
            // Only the two players' rows change.
            table.filter { it.uid == target.player1Id || it.uid == target.player2Id }.forEach { row ->
                batch.set(
                    clubs().document(club.id).collection("standings").document(row.uid),
                    mapOf("uid" to row.uid, "played" to row.played, "won" to row.won, "drawn" to row.drawn, "lost" to row.lost, "points" to row.points)
                )
            }
            batch.commit().await()
            Log.i(TAG, "Fixture $fixtureId verified $player1Score-$player2Score")
            ClubResult.Success(Unit)
        }
    }


    /** Runs an action with a timeout and turns any failure into a ClubResult.Failure. */
    private suspend fun <T> guarded(label: String, block: suspend () -> ClubResult<T>): ClubResult<T> = try {
        withTimeoutOrNull(ACTION_TIMEOUT_MS) { block() } ?: ClubResult.Failure(ClubFailure.NETWORK).also { Log.w(TAG, "$label timed out") }
    } catch (e: CancellationException) {
        throw e
    } catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
        Log.e(TAG, "$label failed: ${e.code}", e)
        ClubResult.Failure(
            when (e.code) {
                com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE -> ClubFailure.NETWORK
                com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED -> ClubFailure.NOT_ALLOWED
                else -> ClubFailure.UNKNOWN
            }
        )
    } catch (e: Exception) {
        Log.e(TAG, "$label failed", e)
        ClubResult.Failure(ClubFailure.UNKNOWN)
    }

    private fun DocumentSnapshot.toClub(): Club? {
        val name = getString("name") ?: return null
        @Suppress("UNCHECKED_CAST")
        val members = (get("memberIds") as? List<String>).orEmpty()
        return Club(
            id = id, name = name, description = getString("description").orEmpty(), ownerId = getString("ownerId").orEmpty(),
            inviteCode = getString("inviteCode").orEmpty(), memberIds = members, status = getString("status") ?: Club.STATUS_OPEN
        )
    }

    private fun DocumentSnapshot.toFixture(): Fixture? {
        val p1 = getString("player1Id") ?: return null
        val p2 = getString("player2Id") ?: return null
        return Fixture(
            id = id, round = (getLong("round") ?: 1L).toInt(), player1Id = p1, player2Id = p2,
            refereeId = getString("refereeId").orEmpty(),
            status = runCatching { FixtureStatus.valueOf(getString("status").orEmpty()) }.getOrDefault(FixtureStatus.SCHEDULED),
            player1Score = getLong("player1Score")?.toInt(), player2Score = getLong("player2Score")?.toInt(),
            winnerId = getString("winnerId"),
            proofUrl = getString("proofUrl")
        )
    }

    private fun DocumentSnapshot.toStanding(): StandingRow? {
        val uid = getString("uid") ?: id
        return StandingRow(
            uid = uid, played = (getLong("played") ?: 0L).toInt(), won = (getLong("won") ?: 0L).toInt(),
            drawn = (getLong("drawn") ?: 0L).toInt(), lost = (getLong("lost") ?: 0L).toInt(), points = (getLong("points") ?: 0L).toInt()
        )
    }

    private companion object {
        const val TAG = "ClubRepository"
        const val ACTION_TIMEOUT_MS = 30_000L
        const val FIXTURE_BATCH = 15
    }
}
