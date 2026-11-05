package com.example.opsc.auth

/** How strong a typed password looks. */
enum class PasswordStrength { EMPTY, WEAK, FAIR, STRONG }

/** Rates a password from its length and the mix of character types. It is only a hint; the real rules are in Validators and Firebase. */
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
        // One kind of character is always weak, however long.
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
