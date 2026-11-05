package com.example.opsc.workouts

import java.util.Calendar
import java.util.TimeZone

/** Where a workout is in syncing: PENDING_UPSERT (new or changed, not confirmed), SYNCED (server has it), PENDING_DELETE (deleted, waiting to delete on the server). */
enum class SyncState { PENDING_UPSERT, SYNCED, PENDING_DELETE }

/** What a sync run does with workouts that exist only on the server. */
data class PullPlan(val insertIds: Set<String>, val removeLocalIds: Set<String>)

/** Decisions made by the sync job, with no Android or Firebase code so they can be unit tested. */
object SyncPolicy {
    /** Firestore errors that may work later (network trouble). */
    private val RETRYABLE = setOf("UNAVAILABLE", "DEADLINE_EXCEEDED", "ABORTED", "RESOURCE_EXHAUSTED", "INTERNAL", "UNKNOWN", "CANCELLED")

    /** True if a failure with this code is worth retrying. */
    fun isRetryable(firestoreCodeName: String): Boolean = firestoreCodeName in RETRYABLE

    /** A workout may only be marked synced if it did not change during the upload. Otherwise the next run uploads the newer edit. */
    fun shouldMarkSynced(versionAtUpload: Long, currentVersion: Long): Boolean = versionAtUpload == currentVersion

    /** Merges the server list into the phone's: insert workouts the phone has not seen, and (only if the server list is complete) drop synced rows deleted elsewhere. Rows with pending changes are never touched. */
    fun planPull(localStates: Map<String, SyncState>, remoteIds: Set<String>, remoteListComplete: Boolean): PullPlan {
        val insert = remoteIds.filter { it !in localStates }.toSet()
        val remove = if (remoteListComplete) {
            localStates.filter { (id, state) -> state == SyncState.SYNCED && id !in remoteIds }.keys
        } else emptySet()
        return PullPlan(insert, remove)
    }
}

/** What the user typed when logging a workout. */
data class NewWorkout(
    val exercise: String,
    val sets: Int,
    val reps: Int,
    val durationMinutes: Int,
    val notes: String
)

/** Fields of the log-workout form. */
enum class WorkoutField { EXERCISE, SETS, REPS, DURATION, NOTES }

/** Every way a field can be invalid. */
enum class WorkoutError {
    EXERCISE_EMPTY, EXERCISE_TOO_LONG, NOT_A_NUMBER,
    SETS_OUT_OF_RANGE, REPS_OUT_OF_RANGE, DURATION_OUT_OF_RANGE, NOTES_TOO_LONG
}

/** Checks the log-workout form. Numbers arrive as typed text. */
object WorkoutValidator {
    const val MAX_EXERCISE_LENGTH = 60
    const val MAX_NOTES_LENGTH = 500
    val SETS_RANGE = 1..50
    val REPS_RANGE = 1..500
    val DURATION_RANGE = 1..600

    /** Returns one error per bad field. An empty map means valid. */
    fun validate(exercise: String, sets: String, reps: String, duration: String, notes: String): Map<WorkoutField, WorkoutError> {
        val errors = linkedMapOf<WorkoutField, WorkoutError>()
        val name = exercise.trim()
        when {
            name.isEmpty() -> errors[WorkoutField.EXERCISE] = WorkoutError.EXERCISE_EMPTY
            name.length > MAX_EXERCISE_LENGTH -> errors[WorkoutField.EXERCISE] = WorkoutError.EXERCISE_TOO_LONG
        }
        checkNumber(sets, SETS_RANGE, WorkoutError.SETS_OUT_OF_RANGE)?.let { errors[WorkoutField.SETS] = it }
        checkNumber(reps, REPS_RANGE, WorkoutError.REPS_OUT_OF_RANGE)?.let { errors[WorkoutField.REPS] = it }
        checkNumber(duration, DURATION_RANGE, WorkoutError.DURATION_OUT_OF_RANGE)?.let { errors[WorkoutField.DURATION] = it }
        if (notes.trim().length > MAX_NOTES_LENGTH) errors[WorkoutField.NOTES] = WorkoutError.NOTES_TOO_LONG
        return errors
    }

    private fun checkNumber(text: String, range: IntRange, outOfRange: WorkoutError): WorkoutError? {
        val value = text.trim().toIntOrNull() ?: return WorkoutError.NOT_A_NUMBER
        return if (value in range) null else outOfRange
    }
}

/** The parts of a workout needed for the dashboard numbers. */
data class WorkoutPoint(val timestamp: Long, val sets: Int, val reps: Int)

/** Numbers on the Workouts screen and dashboard. */
data class DashboardStats(
    val monthCount: Int,
    val streakDays: Int,
    val monthReps: Int,
    val goal: Int,
    /** 0.0 to 1.0: how much of the monthly goal is done. */
    val progress: Float
)

/** Calculations for the monthly goal, streak and total reps. */
object WorkoutStats {
    const val DEFAULT_GOAL = 12
    val GOAL_RANGE = 1..100
    private const val DAY_MS = 86_400_000L

    fun compute(workouts: List<WorkoutPoint>, nowMillis: Long, goal: Int, timeZone: TimeZone = TimeZone.getDefault()): DashboardStats {
        val safeGoal = goal.coerceIn(GOAL_RANGE)
        val thisMonth = monthKey(nowMillis, timeZone)
        val inMonth = workouts.filter { monthKey(it.timestamp, timeZone) == thisMonth }
        return DashboardStats(
            monthCount = inMonth.size,
            streakDays = streak(workouts, nowMillis, timeZone),
            monthReps = inMonth.sumOf { it.sets.toLong() * it.reps }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            goal = safeGoal,
            progress = (inMonth.size.toFloat() / safeGoal).coerceIn(0f, 1f)
        )
    }

    /** Consecutive days with a workout, counting back from today. If today has none yet, the streak lives on if yesterday had one. */
    fun streak(workouts: List<WorkoutPoint>, nowMillis: Long, timeZone: TimeZone = TimeZone.getDefault()): Int {
        val days = workouts.map { dayIndex(it.timestamp, timeZone) }.toSet()
        var day = dayIndex(nowMillis, timeZone)
        if (day !in days) day -= 1
        var count = 0
        while (day in days) {
            count++
            day -= 1
        }
        return count
    }

    /** The number of whole days since 1970 in the time zone. */
    fun dayIndex(millis: Long, timeZone: TimeZone): Long = Math.floorDiv(millis + timeZone.getOffset(millis), DAY_MS)

    private fun monthKey(millis: Long, timeZone: TimeZone): Int {
        val calendar = Calendar.getInstance(timeZone).apply { timeInMillis = millis }
        return calendar.get(Calendar.YEAR) * 12 + calendar.get(Calendar.MONTH)
    }
}
