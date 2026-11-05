package com.example.opsc

import com.example.opsc.ui.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for the saved light/dark preference logic. */
class ThemeModeTest {
    @Test fun darkIsTheDefault() = assertEquals(ThemeMode.DARK, ThemeMode.DEFAULT)
    @Test fun missingValueGivesDark() = assertEquals(ThemeMode.DARK, ThemeMode.fromPref(null))
    @Test fun unknownValueGivesDark() = assertEquals(ThemeMode.DARK, ThemeMode.fromPref("purple"))
    @Test fun savedValuesRoundTrip() {
        ThemeMode.entries.forEach { assertEquals(it, ThemeMode.fromPref(it.prefValue)) }
    }
}
