package com.example.opsc.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Unit tests for [Validators]; they run on the JVM with no Android or Firebase needed. */
class ValidatorsTest {
    private val today = SimpleDate(2026, 11, 5)

    @Test fun email_valid() = assertNull(Validators.validateEmail("john.smith+fit@mail.co.za"))
    @Test fun email_trimsSpaces() = assertNull(Validators.validateEmail("  a@b.com  "))
    @Test fun email_empty() = assertEquals(FieldError.EMAIL_EMPTY, Validators.validateEmail("   "))
    @Test fun email_missingAt() = assertEquals(FieldError.EMAIL_INVALID, Validators.validateEmail("john.mail.com"))
    @Test fun email_missingDomain() = assertEquals(FieldError.EMAIL_INVALID, Validators.validateEmail("john@"))
    @Test fun email_noTld() = assertEquals(FieldError.EMAIL_INVALID, Validators.validateEmail("john@mail"))

    @Test fun loginPassword_empty() = assertEquals(FieldError.PASSWORD_EMPTY, Validators.validateLoginPassword(""))
    @Test fun loginPassword_any() = assertNull(Validators.validateLoginPassword("x"))

    @Test fun newPassword_empty() = assertEquals(FieldError.PASSWORD_EMPTY, Validators.validateNewPassword(""))
    @Test fun newPassword_fiveChars() = assertEquals(FieldError.PASSWORD_TOO_SHORT, Validators.validateNewPassword("12345"))
    @Test fun newPassword_sixChars() = assertNull(Validators.validateNewPassword("123456"))
    @Test fun confirm_mismatch() = assertEquals(FieldError.PASSWORD_MISMATCH, Validators.validateConfirmPassword("abcdef", "abcdeg"))
    @Test fun confirm_match() = assertNull(Validators.validateConfirmPassword("abcdef", "abcdef"))

    @Test fun name_valid() = assertNull(Validators.validateName("Thandi"))
    @Test fun name_hyphenAndApostrophe() = assertNull(Validators.validateName("Mary-Ann O'Neil"))
    @Test fun name_accented() = assertNull(Validators.validateName("Zoë"))
    @Test fun name_empty() = assertEquals(FieldError.NAME_EMPTY, Validators.validateName(" "))
    @Test fun name_digits() = assertEquals(FieldError.NAME_INVALID, Validators.validateName("R2D2"))
    @Test fun name_oneLetter() = assertEquals(FieldError.NAME_INVALID, Validators.validateName("A"))
    @Test fun surname_empty() = assertEquals(FieldError.SURNAME_EMPTY, Validators.validateSurname(""))
    @Test fun surname_valid() = assertNull(Validators.validateSurname("Behardien"))

    @Test fun username_valid() = assertNull(Validators.validateUsername("ethan_c1"))
    @Test fun username_empty() = assertEquals(FieldError.USERNAME_EMPTY, Validators.validateUsername(""))
    @Test fun username_short() = assertEquals(FieldError.USERNAME_TOO_SHORT, Validators.validateUsername("ab"))
    @Test fun username_long() = assertEquals(FieldError.USERNAME_TOO_LONG, Validators.validateUsername("a".repeat(21)))
    @Test fun username_badChars() = assertEquals(FieldError.USERNAME_BAD_CHARS, Validators.validateUsername("bad name!"))

    @Test fun birth_empty() = assertEquals(FieldError.BIRTH_EMPTY, Validators.validateBirthDate("", today))
    @Test fun birth_garbage() = assertEquals(FieldError.BIRTH_INVALID, Validators.validateBirthDate("hello", today))
    @Test fun birth_impossibleDay() = assertEquals(FieldError.BIRTH_INVALID, Validators.validateBirthDate("2001-02-30", today))
    @Test fun birth_leapDayValid() = assertNull(Validators.validateBirthDate("2004-02-29", today))
    @Test fun birth_notLeapYear() = assertEquals(FieldError.BIRTH_INVALID, Validators.validateBirthDate("2003-02-29", today))
    @Test fun birth_future() = assertEquals(FieldError.BIRTH_IN_FUTURE, Validators.validateBirthDate("2027-01-01", today))
    @Test fun birth_tooYoung() = assertEquals(FieldError.BIRTH_TOO_YOUNG, Validators.validateBirthDate("2015-01-01", today))
    @Test fun birth_exactlyThirteenToday() = assertNull(Validators.validateBirthDate("2013-11-05", today))
    @Test fun birth_dayBeforeThirteen() = assertEquals(FieldError.BIRTH_TOO_YOUNG, Validators.validateBirthDate("2013-11-06", today))
    @Test fun birth_adult() = assertNull(Validators.validateBirthDate("2000-06-15", today))

    @Test fun registration_allValid() {
        val errors = Validators.validateRegistration(
            "a@b.com", "Ethan", "Carr", "2000-01-01", "ethan_c", "secret1", "secret1", today
        )
        assertEquals(emptyMap<FormField, FieldError>(), errors)
    }

    @Test fun registration_reportsEveryBadField() {
        val errors = Validators.validateRegistration("x", "", "", "", "a", "1", "2", today)
        assertEquals(FieldError.EMAIL_INVALID, errors[FormField.EMAIL])
        assertEquals(FieldError.NAME_EMPTY, errors[FormField.NAME])
        assertEquals(FieldError.SURNAME_EMPTY, errors[FormField.SURNAME])
        assertEquals(FieldError.BIRTH_EMPTY, errors[FormField.BIRTH_DATE])
        assertEquals(FieldError.USERNAME_TOO_SHORT, errors[FormField.USERNAME])
        assertEquals(FieldError.PASSWORD_TOO_SHORT, errors[FormField.PASSWORD])
        assertNull(errors[FormField.CONFIRM_PASSWORD]) // not reported while the password itself is bad
    }

    @Test fun registration_mismatchReported() {
        val errors = Validators.validateRegistration(
            "a@b.com", "Ethan", "Carr", "2000-01-01", "ethan_c", "secret1", "secret2", today
        )
        assertEquals(FieldError.PASSWORD_MISMATCH, errors[FormField.CONFIRM_PASSWORD])
    }

    @Test fun usernameFromEmail_sanitises() = assertEquals("john_smith", Validators.usernameFromEmail("john.smith@x.com"))
    @Test fun usernameFromEmail_padsShort() = assertEquals("a__", Validators.usernameFromEmail("a@x.com"))
    @Test fun usernameFromEmail_truncates() = assertEquals(20, Validators.usernameFromEmail("a".repeat(40) + "@x.com").length)
}
