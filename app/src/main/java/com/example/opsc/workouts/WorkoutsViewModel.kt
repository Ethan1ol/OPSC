package com.example.opsc.workouts

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.opsc.profile.ProfileRepository
import com.example.opsc.ui.ConnectivityMonitor
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/** Everything the Workouts screen and dashboard show. */
data class WorkoutsUiState(
    /** False until the database first answers (shows a spinner). */
    val loaded: Boolean = false,
    val items: List<Workout> = emptyList(),
    val stats: DashboardStats = WorkoutStats.compute(emptyList(), System.currentTimeMillis(), WorkoutStats.DEFAULT_GOAL),
    /** Workouts the server has not confirmed yet. */
    val pendingCount: Int = 0
)

/** One-off messages for the screens. */
sealed interface WorkoutEvent {
    /** A sync finished. pushed changes were sent. */
    data class SyncFinished(val pushed: Int) : WorkoutEvent
    /** Sync now was pressed offline. It runs when the phone is online. */
    data object SyncQueuedOffline : WorkoutEvent
    data class Saved(val offline: Boolean) : WorkoutEvent
}

/** Remembers the monthly goal on the phone. */
object GoalPrefs {
    private const val PREFS = "battle_fitness_workouts"
    private const val KEY_GOAL = "monthly_goal"

    fun get(context: Context): Int =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_GOAL, WorkoutStats.DEFAULT_GOAL).coerceIn(WorkoutStats.GOAL_RANGE)

    fun set(context: Context, goal: Int) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_GOAL, goal.coerceIn(WorkoutStats.GOAL_RANGE)).apply()
    }
}

/** Shared by the Workouts screen, dashboard and log sheet. Turns the Room database into screen state and runs saves, deletes and syncs. */
class WorkoutsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = WorkoutRepository.get(application)
    private val connectivity = ConnectivityMonitor(application)
    private val uid: String? = FirebaseAuth.getInstance().currentUser?.uid

    /** Workouts swiped away but still in the Undo time: hidden, not deleted yet. */
    private val hiddenIds = MutableStateFlow<Set<String>>(emptySet())
    private val goal = MutableStateFlow(GoalPrefs.get(application))

    val state: StateFlow<WorkoutsUiState> = if (uid == null) {
        flowOf(WorkoutsUiState(loaded = true))
    } else {
        combine(repository.observe(uid), hiddenIds, goal, repository.observePendingCount(uid)) { workouts, hidden, goalValue, pending ->
            val visible = workouts.filter { it.id !in hidden }
            WorkoutsUiState(
                loaded = true,
                items = visible,
                stats = WorkoutStats.compute(
                    visible.map { WorkoutPoint(it.timestamp, it.sets, it.reps) }, System.currentTimeMillis(), goalValue
                ),
                pendingCount = pending
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutsUiState())

    /** Exercise names logged before, newest first. */
    val recentExercises: StateFlow<List<String>> =
        (if (uid == null) flowOf(emptyList()) else repository.observeRecentExercises(uid))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _events = Channel<WorkoutEvent>(Channel.BUFFERED)
    val events: Flow<WorkoutEvent> = _events.receiveAsFlow()

    init {
        // Start-up sync: sends leftovers and pulls workouts from other devices.
        SyncScheduler.enqueue(application)
        watchSyncRuns()
    }

    /** Saves a workout on the phone at once. Sync runs in the background. */
    fun add(input: NewWorkout) {
        val owner = uid ?: return
        viewModelScope.launch {
            repository.add(owner, input)
            _events.send(WorkoutEvent.Saved(offline = !connectivity.isOnlineNow()))
        }
    }

    /** Hides a swiped workout. Call commitDelete when Undo runs out, or undoHide to bring it back. */
    fun hide(id: String) {
        hiddenIds.value = hiddenIds.value + id
    }

    fun undoHide(id: String) {
        hiddenIds.value = hiddenIds.value - id
    }

    /** Deletes the workout (on the phone now, on the server when online). */
    fun commitDelete(id: String) {
        viewModelScope.launch {
            repository.delete(id)
            hiddenIds.value = hiddenIds.value - id
        }
    }

    fun setGoal(value: Int) {
        val clean = value.coerceIn(WorkoutStats.GOAL_RANGE)
        GoalPrefs.set(getApplication(), clean)
        goal.value = clean
        uid?.let { ProfileRepository().save(it, mapOf("monthlyGoal" to clean)) }
    }

    /** The Sync now action. */
    fun syncNow() {
        SyncScheduler.enqueue(getApplication())
        if (!connectivity.isOnlineNow()) _events.trySend(WorkoutEvent.SyncQueuedOffline)
    }

    /** Watches the sync job and reports when a run we saw start has finished. Runs that finished earlier are ignored. */
    private fun watchSyncRuns() {
        viewModelScope.launch {
            val seenActive = mutableSetOf<UUID>()
            var lastReported: UUID? = null
            WorkManager.getInstance(getApplication()).getWorkInfosForUniqueWorkFlow(SyncScheduler.WORK_NAME).collect { infos ->
                val info = infos.lastOrNull() ?: return@collect
                if (!info.state.isFinished) seenActive += info.id
                if (info.state == WorkInfo.State.SUCCEEDED && info.id in seenActive && info.id != lastReported) {
                    lastReported = info.id
                    val pushed = info.outputData.getInt(SyncWorker.KEY_PUSHED, 0)
                    Log.d(TAG, "Sync run finished, pushed $pushed")
                    _events.trySend(WorkoutEvent.SyncFinished(pushed))
                }
            }
        }
    }

    private companion object {
        const val TAG = "WorkoutsViewModel"
    }
}
