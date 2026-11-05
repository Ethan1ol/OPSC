package com.example.opsc.ui

import android.content.Context
import android.view.View
import androidx.annotation.AttrRes
import androidx.annotation.StringRes
import com.example.opsc.R
import com.google.android.material.color.MaterialColors
import com.google.android.material.R as MaterialR
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

/** Feedback messages (snackbars) for the whole app. */
object UiMessages {

    /** Neutral information, such as Settings saved. */
    fun info(anchor: View, @StringRes message: Int, duration: Int = Snackbar.LENGTH_SHORT): Snackbar =
        show(anchor, anchor.context.getString(message), duration, null, null)

    /** Success in green. */
    fun success(anchor: View, @StringRes message: Int, duration: Int = Snackbar.LENGTH_SHORT): Snackbar =
        show(anchor, anchor.context.getString(message), duration, R.attr.colorSuccess, R.attr.colorOnSuccess)

    /** Error in red. Pass a retry action to add a Retry button. */
    fun error(
        anchor: View,
        @StringRes message: Int,
        duration: Int = Snackbar.LENGTH_LONG,
        onRetry: (() -> Unit)? = null
    ): Snackbar {
        val bar = show(anchor, anchor.context.getString(message), duration, androidx.appcompat.R.attr.colorError, MaterialR.attr.colorOnError)
        if (onRetry != null) bar.setAction(R.string.action_retry) { onRetry() }
        return bar
    }

    /** A message with an Undo button. */
    fun undo(anchor: View, @StringRes message: Int, onUndo: () -> Unit): Snackbar {
        val bar = show(anchor, anchor.context.getString(message), Snackbar.LENGTH_LONG, null, null)
        bar.setAction(R.string.action_undo) { onUndo() }
        return bar
    }

    private fun show(
        anchor: View,
        text: String,
        duration: Int,
        @AttrRes background: Int?,
        @AttrRes foreground: Int?
    ): Snackbar {
        val bar = Snackbar.make(anchor, text, duration)
        if (background != null && foreground != null) {
            bar.setBackgroundTint(MaterialColors.getColor(anchor, background))
            bar.setTextColor(MaterialColors.getColor(anchor, foreground))
            bar.setActionTextColor(MaterialColors.getColor(anchor, foreground))
        }
        bar.show()
        return bar
    }
}

/** A confirmation dialog for risky actions (log out, delete). */
object ConfirmDialog {

    /** Shows a dialog with Cancel and a confirm button that runs onConfirm. */
    fun show(
        context: Context,
        @StringRes title: Int,
        @StringRes message: Int,
        @StringRes confirmLabel: Int = R.string.dialog_confirm,
        onConfirm: () -> Unit
    ) {
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton(R.string.dialog_cancel, null)
            .setPositiveButton(confirmLabel) { _, _ -> onConfirm() }
            .show()
    }
}
