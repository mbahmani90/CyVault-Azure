package com.cypressit.cyvault.feature.authentication.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.cypressit.cyvault.auth.SignInStep
import com.cypressit.cyvault.feature.authentication.fake.AuthTestViewModels
import com.cypressit.cyvault.feature.authentication.fake.FakeAuthClient
import com.cypressit.cyvault.feature.authentication.presentation.viewmodel.AuthNavEvent
import com.cypressit.cyvault.systemdesign.theme.CyTheme
import com.cypressit.cyvault.testtags.LoginTags
import com.cypressit.cyvault.testtags.MfaTags
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Each auth screen with its real ViewModel: taps go through it and come back as navigation or messages. */
@OptIn(ExperimentalTestApi::class) // runComposeUiTest
@RunWith(RobolectricTestRunner::class)
class AuthScreensTest {

    private val viewModels = AuthTestViewModels()
    private val navigated = mutableListOf<AuthNavEvent>()

    private fun assertNavigated(expected: List<AuthNavEvent>) = assertEquals(expected, navigated)

    private fun ComposeUiTest.show(screen: @Composable () -> Unit) = setContent {
        CyTheme(darkTheme = false) { screen() }
    }

    @Test
    fun login_signsIn_andOpensRegisterAndForgotPassword() = runComposeUiTest {
        // Built outside setContent: a ViewModel constructed in a composable is a lint error.
        val viewModel = viewModels.login(debugBuild = true)
        show {
            LoginScreen(
                resetEmailSent = false,
                onResetEmailSentShown = {},
                onNavigate = { navigated += it },
                viewModel = viewModel,
            )
        }

        onNodeWithTag(LoginTags.EMAIL_FIELD).performTextInput("ada@example.com")
        onNodeWithTag(LoginTags.PASSWORD_FIELD).performTextInput("password123")
        onNodeWithText("Show").performClick()
        onNodeWithTag(LoginTags.SIGN_IN_BUTTON).performClick()
        onNodeWithText("Forgot Password?").performClick()
        onNodeWithText("Don't have an account? Register").performClick()
        waitForIdle()

        assertNavigated(
            listOf(AuthNavEvent.Home, AuthNavEvent.ForgotPassword("ada@example.com"), AuthNavEvent.Register),
        )
    }

    @Test
    fun login_resetEmailSent_showsTheMessageOnce() = runComposeUiTest {
        val viewModel = viewModels.login()
        var shown = 0
        show {
            LoginScreen(
                resetEmailSent = true,
                onResetEmailSentShown = { shown++ },
                onNavigate = { navigated += it },
                viewModel = viewModel,
            )
        }

        onNodeWithText("Password reset email sent", substring = true).assertIsDisplayed()
        assertEquals(1, shown)
    }

    @Test
    fun login_invalidInput_showsTheErrors() = runComposeUiTest {
        val viewModel = viewModels.login()
        show {
            LoginScreen(
                resetEmailSent = false,
                onResetEmailSentShown = {},
                onNavigate = { navigated += it },
                viewModel = viewModel,
            )
        }

        onNodeWithTag(LoginTags.SIGN_IN_BUTTON).performClick()

        onNodeWithText("Enter a valid email address").assertIsDisplayed()
        assertFalse(navigated.isNotEmpty())
    }

    @Test
    fun register_createsTheAccount_thenAsksForTheCode() = runComposeUiTest {
        val viewModel = viewModels.register()
        show { RegisterScreen(onNavigate = { navigated += it }, viewModel = viewModel) }

        onNodeWithText("Full Name").performTextInput("Ada")
        onNodeWithText("Email").performTextInput("ada@example.com")
        onNodeWithText("Password").performTextInput("password123")
        onNodeWithText("Confirm Password").performTextInput("password123")
        onNodeWithText("Create Account").performScrollTo().performClick()
        onNodeWithText("Already have an account? Sign in").performScrollTo().performClick()
        waitForIdle()

        assertNavigated(
            listOf(AuthNavEvent.Verification("ada@example.com"), AuthNavEvent.BackToLogin()),
        )
    }

    @Test
    fun register_failure_showsTheSnackbar() = runComposeUiTest {
        viewModels.client.failure = "Email already in use"
        val viewModel = viewModels.register()
        show { RegisterScreen(onNavigate = { navigated += it }, viewModel = viewModel) }

        onNodeWithText("Full Name").performTextInput("Ada")
        onNodeWithText("Email").performTextInput("ada@example.com")
        onNodeWithText("Password").performTextInput("password123")
        onNodeWithText("Confirm Password").performTextInput("password123")
        onNodeWithText("Create Account").performScrollTo().performClick()

        onNodeWithText("Email already in use").assertIsDisplayed()
    }

    @Test
    fun verification_confirmsTheCode_andGoesBack() = runComposeUiTest {
        val viewModel = viewModels.verification("ada@example.com")
        show {
            VerificationScreen(email = "ada@example.com", onNavigate = { navigated += it }, viewModel = viewModel)
        }

        onNodeWithText("ada@example.com", substring = true).assertIsDisplayed()
        onNodeWithText("Verification Code").performTextInput(viewModels.client.validSignUpCode)
        onNodeWithText("Verify").performClick()
        onNodeWithText("Back to Sign In").performClick()
        waitForIdle()

        assertNavigated(listOf(AuthNavEvent.Home, AuthNavEvent.BackToLogin()))
    }

    @Test
    fun forgotPassword_sendsTheEmail_andGoesBackSayingSo() = runComposeUiTest {
        val viewModel = viewModels.forgotPassword("ada@example.com")
        show {
            ForgotPasswordScreen(email = "ada@example.com", onNavigate = { navigated += it }, viewModel = viewModel)
        }

        onNodeWithText("Send Reset Link").performClick()
        onNodeWithText("Back to Sign In").performClick()
        waitForIdle()

        assertEquals(listOf("ada@example.com"), viewModels.client.resetPasswordEmails)
        assertNavigated(
            listOf(AuthNavEvent.BackToLogin(resetEmailSent = true), AuthNavEvent.BackToLogin()),
        )
    }

    @Test
    fun mfaPhoneNumber_sendsTheNumber_thenAsksForTheCode() = runComposeUiTest {
        viewModels.client.phoneNumberStep = SignInStep.CodeRequired(sentTo = "+351*****678", codeLength = 6)
        val viewModel = viewModels.mfaPhoneNumber()
        show { MfaPhoneNumberScreen(onNavigate = { navigated += it }, viewModel = viewModel) }

        onNodeWithTag(MfaTags.PHONE_NUMBER_FIELD).performTextInput("+351 912 345 678")
        onNodeWithTag(MfaTags.SEND_CODE_BUTTON).performClick()
        onNodeWithText("Back to Sign In").performClick()
        waitForIdle()

        assertNavigated(
            listOf(AuthNavEvent.MfaCode("+351*****678", 6), AuthNavEvent.BackToLogin()),
        )
    }

    @Test
    fun mfaPhoneNumber_invalidNumber_showsTheHint() = runComposeUiTest {
        val viewModel = viewModels.mfaPhoneNumber()
        show { MfaPhoneNumberScreen(onNavigate = { navigated += it }, viewModel = viewModel) }

        onNodeWithTag(MfaTags.PHONE_NUMBER_FIELD).performTextInput("912345678")
        onNodeWithTag(MfaTags.SEND_CODE_BUTTON).performClick()

        onNodeWithText("Enter the number with + and the country code", substring = true).assertIsDisplayed()
    }

    private fun ComposeUiTest.showMfaCode() {
        val viewModel = viewModels.mfaCode(sentTo = "+351*****678", codeLength = 6)
        show {
            MfaCodeScreen(
                sentTo = "+351*****678",
                codeLength = 6,
                onNavigate = { navigated += it },
                viewModel = viewModel,
            )
        }
    }

    @Test
    fun mfaCode_rightCodeGoesHome_andBackGoesToSignIn() = runComposeUiTest {
        showMfaCode()

        onNodeWithText("+351*****678", substring = true).assertIsDisplayed()
        onNodeWithTag(MfaTags.CODE_FIELD).performTextInput(viewModels.client.validMfaCode)
        onNodeWithTag(MfaTags.VERIFY_BUTTON).performClick()
        onNodeWithText("Back to Sign In").performClick()
        waitForIdle()

        assertNavigated(listOf(AuthNavEvent.Home, AuthNavEvent.BackToLogin()))
    }

    @Test
    fun mfaCode_wrongCode_showsEntrasMessage() = runComposeUiTest {
        showMfaCode()

        onNodeWithTag(MfaTags.CODE_FIELD).performTextInput("000000")
        onNodeWithTag(MfaTags.VERIFY_BUTTON).performClick()

        onNodeWithText(FakeAuthClient.WRONG_CODE_MESSAGE).assertIsDisplayed()
    }

    @Test
    fun mfaCode_incompleteCode_showsTheHint() = runComposeUiTest {
        val viewModel = viewModels.mfaCode(codeLength = 6)
        show { MfaCodeScreen(sentTo = "+351*****678", codeLength = 6, onNavigate = {}, viewModel = viewModel) }

        onNodeWithTag(MfaTags.CODE_FIELD).performTextInput("123")
        onNodeWithTag(MfaTags.VERIFY_BUTTON).performClick()

        onNodeWithText("Enter all 6 digits").assertIsDisplayed()
    }
}
