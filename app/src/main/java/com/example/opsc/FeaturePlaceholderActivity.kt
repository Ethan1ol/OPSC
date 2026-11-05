package com.example.opsc

import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Temporary "Coming soon" page shown by dashboard tiles whose real screen is not built yet.
 * Each tile is switched to its real screen when that feature is completed.
 */
class FeaturePlaceholderActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_feature_placeholder)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        val titleRes = intent.getIntExtra(EXTRA_TITLE_RES, R.string.coming_soon)
        findViewById<TextView>(R.id.textFeatureTitle).setText(titleRes)
        findViewById<TextView>(R.id.buttonBack).setOnClickListener { finish() }
    }

    companion object {
        /** Intent extra holding the string resource id of the feature name. */
        const val EXTRA_TITLE_RES = "TITLE_RES"
    }
}
