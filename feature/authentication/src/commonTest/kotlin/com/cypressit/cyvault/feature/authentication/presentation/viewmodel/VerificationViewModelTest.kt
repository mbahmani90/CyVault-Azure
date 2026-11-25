package com.cypressit.cyvault.feature.authentication.presentation.viewmodel

import com.cypressit.cyvault.feature.authentication.fake.AuthTestViewModels
import com.cypressit.cyvault.feature.authentication.fake.FakeAuthClient
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
import kotlin.test.assertFalse
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class) // setMain, resetMain, advanceUntilIdle
class VerificationViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val viewModels = AuthTestViewModels()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun startsWithTheEmailTheCodeWentTo() {
        assertEquals("ada@example.com", viewModels.verification("ada@example.com").state.value.email)
    }

    @Test
    fun rightCode_goesHome() = runTest(dispatcher) {
        val viewModel = viewModels.verification()

        viewModel.onIntent(VerificationIntent.CodeChanged(" ${viewModels.client.validSignUpCode} "))
        viewModel.onIntent(VerificationIntent.SubmitVerification)
        advanceUntilIdle()

        assertEquals(AuthEffect.Navigate(AuthNavEvent.Home), viewModel.effect.first())
    }

    @Test
    fun emptyCode_isRejectedLocally_andTypingClearsTheError() = runTest(dispatcher) {
        val viewModel = viewModels.verification()

        viewModel.onIntent(VerificationIntent.SubmitVerification)
        assertEquals("Please enter the verification code", viewModel.state.value.verificationCodeError)

        viewModel.onIntent(VerificationIntent.CodeChanged("1"))
        assertNull(viewModel.state.value.verificationCodeError)
    }

    @Test
    fun wrongCode_showsTheError() = runTest(dispatcher) {
        val viewModel = viewModels.verification()

        viewModel.onIntent(VerificationIntent.CodeChanged("000000"))
        viewModel.onIntent(VerificationIntent.SubmitVerification)
        advanceUntilIdle()

        assertEquals(AuthEffect.ShowError(FakeAuthClient.WRONG_CODE_MESSAGE), viewModel.effect.first())
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun backToSignIn_goesBack() = runTest(dispatcher) {
        val viewModel = viewModels.verification()

        viewModel.onIntent(VerificationIntent.BackToLogin)
        advanceUntilIdle()

        assertEquals(AuthEffect.Navigate(AuthNavEvent.BackToLogin()), viewModel.effect.first())
    }
}
