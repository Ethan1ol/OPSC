package com.example.opsc.exercises

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import retrofit2.HttpException
import java.io.IOException

/** Why the download failed. */
enum class RefreshFailure { OFFLINE, SERVER, UNKNOWN }

/** Result of the download. */
sealed interface RefreshResult {
    data class Success(val count: Int) : RefreshResult
    data class Failure(val reason: RefreshFailure) : RefreshResult
}

/** Gives the app the wger library. It is downloaded once into the phone's database, then searching and paging all happen locally, so it works in airplane mode. */
class ExerciseRepository private constructor(context: Context, private val api: WgerApi = WgerApi.create()) {
    private val dao = ExerciseDatabase.get(context).exerciseDao()
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun search(query: String, categoryId: Int, limit: Int): Flow<List<ExerciseEntity>> =
        dao.search(query.trim().replace("%", "").replace("_", ""), categoryId, limit)

    fun categories(): Flow<List<CategoryRow>> = dao.categories()

    fun count(): Flow<Int> = dao.count()

    suspend fun get(id: Int): ExerciseEntity? = dao.get(id)

    /** Every exercise name, for the log-workout suggestions. */
    suspend fun allNames(): List<String> = dao.allNames()

    /** True if never downloaded or older than a week. */
    fun isStale(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val last = prefs.getLong(KEY_LAST_REFRESH, 0L)
        return last == 0L || nowMillis - last > STALE_AFTER_MS
    }

    /** Downloads the English library (about 20 small pages) and replaces the cache. If it fails, the old cache stays. */
    suspend fun refresh(onProgress: (Float) -> Unit = {}): RefreshResult = try {
        onProgress(0f)
        val categories = api.categories().results.orEmpty()
        val muscles = api.muscles().results.orEmpty()
        val equipment = api.equipment().results.orEmpty()
        onProgress(0.05f)

        val exercises = mutableListOf<ExerciseDto>()
        var offset = 0
        var pages = 0
        do {
            val page = api.exercises(offset = offset)
            exercises += page.results.orEmpty()
            onProgress(0.05f + 0.40f * fraction(exercises.size, page.count))
            offset = ExerciseMapper.nextOffset(page.next) ?: break
        } while (++pages < MAX_PAGES)

        val translations = mutableListOf<TranslationDto>()
        offset = 0
        pages = 0
        do {
            val page = api.translations(offset = offset)
            translations += page.results.orEmpty()
            onProgress(0.45f + 0.50f * fraction(translations.size, page.count))
            offset = ExerciseMapper.nextOffset(page.next) ?: break
        } while (++pages < MAX_PAGES)

        val items = ExerciseMapper.merge(exercises, translations, categories, muscles, equipment)
        if (items.isEmpty()) {
            Log.w(TAG, "Download returned no usable exercises; keeping the old cache")
            RefreshResult.Failure(RefreshFailure.UNKNOWN)
        } else {
            dao.replaceAll(items)
            prefs.edit().putLong(KEY_LAST_REFRESH, System.currentTimeMillis()).apply()
            onProgress(1f)
            Log.i(TAG, "Exercise library updated: ${items.size} exercises")
            RefreshResult.Success(items.size)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        Log.w(TAG, "Library download failed (network)", e)
        RefreshResult.Failure(RefreshFailure.OFFLINE)
    } catch (e: HttpException) {
        Log.w(TAG, "Library download failed (HTTP ${e.code()})", e)
        RefreshResult.Failure(RefreshFailure.SERVER)
    } catch (e: Exception) {
        Log.e(TAG, "Library download failed", e)
        RefreshResult.Failure(RefreshFailure.UNKNOWN)
    }

    private fun fraction(done: Int, total: Int?): Float =
        if (total == null || total <= 0) 0.5f else (done.toFloat() / total).coerceIn(0f, 1f)

    companion object {
        private const val TAG = "ExerciseRepository"
        private const val PREFS = "battle_fitness_exercises"
        private const val KEY_LAST_REFRESH = "last_refresh"
        private const val STALE_AFTER_MS = 7L * 24 * 60 * 60 * 1000
        private const val MAX_PAGES = 40

        @Volatile private var instance: ExerciseRepository? = null

        fun get(context: Context): ExerciseRepository = instance ?: synchronized(this) {
            instance ?: ExerciseRepository(context.applicationContext).also { instance = it }
        }
    }
}
