package com.example.opsc.auth

/** Every way a form field can be invalid. */
enum class FieldError {
    EMAIL_EMPTY, EMAIL_INVALID,
    PASSWORD_EMPTY, PASSWORD_TOO_SHORT, PASSWORD_MISMATCH, PASSWORD_SAME_AS_CURRENT,
    NAME_EMPTY, NAME_INVALID,
    SURNAME_EMPTY, SURNAME_INVALID,
    USERNAME_EMPTY, USERNAME_TOO_SHORT, USERNAME_TOO_LONG, USERNAME_BAD_CHARS,
    BIRTH_EMPTY, BIRTH_INVALID, BIRTH_IN_FUTURE, BIRTH_TOO_YOUNG,
    NUMBER_INVALID, HEIGHT_OUT_OF_RANGE, WEIGHT_OUT_OF_RANGE,
    HEIGHT_OUT_OF_RANGE_IMPERIAL, WEIGHT_OUT_OF_RANGE_IMPERIAL
}

/** The fields on the sign-up form. */
enum class FormField { EMAIL, NAME, SURNAME, BIRTH_DATE, USERNAME, PASSWORD, CONFIRM_PASSWORD }

/** A simple date, so it is easy to unit test. */
data class SimpleDate(val year: Int, val month: Int, val day: Int)

/** Input checks for login and sign-up. Each returns null when valid, otherwise the error to show. No Android code, so it runs as a plain unit test. */
object Validators {
    const val MIN_PASSWORD_LENGTH = 6
    const val MIN_USERNAME_LENGTH = 3
    const val MAX_USERNAME_LENGTH = 20
    const val MIN_AGE_YEARS = 13
    const val MIN_HEIGHT_CM = 50.0
    const val MAX_HEIGHT_CM = 260.0
    const val MIN_WEIGHT_KG = 20.0
    const val MAX_WEIGHT_KG = 400.0

    private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9\\-]+(\\.[A-Za-z0-9\\-]+)*\\.[A-Za-z]{2,}$")
    private val NAME_REGEX = Regex("^\\p{L}[\\p{L} '\\-]{1,29}$")
    private val USERNAME_REGEX = Regex("^[A-Za-z0-9_]+$")

    fun validateEmail(email: String): FieldError? {
        val value = email.trim()
        return when {
            value.isEmpty() -> FieldError.EMAIL_EMPTY
            !EMAIL_REGEX.matches(value) -> FieldError.EMAIL_INVALID
            else -> null
        }
    }

    /** Login only checks that something was typed. */
    fun validateLoginPassword(password: String): FieldError? =
        if (password.isEmpty()) FieldError.PASSWORD_EMPTY else null

    fun validateNewPassword(password: String): FieldError? = when {
        password.isEmpty() -> FieldError.PASSWORD_EMPTY
        password.length < MIN_PASSWORD_LENGTH -> FieldError.PASSWORD_TOO_SHORT
        else -> null
    }

    /** The new password must be valid and different from the current one. */
    fun validateChangedPassword(newPassword: String, currentPassword: String): FieldError? =
        validateNewPassword(newPassword)
            ?: if (newPassword == currentPassword) FieldError.PASSWORD_SAME_AS_CURRENT else null

    fun validateConfirmPassword(password: String, confirm: String): FieldError? =
        if (password != confirm) FieldError.PASSWORD_MISMATCH else null

    fun validateName(name: String): FieldError? {
        val value = name.trim()
        return when {
            value.isEmpty() -> FieldError.NAME_EMPTY
            !NAME_REGEX.matches(value) -> FieldError.NAME_INVALID
            else -> null
        }
    }

    fun validateSurname(surname: String): FieldError? {
        val value = surname.trim()
        return when {
            value.isEmpty() -> FieldError.SURNAME_EMPTY
            !NAME_REGEX.matches(value) -> FieldError.SURNAME_INVALID
            else -> null
        }
    }

    fun validateUsername(username: String): FieldError? {
        val value = username.trim()
        return when {
            value.isEmpty() -> FieldError.USERNAME_EMPTY
            value.length < MIN_USERNAME_LENGTH -> FieldError.USERNAME_TOO_SHORT
            value.length > MAX_USERNAME_LENGTH -> FieldError.USERNAME_TOO_LONG
            !USERNAME_REGEX.matches(value) -> FieldError.USERNAME_BAD_CHARS
            else -> null
        }
    }

    /** Reads a decimal such as 1,75 or 1.75. Returns null if it is not a number. */
    fun parseDecimal(text: String): Double? =
        text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

    /** Height is optional. If given it must be 50 to 260 cm. */
    fun validateHeightCm(text: String): FieldError? {
        if (text.isBlank()) return null
        val value = parseDecimal(text) ?: return FieldError.NUMBER_INVALID
        return if (value in MIN_HEIGHT_CM..MAX_HEIGHT_CM) null else FieldError.HEIGHT_OUT_OF_RANGE
    }

    /** Weight is optional. If given it must be 20 to 400 kg. */
    fun validateWeightKg(text: String): FieldError? {
        if (text.isBlank()) return null
        val value = parseDecimal(text) ?: return FieldError.NUMBER_INVALID
        return if (value in MIN_WEIGHT_KG..MAX_WEIGHT_KG) null else FieldError.WEIGHT_OUT_OF_RANGE
    }

    /** Reads a date such as 2005-03-27. Returns null if it is not a real date. */
    fun parseIsoDate(text: String): SimpleDate? {
        val parts = text.trim().split("-")
        if (parts.size != 3) return null
        val year = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        val day = parts[2].toIntOrNull() ?: return null
        if (parts[0].length != 4 || month !in 1..12 || day < 1 || day > daysInMonth(year, month)) return null
        return SimpleDate(year, month, day)
    }

    /** Checks the date is real, not in the future, and the user is old enough. */
    fun validateBirthDate(text: String, today: SimpleDate): FieldError? {
        if (text.isBlank()) return FieldError.BIRTH_EMPTY
        val birth = parseIsoDate(text) ?: return FieldError.BIRTH_INVALID
        if (isAfter(birth, today)) return FieldError.BIRTH_IN_FUTURE
        return if (ageInYears(birth, today) < MIN_AGE_YEARS) FieldError.BIRTH_TOO_YOUNG else null
    }

    /** Checks the whole sign-up form. Returns one error per bad field. */
    fun validateRegistration(
        email: String, name: String, surname: String, birthDate: String,
        username: String, password: String, confirmPassword: String, today: SimpleDate
    ): Map<FormField, FieldError> {
        val errors = linkedMapOf<FormField, FieldError>()
        validateEmail(email)?.let { errors[FormField.EMAIL] = it }
        validateName(name)?.let { errors[FormField.NAME] = it }
        validateSurname(surname)?.let { errors[FormField.SURNAME] = it }
        validateBirthDate(birthDate, today)?.let { errors[FormField.BIRTH_DATE] = it }
        validateUsername(username)?.let { errors[FormField.USERNAME] = it }
        validateNewPassword(password)?.let { errors[FormField.PASSWORD] = it }
        // Only report a mismatch if the password itself was fine.
        if (!errors.containsKey(FormField.PASSWORD)) {
            validateConfirmPassword(password, confirmPassword)?.let { errors[FormField.CONFIRM_PASSWORD] = it }
        }
        return errors
    }

    /** Turns an email into a username, for example john.smith@x.com becomes john_smith. */
    fun usernameFromEmail(email: String): String {
        val local = email.substringBefore('@').replace(Regex("[^A-Za-z0-9_]"), "_")
        val padded = if (local.length < MIN_USERNAME_LENGTH) local.padEnd(MIN_USERNAME_LENGTH, '_') else local
        return padded.take(MAX_USERNAME_LENGTH)
    }

    private fun isLeap(year: Int) = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0

    private fun daysInMonth(year: Int, month: Int) = when (month) {
        2 -> if (isLeap(year)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }

    private fun isAfter(a: SimpleDate, b: SimpleDate): Boolean =
        a.year > b.year ||
            a.year == b.year && (a.month > b.month || a.month == b.month && a.day > b.day)

    private fun ageInYears(birth: SimpleDate, today: SimpleDate): Int {
        var age = today.year - birth.year
        if (today.month < birth.month || today.month == birth.month && today.day < birth.day) age--
        return age
    }
}
