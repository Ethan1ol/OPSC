package com.example.opsc.workouts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/** Unit tests for the sync decisions, workout validation and dashboard statistics. */
class WorkoutLogicTest {
    private val utc = TimeZone.getTimeZone("UTC")

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12): Long =
        Calendar.getInstance(utc).apply { clear(); set(year, month - 1, day, hour, 0, 0) }.timeInMillis

    private fun point(timestamp: Long, sets: Int = 3, reps: Int = 10) = WorkoutPoint(timestamp, sets, reps)

    // ---------- sync policy ----------

    @Test fun networkErrorsAreRetried() {
        assertTrue(SyncPolicy.isRetryable("UNAVAILABLE"))
        assertTrue(SyncPolicy.isRetryable("DEADLINE_EXCEEDED"))
    }

    @Test fun permissionErrorsAreNotRetried() {
        assertFalse(SyncPolicy.isRetryable("PERMISSION_DENIED"))
        assertFalse(SyncPolicy.isRetryable("INVALID_ARGUMENT"))
        assertFalse(SyncPolicy.isRetryable("UNAUTHENTICATED"))
    }

    @Test fun markSyncedOnlyIfTheRowDidNotChangeDuringUpload() {
        assertTrue(SyncPolicy.shouldMarkSynced(versionAtUpload = 5, currentVersion = 5))
        assertFalse(SyncPolicy.shouldMarkSynced(versionAtUpload = 5, currentVersion = 6))
    }

    @Test fun pullInsertsWorkoutsFromOtherDevices() {
        val plan = SyncPolicy.planPull(mapOf("a" to SyncState.SYNCED), setOf("a", "b"), remoteListComplete = true)
        assertEquals(setOf("b"), plan.insertIds)
        assertTrue(plan.removeLocalIds.isEmpty())
    }

    @Test fun pullRemovesSyncedRowsDeletedElsewhere() {
        val plan = SyncPolicy.planPull(mapOf("a" to SyncState.SYNCED, "b" to SyncState.SYNCED), setOf("a"), remoteListComplete = true)
        assertEquals(setOf("b"), plan.removeLocalIds)
    }

    @Test fun pullNeverTouchesPendingRows() {
        val local = mapOf("new" to SyncState.PENDING_UPSERT, "gone" to SyncState.PENDING_DELETE)
        val plan = SyncPolicy.planPull(local, remoteIds = emptySet(), remoteListComplete = true)
        assertTrue(plan.insertIds.isEmpty())
        assertTrue(plan.removeLocalIds.isEmpty())
        // a workout the user deleted offline must not be re-added from the server
        val again = SyncPolicy.planPull(local, remoteIds = setOf("gone"), remoteListComplete = true)
        assertTrue(again.insertIds.isEmpty())
    }

    @Test fun incompleteServerListNeverRemovesLocalRows() {
        val plan = SyncPolicy.planPull(mapOf("a" to SyncState.SYNCED), emptySet(), remoteListComplete = false)
        assertTrue(plan.removeLocalIds.isEmpty())
    }

    // ---------- validation ----------

    @Test fun validWorkoutHasNoErrors() {
        assertTrue(WorkoutValidator.validate("Squat", "3", "10", "30", "felt good").isEmpty())
    }

    @Test fun blankExerciseIsRejected() {
        assertEquals(WorkoutError.EXERCISE_EMPTY, WorkoutValidator.validate("  ", "3", "10", "30", "")[WorkoutField.EXERCISE])
    }

    @Test fun longExerciseIsRejected() {
        val errors = WorkoutValidator.validate("x".repeat(61), "3", "10", "30", "")
        assertEquals(WorkoutError.EXERCISE_TOO_LONG, errors[WorkoutField.EXERCISE])
    }

    @Test fun numbersMustBeNumbers() {
        val errors = WorkoutValidator.validate("Squat", "", "abc", "1.5", "")
        assertEquals(WorkoutError.NOT_A_NUMBER, errors[WorkoutField.SETS])
        assertEquals(WorkoutError.NOT_A_NUMBER, errors[WorkoutField.REPS])
        assertEquals(WorkoutError.NOT_A_NUMBER, errors[WorkoutField.DURATION])
    }

    @Test fun numbersMustBeInRange() {
        val errors = WorkoutValidator.validate("Squat", "0", "501", "601", "")
        assertEquals(WorkoutError.SETS_OUT_OF_RANGE, errors[WorkoutField.SETS])
        assertEquals(WorkoutError.REPS_OUT_OF_RANGE, errors[WorkoutField.REPS])
        assertEquals(WorkoutError.DURATION_OUT_OF_RANGE, errors[WorkoutField.DURATION])
    }

    @Test fun rangeBoundariesAreAccepted() {
        assertTrue(WorkoutValidator.validate("Squat", "1", "1", "1", "").isEmpty())
        assertTrue(WorkoutValidator.validate("Squat", "50", "500", "600", "").isEmpty())
    }

    @Test fun longNotesAreRejected() {
        assertEquals(WorkoutError.NOTES_TOO_LONG, WorkoutValidator.validate("Squat", "3", "10", "30", "n".repeat(501))[WorkoutField.NOTES])
    }

    // ---------- statistics ----------

    @Test fun monthCountOnlyCountsThisMonth() {
        val now = at(2026, 11, 15)
        val workouts = listOf(point(at(2026, 11, 1)), point(at(2026, 11, 14)), point(at(2026, 10, 31)), point(at(2025, 11, 15)))
        assertEquals(2, WorkoutStats.compute(workouts, now, 12, utc).monthCount)
    }

    @Test fun totalRepsIsSetsTimesReps() {
        val now = at(2026, 11, 15)
        val stats = WorkoutStats.compute(listOf(point(now, sets = 3, reps = 10), point(now, sets = 4, reps = 8)), now, 12, utc)
        assertEquals(62, stats.monthReps)
    }

    @Test fun progressIsCappedAtOne() {
        val now = at(2026, 11, 15)
        val many = (1..15).map { point(at(2026, 11, it)) }
        assertEquals(1f, WorkoutStats.compute(many, now, 12, utc).progress, 0.0001f)
    }

    @Test fun progressIsAFractionOfTheGoal() {
        val now = at(2026, 11, 15)
        val six = (1..6).map { point(at(2026, 11, it)) }
        assertEquals(0.5f, WorkoutStats.compute(six, now, 12, utc).progress, 0.0001f)
    }

    @Test fun goalIsKeptInRange() {
        val now = at(2026, 11, 15)
        assertEquals(1, WorkoutStats.compute(emptyList(), now, 0, utc).goal)
        assertEquals(100, WorkoutStats.compute(emptyList(), now, 5000, utc).goal)
    }

    @Test fun streakCountsConsecutiveDaysIncludingToday() {
        val now = at(2026, 11, 15)
        val days = listOf(15, 14, 13).map { point(at(2026, 11, it)) }
        assertEquals(3, WorkoutStats.streak(days, now, utc))
    }

    @Test fun streakSurvivesIfOnlyYesterdayHasAWorkout() {
        val now = at(2026, 11, 15, hour = 8)
        val days = listOf(14, 13).map { point(at(2026, 11, it)) }
        assertEquals(2, WorkoutStats.streak(days, now, utc))
    }

    @Test fun streakBreaksAfterAMissedDay() {
        val now = at(2026, 11, 15)
        val days = listOf(point(at(2026, 11, 13)), point(at(2026, 11, 12)))
        assertEquals(0, WorkoutStats.streak(days, now, utc))
    }

    @Test fun streakStopsAtAGap() {
        val now = at(2026, 11, 15)
        val days = listOf(15, 14, 12, 11).map { point(at(2026, 11, it)) }
        assertEquals(2, WorkoutStats.streak(days, now, utc))
    }

    @Test fun twoWorkoutsOnOneDayCountOnceForTheStreak() {
        val now = at(2026, 11, 15)
        val days = listOf(point(at(2026, 11, 15, 7)), point(at(2026, 11, 15, 18)))
        assertEquals(1, WorkoutStats.streak(days, now, utc))
    }

    @Test fun streakWorksAcrossMonthBoundary() {
        val now = at(2026, 12, 1)
        val days = listOf(point(at(2026, 12, 1)), point(at(2026, 11, 30)), point(at(2026, 11, 29)))
        assertEquals(3, WorkoutStats.streak(days, now, utc))
    }

    @Test fun noWorkoutsMeansNoStreak() = assertEquals(0, WorkoutStats.streak(emptyList(), at(2026, 11, 15), utc))
}
