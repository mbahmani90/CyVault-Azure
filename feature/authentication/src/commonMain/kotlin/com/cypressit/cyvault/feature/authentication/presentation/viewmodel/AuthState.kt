package com.cypressit.cyvault.feature.authentication.presentation.viewmodel

data class AuthState(
    val isLoading: Boolean = false,
    val email: String = "",
    val password: String = "",
    val name: String = "",
    val confirmPassword: String = "",
    val verificationCode: String = "",
    val isRegisterMode: Boolean = false,
    val isForgotPasswordMode: Boolean = false,
    /** True in debug builds only, until resetting the password with the code is built. */
    val canResetPassword: Boolean = false,
    val isVerificationMode: Boolean = false,
    val nameError: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null,
    val confirmPasswordError: String? = null,
    val verificationCodeError: String? = null,
    /** Set while a password sign-in waits for SMS MFA. */
    val mfaStep: MfaStep? = null,
    val mfaPhoneNumber: String = "",
    val mfaCode: String = "",
    /** The masked phone number the code was texted to. */
    val mfaSentTo: String = "",
    val mfaCodeLength: Int = DEFAULT_MFA_CODE_LENGTH,
    val mfaPhoneNumberError: MfaInputError? = null,
    val mfaCodeError: MfaInputError? = null,
) {
    companion object {
        const val DEFAULT_MFA_CODE_LENGTH = 6
    }
}

enum class MfaStep {
    /** The account's first MFA sign-in: ask for the phone number codes go to. */
    PhoneNumber,

    /** A code was texted: ask for it. */
    Code,
}

enum class MfaInputError { InvalidPhoneNumber, IncompleteCode }
