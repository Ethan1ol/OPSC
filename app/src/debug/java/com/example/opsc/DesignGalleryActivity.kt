package com.example.opsc

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.opsc.ui.ConfirmDialog
import com.example.opsc.ui.StateLayout
import com.example.opsc.ui.UiMessages
import com.google.android.material.textfield.TextInputLayout

/** Debug-only screen that shows every design component, so the look can be checked on a phone. */
class DesignGalleryActivity : AppCompatActivity() {
    private var inputHasError = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_design_gallery)
        val root = findViewById<View>(R.id.galleryRoot)
        // Keep content clear of the system bars.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.galleryScroll)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        findViewById<View>(R.id.galleryDarkToggle).setOnClickListener {
            val dark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
            AppCompatDelegate.setDefaultNightMode(
                if (dark) AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES
            )
        }

        val input = findViewById<TextInputLayout>(R.id.galleryInputLayout)
        findViewById<View>(R.id.galleryToggleError).setOnClickListener {
            inputHasError = !inputHasError
            input.error = if (inputHasError) getString(R.string.gallery_input_error) else null
        }

        val states = findViewById<StateLayout>(R.id.galleryStateLayout)
        findViewById<View>(R.id.stateBtnContent).setOnClickListener { states.showContent() }
        findViewById<View>(R.id.stateBtnLoading).setOnClickListener { states.showLoading() }
        findViewById<View>(R.id.stateBtnEmpty).setOnClickListener {
            states.showEmpty(R.drawable.ic_state_empty, R.string.gallery_empty_message, R.string.gallery_empty_action) {
                UiMessages.info(root, R.string.gallery_msg_info)
            }
        }
        findViewById<View>(R.id.stateBtnError).setOnClickListener { states.showError { states.showContent() } }
        findViewById<View>(R.id.stateBtnOffline).setOnClickListener { states.showOffline { states.showContent() } }

        findViewById<View>(R.id.snackInfo).setOnClickListener { UiMessages.info(root, R.string.gallery_msg_info) }
        findViewById<View>(R.id.snackSuccess).setOnClickListener { UiMessages.success(root, R.string.gallery_msg_success) }
        findViewById<View>(R.id.snackError).setOnClickListener { UiMessages.error(root, R.string.gallery_msg_error) {} }
        findViewById<View>(R.id.snackUndo).setOnClickListener { UiMessages.undo(root, R.string.gallery_msg_undo) {} }
        findViewById<View>(R.id.galleryDialog).setOnClickListener {
            ConfirmDialog.show(this, R.string.gallery_dialog_title, R.string.gallery_dialog_message) {}
        }
    }
}
