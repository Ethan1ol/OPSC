package com.example.opsc.workouts

import java.util.Calendar
import java.util.TimeZone

/**
 * Where a workout is in the sync process.
 * - [PENDING_UPSERT]: saved on the phone, not yet confirmed on the server (new or changed).
 * - [SYNCED]: the server has this version.
 * - [PENDING_DELETE]: deleted by the user, waiting to be deleted on the server too.
 */
enum class SyncState { PENDING_UPSERT, SYNCED, PENDING_DELETE }

/** What a sync run should do with the workouts that exist only on the server. Pure data, unit tested. */
data class PullPlan(val insertIds: Set<String>, val removeLocalIds: Set<String>)

/**
 * Pure decisions made by the sync job. Kept free of Android and Firebase so they can be unit tested.
 */
object SyncPolicy {
    /** Firestore error codes that may succeed later (network trouble), so the job should retry with backoff. */
    private val RETRYABLE = setOf("UNAVAILABLE", "DEADLINE_EXCEEDED", "ABORTED", "RESOURCE_EXHAUSTED", "INTERNAL", "UNKNOWN", "CANCELLED")

    /** True if a failure with this Firestore code is worth retrying. Permission and argument errors are not. */
    fun isRetryable(firestoreCodeName: String): Boolean = firestoreCodeName in RETRYABLE

    /**
     * A push of a workout may only mark it as synced if the row has not changed while the upload
     * was running (same version). Otherwise it stays pending and the next run uploads the newer edit.
     */
    fun shouldMarkSynced(versionAtUpload: Long, currentVersion: Long): Boolean = versionAtUpload == currentVersion

    /**
     * Decides how to merge the server's workout list into the phone's:
     * insert ones the phone has never seen, and (only if the server list is complete) drop local
     * rows that were already synced but no longer exist on the server (deleted on another device).
     * Rows with pending changes are never touched.
     */
    fun planPull(localStates: Map<String, SyncState>, remoteIds: Set<String>, remoteListComplete: Boolean): PullPlan {
        val insert = remoteIds.filter { it !in localStates }.toSet()
        val remove = if (remoteListComplete) {
            localStates.filter { (id, state) -> state == SyncState.SYNCED && id !in remoteIds }.keys
        } else emptySet()
        return PullPlan(insert, remove)
    }
}

/** The user's input when logging a workout. */
data class NewWorkout(
    val exercise: String,
    val sets: Int,
    val reps: Int,
    val durationMinutes: Int,
    val notes: String
)

/** Fields of the log-workout form. */
enum class WorkoutField { EXERCISE, SETS, REPS, DURATION, NOTES }

/** Every way a field can be invalid. The UI turns each into a translated message. */
enum class WorkoutError {
    EXERCISE_EMPTY, EXERCISE_TOO_LONG, NOT_A_NUMBER,
    SETS_OUT_OF_RANGE, REPS_OUT_OF_RANGE, DURATION_OUT_OF_RANGE, NOTES_TOO_LONG
}

/** Validation for the log-workout form. Pure Kotlin. Numbers arrive as text exactly as typed. */
object WorkoutValidator {
    const val MAX_EXERCISE_LENGTH = 60
    const val MAX_NOTES_LENGTH = 500
    val SETS_RANGE = 1..50
    val REPS_RANGE = 1..500
    val DURATION_RANGE = 1..600

    /** Returns one error per bad field (an empty map means the workout is valid). */
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

/** The bits of a workout needed to work out the dashboard numbers. */
data class WorkoutPoint(val timestamp: Long, val sets: Int, val reps: Int)

/** Numbers shown on the Workouts screen and the dashboard. */
data class DashboardStats(
    val monthCount: Int,
    val streakDays: Int,
    val monthReps: Int,
    val goal: Int,
    /** 0.0 to 1.0, how much of the monthly goal is done. */
    val progress: Float
)

/** Pure calculations for the monthly goal, streak and total reps. */
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

    /**
     * Consecutive days with at least one workout, counting back from today. If there is none yet
     * today the streak is still alive when yesterday had one (the day is not over).
     */
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

    /** The number of whole days since 1970 in the given time zone. */
    fun dayIndex(millis: Long, timeZone: TimeZone): Long = Math.floorDiv(millis + timeZone.getOffset(millis), DAY_MS)

    private fun monthKey(millis: Long, timeZone: TimeZone): Int {
        val calendar = Calendar.getInstance(timeZone).apply { timeInMillis = millis }
        return calendar.get(Calendar.YEAR) * 12 + calendar.get(Calendar.MONTH)
    }
}
