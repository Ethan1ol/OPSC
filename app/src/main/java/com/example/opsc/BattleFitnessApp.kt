package com.example.opsc

import android.app.Application
import com.example.opsc.ui.ThemeManager

/** Application class: applies the saved light/dark mode (dark by default) before any screen opens. */
class BattleFitnessApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemeManager.apply(this)
    }
}
