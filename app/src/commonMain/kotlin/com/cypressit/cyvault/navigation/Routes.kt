package com.cypressit.cyvault.navigation

import kotlinx.serialization.Serializable

/** Every route in the app. Features don't know these exist; only the app's navigation uses them. */

/** The sign-in flow, a nested graph: sign-out opens it, and signing in leaves it as a whole. */
@Serializable
data object AuthGraphRoute

@Serializable
data object LoginRoute

@Serializable
data object RegisterRoute

@Serializable
data class VerificationRoute(val email: String)

@Serializable
data class ForgotPasswordRoute(val email: String = "")

@Serializable
data object MfaPhoneNumberRoute

/** [sentTo] is the masked phone number Entra texted the code to. */
@Serializable
data class MfaCodeRoute(val sentTo: String, val codeLength: Int)

@Serializable
data object VaultHomeRoute

@Serializable
data object AddCardRoute
