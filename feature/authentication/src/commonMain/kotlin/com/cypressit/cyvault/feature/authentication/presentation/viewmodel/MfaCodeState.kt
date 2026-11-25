package com.cypressit.cyvault.feature.authentication.presentation.viewmodel

data class MfaCodeState(
    /** The masked phone number the code was texted to. */
    val sentTo: String,
    val codeLength: Int = DEFAULT_CODE_LENGTH,
    val isLoading: Boolean = false,
    val code: String = "",
    val codeError: MfaInputError? = null,
) {
    companion object {
        const val DEFAULT_CODE_LENGTH = 6
    }
}
