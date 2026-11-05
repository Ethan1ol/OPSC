package com.example.opsc.workouts

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/** Starts the workout sync job. Safe to call often. */
object SyncScheduler {
    /** Name of the sync job. */
    const val WORK_NAME = "workout_sync"

    // Adapted from: Android Developers (2026) Define work requests (WorkManager).
    // Available at: https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work
    /** Queues a sync for when the phone has a connection, even if the app is closed. A running sync makes the new one wait, so no workout is missed. Failed runs retry with backoff. */
    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        Log.d("SyncScheduler", "Sync queued")
    }
}

/** Background job that makes the phone and Firestore agree: 1. send offline deletes, 2. send new and changed workouts, 3. pull workouts from other devices. Safe to run twice, because workouts use a UUID. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            Log.i(TAG, "No signed-in user, nothing to sync")
            return Result.success()
        }
        val repository = WorkoutRepository.get(applicationContext)
        val dao = repository.localDao
        val remote = repository.remote
        var pushed = 0
        return try {
            for (row in dao.pendingDeletes(uid)) {
                if (!remote.delete(uid, row.id)) return retry("delete timed out")
                dao.removeDeleted(row.id)
                pushed++
            }
            for (row in dao.pendingUpserts(uid)) {
                if (!remote.upsert(uid, row)) return retry("upload timed out")
                // Only mark synced if the user did not edit it during the upload.
                dao.markSynced(row.id, row.version)
                pushed++
            }
            pull(uid, repository)
            Log.i(TAG, "Sync finished, pushed $pushed change(s)")
            Result.success(workDataOf(KEY_PUSHED to pushed))
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            if (SyncPolicy.isRetryable(e.code.name)) retry("Firestore ${e.code}") else {
                Log.e(TAG, "Sync failed permanently: ${e.code}", e)
                Result.failure()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed, will retry", e)
            Result.retry()
        }
    }

    /** Brings in workouts from other devices and removes ones deleted elsewhere. */
    private suspend fun pull(uid: String, repository: WorkoutRepository) {
        val dao = repository.localDao
        val (remoteRows, complete) = repository.remote.fetchAll(uid) ?: return
        val local = dao.idsAndStates(uid).associate { it.id to it.syncState }
        val plan = SyncPolicy.planPull(local, remoteRows.map { it.id }.toSet(), complete)
        if (plan.insertIds.isNotEmpty()) dao.insertIfMissing(remoteRows.filter { it.id in plan.insertIds })
        if (plan.removeLocalIds.isNotEmpty()) dao.removeSynced(uid, plan.removeLocalIds)
        Log.d(TAG, "Pull: inserted ${plan.insertIds.size}, removed ${plan.removeLocalIds.size}")
    }

    private fun retry(reason: String): Result {
        Log.w(TAG, "Sync will retry: $reason (attempt $runAttemptCount)")
        return Result.retry()
    }

    companion object {
        private const val TAG = "SyncWorker"

        /** Output key: how many changes were sent. */
        const val KEY_PUSHED = "pushed"
    }
}
