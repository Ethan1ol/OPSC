package com.example.opsc.clubs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Unit tests for the league rules, standings, invite codes and club validation. */
class LeagueEngineTest {
    private fun members(n: Int) = (1..n).map { "m$it" }

    private fun match(a: String, b: String, s1: Int?, s2: Int?, verified: Boolean = true) = MatchResult(a, b, s1, s2, verified)


    @Test fun needsAtLeastThreeMembersToStart() {
        assertFalse(LeagueEngine.canStart(2))
        assertTrue(LeagueEngine.canStart(3))
        assertTrue(LeagueEngine.canStart(12))
        assertFalse(LeagueEngine.canStart(13))
    }

    @Test fun twoMembersCannotGenerateFixtures() {
        var failed = false
        try { LeagueEngine.generateFixtures(members(2)) } catch (e: IllegalArgumentException) { failed = true }
        assertTrue(failed)
    }


    @Test fun fixtureCountIsThreeTimesEveryPair() {
        for (n in 3..12) assertEquals(LeagueEngine.fixtureCount(n), LeagueEngine.generateFixtures(members(n)).size)
        assertEquals(9, LeagueEngine.fixtureCount(3))
        assertEquals(18, LeagueEngine.fixtureCount(4))
    }

    @Test fun everyPairPlaysExactlyThreeTimesOncePerRound() {
        val fixtures = LeagueEngine.generateFixtures(members(5))
        val pairs = fixtures.groupBy { setOf(it.player1Id, it.player2Id) }
        assertEquals(10, pairs.size)
        pairs.values.forEach { games ->
            assertEquals(3, games.size)
            assertEquals(setOf(1, 2, 3), games.map { it.round }.toSet())
        }
    }

    @Test fun roundsAreOneToThree() {
        assertEquals(setOf(1, 2, 3), LeagueEngine.generateFixtures(members(4)).map { it.round }.toSet())
    }

    @Test fun refereeIsNeverAPlayerInTheMatch() {
        for (n in 3..12) {
            LeagueEngine.generateFixtures(members(n)).forEach {
                assertTrue(it.refereeId != it.player1Id && it.refereeId != it.player2Id)
                assertTrue(it.player1Id != it.player2Id)
            }
        }
    }

    @Test fun refereeIsAMember() {
        val ids = members(6).toSet()
        LeagueEngine.generateFixtures(members(6)).forEach { assertTrue(it.refereeId in ids) }
    }

    @Test fun refereeingIsSpreadEvenly() {
        for (n in 3..12) {
            val counts = LeagueEngine.generateFixtures(members(n)).groupingBy { it.refereeId }.eachCount()
            assertEquals(n, counts.size)
            assertTrue("n=$n spread ${counts.values.max() - counts.values.min()}", counts.values.max() - counts.values.min() <= 1)
        }
    }

    @Test fun homeAndAwaySwapBetweenRounds() {
        val fixtures = LeagueEngine.generateFixtures(members(3))
        val one = fixtures.first { it.round == 1 && setOf(it.player1Id, it.player2Id) == setOf("m1", "m2") }
        val two = fixtures.first { it.round == 2 && setOf(it.player1Id, it.player2Id) == setOf("m1", "m2") }
        assertEquals(one.player1Id, two.player2Id)
    }

    @Test fun duplicateMembersAreIgnored() {
        assertEquals(9, LeagueEngine.generateFixtures(listOf("a", "b", "c", "a", "b")).size)
    }


    @Test fun scoresMustBeWholeNumbersInRange() {
        assertTrue(LeagueEngine.isValidScore(0))
        assertTrue(LeagueEngine.isValidScore(999))
        assertFalse(LeagueEngine.isValidScore(-1))
        assertFalse(LeagueEngine.isValidScore(1000))
        assertFalse(LeagueEngine.isValidScore(null))
    }

    @Test fun outcomeAndWinner() {
        assertEquals(Outcome.PLAYER1_WIN, LeagueEngine.outcome(3, 1))
        assertEquals(Outcome.PLAYER2_WIN, LeagueEngine.outcome(0, 2))
        assertEquals(Outcome.DRAW, LeagueEngine.outcome(2, 2))
        assertEquals("a", LeagueEngine.winnerId("a", "b", 3, 1))
        assertEquals("b", LeagueEngine.winnerId("a", "b", 0, 2))
        assertNull(LeagueEngine.winnerId("a", "b", 2, 2))
    }


    @Test fun winGivesThreeAndLossGivesZero() {
        val table = LeagueEngine.computeStandings(listOf("a", "b", "c"), listOf(match("a", "b", 2, 0)))
        val a = table.first { it.uid == "a" }
        val b = table.first { it.uid == "b" }
        assertEquals(3, a.points)
        assertEquals(1, a.won)
        assertEquals(0, b.points)
        assertEquals(1, b.lost)
    }

    @Test fun drawGivesOnePointEach() {
        val table = LeagueEngine.computeStandings(listOf("a", "b", "c"), listOf(match("a", "b", 1, 1)))
        assertEquals(1, table.first { it.uid == "a" }.points)
        assertEquals(1, table.first { it.uid == "b" }.points)
        assertEquals(1, table.first { it.uid == "a" }.drawn)
    }

    @Test fun unverifiedMatchesDoNotCount() {
        val table = LeagueEngine.computeStandings(listOf("a", "b", "c"), listOf(match("a", "b", 5, 0, verified = false), match("a", "c", null, null)))
        assertTrue(table.all { it.points == 0 && it.played == 0 })
    }

    @Test fun everyMemberAppearsEvenWithNoMatches() {
        val table = LeagueEngine.computeStandings(listOf("a", "b", "c"), emptyList())
        assertEquals(3, table.size)
    }

    @Test fun tableIsSortedByPointsThenWins() {
        val results = listOf(
            match("a", "b", 1, 1),      // a 1, b 1
            match("a", "c", 0, 3),      // c 3
            match("b", "c", 2, 0)       // b 4 total
        )
        val table = LeagueEngine.computeStandings(listOf("a", "b", "c"), results)
        assertEquals(listOf("b", "c", "a"), table.map { it.uid })
        assertEquals(listOf(4, 3, 1), table.map { it.points })
    }

    @Test fun matchesWithUnknownMembersAreIgnored() {
        val table = LeagueEngine.computeStandings(listOf("a", "b", "c"), listOf(match("a", "ghost", 1, 0)))
        assertTrue(table.all { it.played == 0 })
    }

    @Test fun playedEqualsWonDrawnLost() {
        val table = LeagueEngine.computeStandings(listOf("a", "b", "c"), listOf(match("a", "b", 1, 0), match("a", "c", 2, 2), match("b", "c", 0, 1)))
        table.forEach { assertEquals(it.played, it.won + it.drawn + it.lost) }
    }

    @Test fun tiedRowsShareARank() {
        val table = listOf(
            StandingRow("a", 2, 1, 0, 1, 3), StandingRow("b", 2, 1, 0, 1, 3), StandingRow("c", 2, 0, 0, 2, 0)
        )
        assertEquals(listOf(1, 1, 3), LeagueEngine.ranks(table))
    }


    @Test fun leagueIsCompleteOnlyWhenEveryFixtureIsVerified() {
        assertFalse(LeagueEngine.isComplete(0, 0))
        assertFalse(LeagueEngine.isComplete(9, 8))
        assertTrue(LeagueEngine.isComplete(9, 9))
    }

    @Test fun championIsTheTopRow() {
        val table = listOf(StandingRow("a", 2, 2, 0, 0, 6), StandingRow("b", 2, 1, 0, 1, 3))
        assertEquals(listOf("a"), LeagueEngine.champions(table).map { it.uid })
    }

    @Test fun sharedFirstPlaceGivesTwoChampions() {
        val table = listOf(StandingRow("a", 2, 1, 0, 1, 3), StandingRow("b", 2, 1, 0, 1, 3), StandingRow("c", 2, 0, 0, 2, 0))
        assertEquals(setOf("a", "b"), LeagueEngine.champions(table).map { it.uid }.toSet())
    }

    @Test fun fullLeagueEndsWithEveryoneHavingPlayedTheRightNumberOfMatches() {
        val ids = members(4)
        val fixtures = LeagueEngine.generateFixtures(ids)
        val results = fixtures.map { match(it.player1Id, it.player2Id, 1, 0) }
        val table = LeagueEngine.computeStandings(ids, results)
        table.forEach { assertEquals(9, it.played) } // 3 opponents x 3 rounds
        assertEquals(fixtures.size * LeagueEngine.POINTS_WIN, table.sumOf { it.points })
    }


    @Test fun inviteCodeHasSixSafeCharacters() {
        repeat(200) {
            val code = InviteCode.generate(Random(it))
            assertEquals(6, code.length)
            assertTrue(InviteCode.isValid(code))
            assertTrue(code.none { c -> c in "0O1IL" })
        }
    }

    @Test fun inviteCodesAreNotAllTheSame() {
        assertTrue((1..50).map { InviteCode.generate(Random(it)) }.toSet().size > 40)
    }

    @Test fun normaliseCleansUserInput() {
        assertEquals("ABC234", InviteCode.normalise(" abc-234 "))
        assertEquals("ABCDEF", InviteCode.normalise("abc def"))
    }

    @Test fun invalidCodesAreRejected() {
        assertFalse(InviteCode.isValid("ABC12"))
        assertFalse(InviteCode.isValid("ABCDE0"))
        assertFalse(InviteCode.isValid("abcdef"))
    }


    @Test fun clubNameLimits() {
        assertEquals(ClubError.NAME_TOO_SHORT, ClubValidator.validateName("ab"))
        assertEquals(ClubError.NAME_TOO_SHORT, ClubValidator.validateName("   "))
        assertNull(ClubValidator.validateName("Sprinters"))
        assertEquals(ClubError.NAME_TOO_LONG, ClubValidator.validateName("x".repeat(41)))
    }

    @Test fun descriptionLimit() {
        assertNull(ClubValidator.validateDescription(""))
        assertNull(ClubValidator.validateDescription("x".repeat(200)))
        assertEquals(ClubError.DESCRIPTION_TOO_LONG, ClubValidator.validateDescription("x".repeat(201)))
    }
}
