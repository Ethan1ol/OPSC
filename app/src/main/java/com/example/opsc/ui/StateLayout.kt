package com.example.opsc.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.example.opsc.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator

/**
 * A container that shows its normal child views (the "content") or one of three states:
 * loading spinner, empty message, or error message with a Retry button.
 *
 * Put the screen's normal views inside it in XML, then call [showLoading], [showEmpty],
 * [showError] or [showContent]. Every screen in the app uses this for consistent states.
 */
class StateLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    /** The state currently displayed. */
    enum class State { CONTENT, LOADING, EMPTY, ERROR }

    private val overlay: View = LayoutInflater.from(context).inflate(R.layout.view_state_overlay, this, false)
    private val progress: CircularProgressIndicator = overlay.findViewById(R.id.stateProgress)
    private val messageGroup: View = overlay.findViewById(R.id.stateMessageGroup)
    private val icon: ImageView = overlay.findViewById(R.id.stateIcon)
    private val message: TextView = overlay.findViewById(R.id.stateMessage)
    private val action: MaterialButton = overlay.findViewById(R.id.stateAction)

    /** The state last requested; useful for tests and for restoring a screen. */
    var state: State = State.CONTENT
        private set

    init {
        addView(overlay)
        val styled = context.obtainStyledAttributes(attrs, R.styleable.StateLayout)
        progress.contentDescription =
            styled.getText(R.styleable.StateLayout_stateLoadingDescription) ?: context.getString(R.string.state_loading)
        styled.recycle()
        overlay.visibility = GONE
    }

    /** Shows the normal content views. */
    fun showContent() = apply(State.CONTENT)

    /** Shows a spinner in place of the content. */
    fun showLoading() = apply(State.LOADING)

    /** Shows an icon with a short message, and an optional action button (for example "Create a club"). */
    fun showEmpty(
        @DrawableRes iconRes: Int,
        @StringRes messageRes: Int,
        @StringRes actionRes: Int? = null,
        onAction: (() -> Unit)? = null
    ) {
        bindMessage(iconRes, context.getString(messageRes), actionRes, onAction)
        apply(State.EMPTY)
    }

    /** Shows an error message with a Retry button. */
    fun showError(@StringRes messageRes: Int = R.string.error_generic, onRetry: () -> Unit) {
        bindMessage(R.drawable.ic_state_error, context.getString(messageRes), R.string.action_retry, onRetry)
        apply(State.ERROR)
    }

    /** Shows the "you are offline" error, with Retry. */
    fun showOffline(onRetry: () -> Unit) {
        bindMessage(R.drawable.ic_state_offline, context.getString(R.string.error_offline), R.string.action_retry, onRetry)
        apply(State.ERROR)
    }

    private fun bindMessage(@DrawableRes iconRes: Int, text: String, @StringRes actionRes: Int?, onAction: (() -> Unit)?) {
        icon.setImageResource(iconRes)
        message.text = text
        if (actionRes != null && onAction != null) {
            action.setText(actionRes)
            action.setOnClickListener { onAction() }
            action.visibility = VISIBLE
        } else {
            action.setOnClickListener(null)
            action.visibility = GONE
        }
    }

    private fun apply(newState: State) {
        state = newState
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child !== overlay) child.visibility = if (newState == State.CONTENT) VISIBLE else GONE
        }
        overlay.visibility = if (newState == State.CONTENT) GONE else VISIBLE
        progress.visibility = if (newState == State.LOADING) VISIBLE else GONE
        messageGroup.visibility = if (newState == State.EMPTY || newState == State.ERROR) VISIBLE else GONE
    }
}
