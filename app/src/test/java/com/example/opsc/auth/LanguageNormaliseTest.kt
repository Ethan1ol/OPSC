package com.example.opsc.auth

import com.example.opsc.profile.LanguageManager
import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for the pure language-code logic in [LanguageManager.normalise]. */
class LanguageNormaliseTest {
    @Test fun supportedCodesKept() {
        assertEquals("af", LanguageManager.normalise("af"))
        assertEquals("zu", LanguageManager.normalise("zu"))
        assertEquals("en", LanguageManager.normalise("en"))
    }

    @Test fun regionAndCaseIgnored() {
        assertEquals("af", LanguageManager.normalise("af-ZA"))
        assertEquals("zu", LanguageManager.normalise("ZU_za"))
    }

    @Test fun unsupportedFallsBackToEnglish() {
        assertEquals("en", LanguageManager.normalise("fr"))
        assertEquals("en", LanguageManager.normalise(""))
        assertEquals("en", LanguageManager.normalise(null))
    }
}
