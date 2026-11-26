package com.cypressit.cyvault.feature.authentication.presentation.viewmodel

/** Input checks shared by the sign-in, register and forgot-password screens. */
internal object AuthInput {
    const val MIN_PASSWORD_LENGTH = 6

    fun isValidEmail(email: String): Boolean = email.isNotBlank() && email.contains('@')
}
