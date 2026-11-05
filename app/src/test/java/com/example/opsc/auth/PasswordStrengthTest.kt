package com.example.opsc.auth

import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for [PasswordStrengthRater]. */
class PasswordStrengthTest {
    private fun rate(p: String) = PasswordStrengthRater.rate(p)

    @Test fun empty() = assertEquals(PasswordStrength.EMPTY, rate(""))
    @Test fun tooShortIsWeak() = assertEquals(PasswordStrength.WEAK, rate("aB3!"))
    @Test fun commonPasswordIsWeak() {
        assertEquals(PasswordStrength.WEAK, rate("password"))
        assertEquals(PasswordStrength.WEAK, rate("Password1".lowercase()))
        assertEquals(PasswordStrength.WEAK, rate("123456789"))
    }
    @Test fun oneCharacterTypeIsWeakEvenWhenLong() {
        assertEquals(PasswordStrength.WEAK, rate("abcdefghijklmnop"))
        assertEquals(PasswordStrength.WEAK, rate("123456789012345"))
    }
    @Test fun shortMixedIsWeak() = assertEquals(PasswordStrength.WEAK, rate("abc123"))
    @Test fun mediumMixedIsFair() = assertEquals(PasswordStrength.FAIR, rate("abcdefg1"))
    @Test fun upperLowerDigitEightCharsIsFair() = assertEquals(PasswordStrength.FAIR, rate("Abcdefg1"))
    @Test fun longAndVariedIsStrong() = assertEquals(PasswordStrength.STRONG, rate("Abcdefg1!xyz"))
    @Test fun allFourTypesEightCharsIsStrong() = assertEquals(PasswordStrength.STRONG, rate("Abcdef1!"))
    @Test fun unicodeLettersCount() = assertEquals(PasswordStrength.FAIR, rate("Zoëabcd9"))
}
