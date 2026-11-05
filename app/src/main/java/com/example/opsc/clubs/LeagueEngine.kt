package com.example.opsc.clubs

import kotlin.random.Random

/** One match to be played: two players and the member who referees it. */
data class FixtureSpec(val round: Int, val player1Id: String, val player2Id: String, val refereeId: String)

/** A finished match as the standings calculation sees it. Scores are null until a referee has verified the result. */
data class MatchResult(val player1Id: String, val player2Id: String, val player1Score: Int?, val player2Score: Int?, val verified: Boolean)

/** One member's row in the league table. */
data class StandingRow(val uid: String, val played: Int, val won: Int, val drawn: Int, val lost: Int, val points: Int)

/** How a match ended. */
enum class Outcome { PLAYER1_WIN, PLAYER2_WIN, DRAW }

/**
 * The rules of the Battle Fitness league, in pure Kotlin so they can be unit tested:
 * every member plays every other member three times, a member who is not playing referees,
 * a win is 3 points, a draw 1 each, a loss 0, and most points when all fixtures are verified wins.
 */
object LeagueEngine {
    const val ROUNDS = 3
    const val POINTS_WIN = 3
    const val POINTS_DRAW = 1
    const val POINTS_LOSS = 0
    const val MIN_MEMBERS = 3
    const val MAX_MEMBERS = 12
    const val MAX_SCORE = 999

    /** A league can start with at least 3 members (two players plus a referee) and at most [MAX_MEMBERS]. */
    fun canStart(memberCount: Int): Boolean = memberCount in MIN_MEMBERS..MAX_MEMBERS

    /** How many fixtures a league of [memberCount] members has: every pair, three times. */
    fun fixtureCount(memberCount: Int): Int = ROUNDS * memberCount * (memberCount - 1) / 2

    /**
     * Creates every fixture: for each of the 3 rounds, every pair of members plays once (home and
     * away swap each round). The referee is a member who is not in the match, chosen to spread the
     * refereeing evenly.
     */
    fun generateFixtures(memberIds: List<String>): List<FixtureSpec> {
        val members = memberIds.distinct()
        require(members.size >= MIN_MEMBERS) { "A league needs at least $MIN_MEMBERS members" }
        val refereeCount = members.associateWith { 0 }.toMutableMap()
        // When two candidates have refereed equally often, pick the one who refereed longest ago.
        val lastTurn = members.associateWith { -1 }.toMutableMap()
        var turn = 0
        val fixtures = mutableListOf<FixtureSpec>()
        for (round in 1..ROUNDS) {
            for (i in members.indices) {
                for (j in i + 1 until members.size) {
                    val (first, second) = if (round % 2 == 1) members[i] to members[j] else members[j] to members[i]
                    val referee = members.filter { it != first && it != second }
                        .minWith(compareBy<String> { refereeCount.getValue(it) }.thenBy { lastTurn.getValue(it) })
                    refereeCount[referee] = refereeCount.getValue(referee) + 1
                    lastTurn[referee] = turn++
                    fixtures += FixtureSpec(round, first, second, referee)
                }
            }
        }
        return balanceReferees(members, fixtures)
    }

    /**
     * Evens out the refereeing: while the busiest referee has done more than one match more than the
     * quietest, move one of the busiest referee's matches (that the quietest member is not playing in)
     * to the quietest member.
     */
    private fun balanceReferees(members: List<String>, fixtures: List<FixtureSpec>): List<FixtureSpec> {
        val referees = fixtures.map { it.refereeId }.toMutableList()
        val counts = members.associateWith { 0 }.toMutableMap()
        referees.forEach { counts[it] = counts.getValue(it) + 1 }
        repeat(fixtures.size * members.size) {
            val busiest = members.maxBy { counts.getValue(it) }
            val quietest = members.minBy { counts.getValue(it) }
            if (counts.getValue(busiest) - counts.getValue(quietest) <= 1) return@repeat
            val index = fixtures.indices.firstOrNull {
                referees[it] == busiest && fixtures[it].player1Id != quietest && fixtures[it].player2Id != quietest
            } ?: return@repeat
            referees[index] = quietest
            counts[busiest] = counts.getValue(busiest) - 1
            counts[quietest] = counts.getValue(quietest) + 1
        }
        return fixtures.mapIndexed { index, fixture -> fixture.copy(refereeId = referees[index]) }
    }

    /** Is this a score a referee can enter? Both must be whole numbers from 0 to [MAX_SCORE]. */
    fun isValidScore(score: Int?): Boolean = score != null && score in 0..MAX_SCORE

    /** Who won, from the two scores. */
    fun outcome(player1Score: Int, player2Score: Int): Outcome = when {
        player1Score > player2Score -> Outcome.PLAYER1_WIN
        player2Score > player1Score -> Outcome.PLAYER2_WIN
        else -> Outcome.DRAW
    }

    /** The winner's id for a verified match, or null for a draw. */
    fun winnerId(player1Id: String, player2Id: String, player1Score: Int, player2Score: Int): String? =
        when (outcome(player1Score, player2Score)) {
            Outcome.PLAYER1_WIN -> player1Id
            Outcome.PLAYER2_WIN -> player2Id
            Outcome.DRAW -> null
        }

    /**
     * Builds the league table from the matches. Only verified matches count. Sorted by points, then
     * wins, then (so the order never jumps around) by id. Every member appears, even with no matches.
     */
    fun computeStandings(memberIds: List<String>, results: List<MatchResult>): List<StandingRow> {
        val won = memberIds.associateWith { 0 }.toMutableMap()
        val drawn = memberIds.associateWith { 0 }.toMutableMap()
        val lost = memberIds.associateWith { 0 }.toMutableMap()
        for (match in results) {
            val s1 = match.player1Score
            val s2 = match.player2Score
            if (!match.verified || s1 == null || s2 == null) continue
            if (match.player1Id !in won || match.player2Id !in won) continue
            when (outcome(s1, s2)) {
                Outcome.PLAYER1_WIN -> { won[match.player1Id] = won.getValue(match.player1Id) + 1; lost[match.player2Id] = lost.getValue(match.player2Id) + 1 }
                Outcome.PLAYER2_WIN -> { won[match.player2Id] = won.getValue(match.player2Id) + 1; lost[match.player1Id] = lost.getValue(match.player1Id) + 1 }
                Outcome.DRAW -> { drawn[match.player1Id] = drawn.getValue(match.player1Id) + 1; drawn[match.player2Id] = drawn.getValue(match.player2Id) + 1 }
            }
        }
        return memberIds.distinct().map { id ->
            val w = won.getValue(id)
            val d = drawn.getValue(id)
            val l = lost.getValue(id)
            StandingRow(id, played = w + d + l, won = w, drawn = d, lost = l, points = w * POINTS_WIN + d * POINTS_DRAW + l * POINTS_LOSS)
        }.sortedWith(compareByDescending<StandingRow> { it.points }.thenByDescending { it.won }.thenBy { it.uid })
    }

    /** Rank of each row, where rows with the same points and wins share a rank (1, 1, 3, ...). */
    fun ranks(sorted: List<StandingRow>): List<Int> {
        val result = mutableListOf<Int>()
        for ((index, row) in sorted.withIndex()) {
            val previous = sorted.getOrNull(index - 1)
            result += if (previous != null && previous.points == row.points && previous.won == row.won) result[index - 1] else index + 1
        }
        return result
    }

    /** The league is complete when there are fixtures and every one has been verified. */
    fun isComplete(totalFixtures: Int, verifiedFixtures: Int): Boolean = totalFixtures > 0 && verifiedFixtures == totalFixtures

    /** The champion(s): everyone sharing first place in a finished league. */
    fun champions(sorted: List<StandingRow>): List<StandingRow> {
        val rank = ranks(sorted)
        return sorted.filterIndexed { index, _ -> rank[index] == 1 }
    }
}

/** Invite codes: 6 characters, easy to read out loud (no 0/O or 1/I/L). */
object InviteCode {
    const val LENGTH = 6
    const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

    fun generate(random: Random = Random.Default): String = buildString { repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }

    /** Cleans what the user typed: upper case, and spaces or dashes removed. */
    fun normalise(input: String): String = input.uppercase().filter { it.isLetterOrDigit() }

    fun isValid(code: String): Boolean = code.length == LENGTH && code.all { it in ALPHABET }
}

/** Why a club form is invalid. */
enum class ClubError { NAME_TOO_SHORT, NAME_TOO_LONG, DESCRIPTION_TOO_LONG }

/** Validation for the create-club form. */
object ClubValidator {
    const val MIN_NAME = 3
    const val MAX_NAME = 40
    const val MAX_DESCRIPTION = 200

    fun validateName(name: String): ClubError? = when {
        name.trim().length < MIN_NAME -> ClubError.NAME_TOO_SHORT
        name.trim().length > MAX_NAME -> ClubError.NAME_TOO_LONG
        else -> null
    }

    fun validateDescription(description: String): ClubError? =
        if (description.trim().length > MAX_DESCRIPTION) ClubError.DESCRIPTION_TOO_LONG else null
}
