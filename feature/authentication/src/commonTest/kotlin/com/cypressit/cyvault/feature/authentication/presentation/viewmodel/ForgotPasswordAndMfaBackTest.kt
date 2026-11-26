package com.cypressit.cyvault.feature.authentication.presentation.viewmodel

import com.cypressit.cyvault.feature.authentication.fake.AuthTestViewModels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Forgot Password and MFA phone-number paths the flow tests don't reach. */
@OptIn(ExperimentalCoroutinesApi::class) // setMain, resetMain, advanceUntilIdle
class ForgotPasswordAndMfaBackTest {

    private val dispatcher = StandardTestDispatcher()
    private val viewModels = AuthTestViewModels()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun forgotPassword_invalidEmail_isRejectedLocally_andTypingClearsTheError() = runTest(dispatcher) {
        val viewModel = viewModels.forgotPassword(email = "ada")

        viewModel.onIntent(ForgotPasswordIntent.SubmitForgotPassword)
        assertEquals("Enter a valid email address", viewModel.state.value.emailError)

        viewModel.onIntent(ForgotPasswordIntent.EmailChanged("ada@example.com"))
        assertNull(viewModel.state.value.emailError)
        assertTrue(viewModels.client.resetPasswordEmails.isEmpty())
    }

    @Test
    fun forgotPassword_back_goesToSignIn() = runTest(dispatcher) {
        val viewModel = viewModels.forgotPassword()

        viewModel.onIntent(ForgotPasswordIntent.BackToLogin)
        advanceUntilIdle()

        assertEquals(AuthEffect.Navigate(AuthNavEvent.BackToLogin()), viewModel.effect.first())
    }

    @Test
    fun mfaPhoneNumber_back_goesToSignIn() = runTest(dispatcher) {
        val viewModel = viewModels.mfaPhoneNumber()

        viewModel.onIntent(MfaPhoneNumberIntent.BackToLogin)
        advanceUntilIdle()

        assertEquals(AuthEffect.Navigate(AuthNavEvent.BackToLogin()), viewModel.effect.first())
    }

    @Test
    fun mfaCode_zeroLength_usesTheDefault() {
        assertEquals(MfaCodeState.DEFAULT_CODE_LENGTH, viewModels.mfaCode(codeLength = 0).state.value.codeLength)
    }
}
