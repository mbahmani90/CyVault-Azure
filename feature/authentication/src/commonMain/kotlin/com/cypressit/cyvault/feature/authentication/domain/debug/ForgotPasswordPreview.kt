package com.cypressit.cyvault.feature.authentication.domain.debug

/**
 * Turns on Forgot Password, which isn't finished: it sends the reset code, but entering the code
 * and a new password isn't built yet. Only debug builds provide it (androidApp's src/debug);
 * without it, the sign-in screen hides Forgot Password.
 */
data object ForgotPasswordPreview
