package com.example.opsc.workouts

import android.content.res.ColorStateList
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.opsc.R
import com.google.android.material.color.MaterialColors
import java.util.Date
import java.util.Locale
import com.google.android.material.R as MaterialR

/**
 * Shows the workout list. Each card has the exercise, sets x reps and minutes, optional notes,
 * the date, and a badge saying whether the workout is still waiting to sync or already synced.
 */
class WorkoutAdapter : ListAdapter<Workout, WorkoutAdapter.Holder>(Diff) {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val exercise: TextView = view.findViewById(R.id.workoutExercise)
        private val summary: TextView = view.findViewById(R.id.workoutSummary)
        private val notes: TextView = view.findViewById(R.id.workoutNotes)
        private val date: TextView = view.findViewById(R.id.workoutDate)
        private val badge: TextView = view.findViewById(R.id.workoutBadge)

        fun bind(workout: Workout) {
            val context = itemView.context
            exercise.text = workout.exercise
            summary.text = context.getString(R.string.workout_summary, workout.sets, workout.reps, workout.durationMinutes)
            notes.text = workout.notes
            notes.visibility = if (workout.notes.isBlank()) View.GONE else View.VISIBLE
            val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "d MMM yyyy HH:mm")
            date.text = DateFormat.format(pattern, Date(workout.timestamp))

            val pending = workout.syncState != SyncState.SYNCED
            badge.setText(if (pending) R.string.badge_pending else R.string.badge_synced)
            val backgroundAttr = if (pending) MaterialR.attr.colorSecondaryContainer else R.attr.colorSuccessContainer
            val textAttr = if (pending) MaterialR.attr.colorOnSecondaryContainer else R.attr.colorOnSuccessContainer
            badge.backgroundTintList = ColorStateList.valueOf(MaterialColors.getColor(itemView, backgroundAttr))
            badge.setTextColor(MaterialColors.getColor(itemView, textAttr))
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_workout, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    private object Diff : DiffUtil.ItemCallback<Workout>() {
        override fun areItemsTheSame(a: Workout, b: Workout) = a.id == b.id
        override fun areContentsTheSame(a: Workout, b: Workout) = a == b
    }
}
