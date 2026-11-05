package com.example.opsc.exercises

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
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** One exercise from the wger library, stored on the phone so the library works offline. */
@Entity(tableName = "exercises", indices = [Index("categoryId"), Index("name")])
data class ExerciseEntity(
    @PrimaryKey val id: Int,
    val name: String,
    /** The description as HTML (from wger). */
    val description: String,
    val categoryId: Int,
    val categoryName: String,
    val musclesText: String,
    val secondaryMusclesText: String,
    val equipmentText: String
)

/** A category name and id, used for the filter chips. */
data class CategoryRow(val categoryId: Int, val categoryName: String)

@Dao
interface ExerciseDao {
    /**
     * Searches by name (case-insensitive) and optionally by category. [categoryId] 0 means all.
     * [limit] gives the "load more" pagination.
     */
    @Query(
        "SELECT * FROM exercises WHERE (:categoryId = 0 OR categoryId = :categoryId) AND name LIKE '%' || :query || '%' " +
            "ORDER BY name COLLATE NOCASE LIMIT :limit"
    )
    fun search(query: String, categoryId: Int, limit: Int): Flow<List<ExerciseEntity>>

    @Query("SELECT DISTINCT categoryId, categoryName FROM exercises WHERE categoryId != 0 ORDER BY categoryName")
    fun categories(): Flow<List<CategoryRow>>

    @Query("SELECT COUNT(*) FROM exercises")
    fun count(): Flow<Int>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun get(id: Int): ExerciseEntity?

    @Query("SELECT name FROM exercises ORDER BY name COLLATE NOCASE")
    suspend fun allNames(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ExerciseEntity>)

    @Query("DELETE FROM exercises")
    suspend fun clear()

    /** Swaps the whole library in one transaction, so a failed download never leaves half a library. */
    @Transaction
    suspend fun replaceAll(items: List<ExerciseEntity>) {
        clear()
        insertAll(items)
    }
}

/**
 * A separate database for the exercise library. It is only a cache of public data that can be
 * downloaded again, so if its structure ever changes it is simply rebuilt.
 */
@Database(entities = [ExerciseEntity::class], version = 1, exportSchema = false)
abstract class ExerciseDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao

    companion object {
        @Volatile private var instance: ExerciseDatabase? = null

        fun get(context: Context): ExerciseDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, ExerciseDatabase::class.java, "exercise_cache.db")
                .fallbackToDestructiveMigration(true)
                .build().also { instance = it }
        }
    }
}
