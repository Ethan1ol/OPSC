package com.example.opsc.auth

/** How strong a typed password looks, for the live hint on the sign-up screen. */
enum class PasswordStrength { EMPTY, WEAK, FAIR, STRONG }

/**
 * Rates a password from its length and the mix of character types (lower case, upper case,
 * digits, symbols). Pure Kotlin so it can be unit tested. This is only a hint for the user;
 * the real minimum is enforced by [Validators.validateNewPassword] and by Firebase.
 */
object PasswordStrengthRater {
    private val COMMON = setOf("password", "password1", "123456", "1234567", "12345678", "123456789", "qwerty", "qwerty123", "abc123", "111111", "letmein", "iloveyou")

    fun rate(password: String): PasswordStrength {
        if (password.isEmpty()) return PasswordStrength.EMPTY
        if (password.length < Validators.MIN_PASSWORD_LENGTH) return PasswordStrength.WEAK
        if (password.lowercase() in COMMON) return PasswordStrength.WEAK

        val classes = listOf(
            password.any { it.isLowerCase() },
            password.any { it.isUpperCase() },
            password.any { it.isDigit() },
            password.any { !it.isLetterOrDigit() }
        ).count { it }
        // A password made of only one kind of character is always weak, however long.
        if (classes <= 1) return PasswordStrength.WEAK

        val lengthPoints = when {
            password.length >= 12 -> 2
            password.length >= 8 -> 1
            else -> 0
        }
        val score = lengthPoints + (classes - 1)
        return when {
            score >= 4 -> PasswordStrength.STRONG
            score >= 2 -> PasswordStrength.FAIR
            else -> PasswordStrength.WEAK
        }
    }
}
