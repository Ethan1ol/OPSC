package com.example.opsc.auth

import androidx.annotation.StringRes
import com.example.opsc.R

/** Maps a pure-Kotlin [FieldError] to the translated string resource shown in the UI. */
@StringRes
fun FieldError.messageRes(): Int = when (this) {
    FieldError.EMAIL_EMPTY -> R.string.err_email_empty
    FieldError.EMAIL_INVALID -> R.string.err_email_invalid
    FieldError.PASSWORD_EMPTY -> R.string.err_password_empty
    FieldError.PASSWORD_TOO_SHORT -> R.string.err_password_short
    FieldError.PASSWORD_MISMATCH -> R.string.err_password_mismatch
    FieldError.PASSWORD_SAME_AS_CURRENT -> R.string.err_password_same
    FieldError.NAME_EMPTY -> R.string.err_name_empty
    FieldError.NAME_INVALID -> R.string.err_name_invalid
    FieldError.SURNAME_EMPTY -> R.string.err_surname_empty
    FieldError.SURNAME_INVALID -> R.string.err_surname_invalid
    FieldError.USERNAME_EMPTY -> R.string.err_username_empty
    FieldError.USERNAME_TOO_SHORT -> R.string.err_username_short
    FieldError.USERNAME_TOO_LONG -> R.string.err_username_long
    FieldError.USERNAME_BAD_CHARS -> R.string.err_username_chars
    FieldError.BIRTH_EMPTY -> R.string.err_birth_empty
    FieldError.BIRTH_INVALID -> R.string.err_birth_invalid
    FieldError.BIRTH_IN_FUTURE -> R.string.err_birth_future
    FieldError.BIRTH_TOO_YOUNG -> R.string.err_birth_young
    FieldError.NUMBER_INVALID -> R.string.err_number_invalid
    FieldError.HEIGHT_OUT_OF_RANGE -> R.string.err_height_range
    FieldError.WEIGHT_OUT_OF_RANGE -> R.string.err_weight_range
    FieldError.HEIGHT_OUT_OF_RANGE_IMPERIAL -> R.string.err_height_range_in
    FieldError.WEIGHT_OUT_OF_RANGE_IMPERIAL -> R.string.err_weight_range_lb
}
