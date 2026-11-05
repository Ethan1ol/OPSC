package com.example.opsc.workouts

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.coroutines.flow.Flow

/**
 * One logged workout in the phone's Room (SQLite) database. The id is a UUID that is also the
 * Firestore document id, which makes uploading it twice harmless (no duplicates).
 */
@Entity(tableName = "workouts", indices = [Index("uid")])
data class WorkoutEntity(
    @PrimaryKey val id: String,
    /** The Firebase user this workout belongs to. */
    val uid: String,
    val exercise: String,
    val sets: Int,
    val reps: Int,
    val durationMinutes: Int,
    val notes: String,
    /** When the workout was done, in milliseconds since 1970. */
    val timestamp: Long,
    /** Changes every time the row changes; the sync job uses it to detect edits made while uploading. */
    val version: Long,
    val syncState: SyncState
)

/** Lets Room store [SyncState] as its name. */
class SyncStateConverter {
    @TypeConverter fun toName(state: SyncState): String = state.name
    @TypeConverter fun fromName(name: String): SyncState = SyncState.valueOf(name)
}

/** Database access for workouts. Every query is limited to one user so accounts on one phone stay separate. */
@Dao
interface WorkoutDao {
    /** Workouts to show: everything except ones the user has deleted (waiting to be deleted on the server). */
    @Query("SELECT * FROM workouts WHERE uid = :uid AND syncState != 'PENDING_DELETE' ORDER BY timestamp DESC")
    fun observeVisible(uid: String): Flow<List<WorkoutEntity>>

    /** How many workouts have changes not yet confirmed by the server. */
    @Query("SELECT COUNT(*) FROM workouts WHERE uid = :uid AND syncState != 'SYNCED'")
    fun observePendingCount(uid: String): Flow<Int>

    /** Exercise names used before, newest first, for the exercise suggestions. */
    @Query("SELECT exercise FROM workouts WHERE uid = :uid AND syncState != 'PENDING_DELETE' GROUP BY exercise ORDER BY MAX(timestamp) DESC LIMIT 30")
    fun observeRecentExercises(uid: String): Flow<List<String>>

    @Query("SELECT * FROM workouts WHERE uid = :uid AND syncState = 'PENDING_UPSERT'")
    suspend fun pendingUpserts(uid: String): List<WorkoutEntity>

    @Query("SELECT * FROM workouts WHERE uid = :uid AND syncState = 'PENDING_DELETE'")
    suspend fun pendingDeletes(uid: String): List<WorkoutEntity>

    @Query("SELECT id, syncState FROM workouts WHERE uid = :uid")
    suspend fun idsAndStates(uid: String): List<IdAndState>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(workout: WorkoutEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfMissing(workouts: List<WorkoutEntity>)

    /** Marks a workout synced, but only if it has not changed since [version] was uploaded. Returns rows changed. */
    @Query("UPDATE workouts SET syncState = 'SYNCED' WHERE id = :id AND version = :version AND syncState = 'PENDING_UPSERT'")
    suspend fun markSynced(id: String, version: Long): Int

    @Query("UPDATE workouts SET syncState = 'PENDING_DELETE', version = :version WHERE id = :id")
    suspend fun markPendingDelete(id: String, version: Long)

    @Query("DELETE FROM workouts WHERE id = :id AND syncState = 'PENDING_DELETE'")
    suspend fun removeDeleted(id: String)

    @Query("DELETE FROM workouts WHERE uid = :uid AND id IN (:ids) AND syncState = 'SYNCED'")
    suspend fun removeSynced(uid: String, ids: Set<String>)

    @Query("DELETE FROM workouts WHERE uid = :uid")
    suspend fun deleteAllForUser(uid: String)
}

/** A row of just the id and sync state, used when merging the server's list. */
data class IdAndState(val id: String, val syncState: SyncState)

/** The phone's Room database. */
@Database(entities = [WorkoutEntity::class], version = 1, exportSchema = false)
@TypeConverters(SyncStateConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        /** The one shared database instance. */
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "battle_fitness.db")
                .build().also { instance = it }
        }
    }
}
