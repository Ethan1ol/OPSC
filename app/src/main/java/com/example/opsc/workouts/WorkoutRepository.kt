package com.example.opsc.workouts

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/** A workout as the screens see it. */
data class Workout(
    val id: String,
    val exercise: String,
    val sets: Int,
    val reps: Int,
    val durationMinutes: Int,
    val notes: String,
    val timestamp: Long,
    val syncState: SyncState
)

/** Talks to Firestore for workouts at users/{uid}/workouts/{workoutId}. */
class WorkoutRemote(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {
    private fun collection(uid: String) = db.collection("users").document(uid).collection("workouts")

    /** Writes the workout under its UUID, so running twice changes nothing. Returns false on timeout. */
    suspend fun upsert(uid: String, e: WorkoutEntity): Boolean = withTimeoutOrNull(TIMEOUT_MS) {
        collection(uid).document(e.id).set(
            mapOf(
                "id" to e.id,
                "exercise" to e.exercise,
                "sets" to e.sets,
                "reps" to e.reps,
                "durationMinutes" to e.durationMinutes,
                "notes" to e.notes,
                "timestamp" to e.timestamp,
                "updatedAt" to e.version
            )
        ).await()
        true
    } ?: false

    /** Deletes the workout. Deleting a missing one is fine. Returns false on timeout. */
    suspend fun delete(uid: String, id: String): Boolean = withTimeoutOrNull(TIMEOUT_MS) {
        collection(uid).document(id).delete().await()
        true
    } ?: false

    /** Reads up to limit workouts. complete is false if there may be more. */
    suspend fun fetchAll(uid: String, limit: Int = FETCH_LIMIT): Pair<List<WorkoutEntity>, Boolean>? =
        withTimeoutOrNull(TIMEOUT_MS) {
            val snapshot = collection(uid).limit(limit.toLong()).get().await()
            val rows = snapshot.documents.mapNotNull { doc ->
                val exercise = doc.getString("exercise") ?: return@mapNotNull null
                WorkoutEntity(
                    id = doc.id,
                    uid = uid,
                    exercise = exercise,
                    sets = (doc.getLong("sets") ?: 1L).toInt(),
                    reps = (doc.getLong("reps") ?: 1L).toInt(),
                    durationMinutes = (doc.getLong("durationMinutes") ?: 0L).toInt(),
                    notes = doc.getString("notes").orEmpty(),
                    timestamp = doc.getLong("timestamp") ?: 0L,
                    version = doc.getLong("updatedAt") ?: 0L,
                    syncState = SyncState.SYNCED
                )
            }
            rows to (snapshot.size() < limit)
        }

    companion object {
        const val TIMEOUT_MS = 30_000L
        const val FETCH_LIMIT = 500
    }
}

/** The one place the app reads and writes workouts. Everything is saved to the phone's database first, then a WorkManager job syncs with Firestore. */
class WorkoutRepository private constructor(private val appContext: Context) {
    private val dao = AppDatabase.get(appContext).workoutDao()
    val remote = WorkoutRemote()
    val localDao: WorkoutDao get() = dao

    /** The user's workouts, newest first, live. Workouts waiting to be deleted are hidden. */
    fun observe(uid: String): Flow<List<Workout>> = dao.observeVisible(uid).map { rows -> rows.map { it.toWorkout() } }

    fun observePendingCount(uid: String): Flow<Int> = dao.observePendingCount(uid)

    fun observeRecentExercises(uid: String): Flow<List<String>> = dao.observeRecentExercises(uid)

    /** Saves a new workout on the phone at once and schedules a sync. */
    suspend fun add(uid: String, input: NewWorkout, nowMillis: Long = System.currentTimeMillis()): String {
        val id = UUID.randomUUID().toString()
        dao.upsert(
            WorkoutEntity(
                id = id, uid = uid, exercise = input.exercise.trim(), sets = input.sets, reps = input.reps,
                durationMinutes = input.durationMinutes, notes = input.notes.trim(),
                timestamp = nowMillis, version = nowMillis, syncState = SyncState.PENDING_UPSERT
            )
        )
        Log.i(TAG, "Saved workout $id locally (pending sync)")
        SyncScheduler.enqueue(appContext)
        return id
    }

    /** Hides the workout now and queues the delete for the server, even when offline. */
    suspend fun delete(id: String) {
        dao.markPendingDelete(id, System.currentTimeMillis())
        Log.i(TAG, "Workout $id deleted locally (delete queued)")
        SyncScheduler.enqueue(appContext)
    }

    /** Removes everything stored on the phone for this user. */
    suspend fun clearLocal(uid: String) = dao.deleteAllForUser(uid)

    private fun WorkoutEntity.toWorkout() = Workout(id, exercise, sets, reps, durationMinutes, notes, timestamp, syncState)

    companion object {
        private const val TAG = "WorkoutRepository"
        @Volatile private var instance: WorkoutRepository? = null

        fun get(context: Context): WorkoutRepository = instance ?: synchronized(this) {
            instance ?: WorkoutRepository(context.applicationContext).also { instance = it }
        }
    }
}
