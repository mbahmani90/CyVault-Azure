package com.cypressit.cyvault.feature.authentication.presentation.viewmodel

import com.cypressit.cyvault.auth.AuthClient
import com.cypressit.cyvault.auth.SignInStep
import com.cypressit.cyvault.feature.authentication.di.authModule
import com.cypressit.cyvault.feature.authentication.fake.FakeAuthClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class) // setMain, resetMain, advanceUntilIdle
class AuthViewModelMfaTest {

    private val dispatcher = StandardTestDispatcher()
    private val authClient = FakeAuthClient()

    // authModule is one shared object and Koin keeps its singles in it, so each test closes its
    // app; otherwise the next test would get this test's repository, holding this test's fake.
    private val koinApp = koinApplication {
        modules(authModule, module { single<AuthClient> { authClient } })
    }

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() {
        koinApp.close()
        Dispatchers.resetMain()
    }

    private fun viewModel(): AuthViewModel = koinApp.koin.get()

    private fun TestScope.signIn(viewModel: AuthViewModel) {
        viewModel.onIntent(AuthIntent.EmailChanged("ada@example.com"))
        viewModel.onIntent(AuthIntent.PasswordChanged("password123"))
        viewModel.onIntent(AuthIntent.SubmitLogin)
        advanceUntilIdle()
    }

    @Test
    fun signInWithoutMfa_goesHome() = runTest(dispatcher) {
        val viewModel = viewModel()

        signIn(viewModel)

        assertEquals(AuthEffect.NavigateToHome, viewModel.effect.first())
        assertNull(viewModel.state.value.mfaStep)
    }

    @Test
    fun firstMfaSignIn_asksForThePhoneNumber() = runTest(dispatcher) {
        authClient.signInStep = SignInStep.PhoneNumberRequired
        val viewModel = viewModel()

        signIn(viewModel)

        assertEquals(MfaStep.PhoneNumber, viewModel.state.value.mfaStep)
    }

    @Test
    fun phoneNumber_isSentWithoutSpaces_thenAsksForTheCode() = runTest(dispatcher) {
        authClient.signInStep = SignInStep.PhoneNumberRequired
        val viewModel = viewModel()
        signIn(viewModel)

        viewModel.onIntent(AuthIntent.MfaPhoneNumberChanged("+351 912 345 678"))
        viewModel.onIntent(AuthIntent.SubmitMfaPhoneNumber)
        advanceUntilIdle()

        assertEquals(listOf("+351912345678"), authClient.submittedPhoneNumbers)
        val state = viewModel.state.value
        assertEquals(MfaStep.Code, state.mfaStep)
        assertEquals("+351*****678", state.mfaSentTo)
    }

    @Test
    fun phoneNumberWithoutCountryCode_isRejectedLocally() = runTest(dispatcher) {
        authClient.signInStep = SignInStep.PhoneNumberRequired
        val viewModel = viewModel()
        signIn(viewModel)

        viewModel.onIntent(AuthIntent.MfaPhoneNumberChanged("912345678"))
        viewModel.onIntent(AuthIntent.SubmitMfaPhoneNumber)
        advanceUntilIdle()

        assertEquals(MfaInputError.InvalidPhoneNumber, viewModel.state.value.mfaPhoneNumberError)
        assertTrue(authClient.submittedPhoneNumbers.isEmpty())
    }

    @Test
    fun laterMfaSignIn_goesStraightToTheCode() = runTest(dispatcher) {
        authClient.signInStep = SignInStep.CodeRequired(sentTo = "+351*****678", codeLength = 8)
        val viewModel = viewModel()

        signIn(viewModel)

        val state = viewModel.state.value
        assertEquals(MfaStep.Code, state.mfaStep)
        assertEquals(8, state.mfaCodeLength)
    }

    @Test
    fun codeInput_keepsDigitsUpToTheCodeLength() = runTest(dispatcher) {
        authClient.signInStep = SignInStep.CodeRequired(sentTo = "+351*****678", codeLength = 6)
        val viewModel = viewModel()
        signIn(viewModel)

        viewModel.onIntent(AuthIntent.MfaCodeChanged("12a3-45678"))

        assertEquals("123456", viewModel.state.value.mfaCode)
    }

    @Test
    fun incompleteCode_isRejectedLocally() = runTest(dispatcher) {
        authClient.signInStep = SignInStep.CodeRequired(sentTo = "+351*****678", codeLength = 6)
        val viewModel = viewModel()
        signIn(viewModel)

        viewModel.onIntent(AuthIntent.MfaCodeChanged("123"))
        viewModel.onIntent(AuthIntent.SubmitMfaCode)
        advanceUntilIdle()

        assertEquals(MfaInputError.IncompleteCode, viewModel.state.value.mfaCodeError)
        assertTrue(authClient.submittedMfaCodes.isEmpty())
    }

    @Test
    fun rightCode_goesHome() = runTest(dispatcher) {
        authClient.signInStep = SignInStep.CodeRequired(sentTo = "+351*****678", codeLength = 6)
        val viewModel = viewModel()
        signIn(viewModel)

        viewModel.onIntent(AuthIntent.MfaCodeChanged(authClient.validMfaCode))
        viewModel.onIntent(AuthIntent.SubmitMfaCode)
        advanceUntilIdle()

        assertEquals(AuthEffect.NavigateToHome, viewModel.effect.first())
    }

    @Test
    fun wrongCode_showsEntrasMessageAndClearsTheField() = runTest(dispatcher) {
        authClient.signInStep = SignInStep.CodeRequired(sentTo = "+351*****678", codeLength = 6)
        val viewModel = viewModel()
        signIn(viewModel)

        viewModel.onIntent(AuthIntent.MfaCodeChanged("000000"))
        viewModel.onIntent(AuthIntent.SubmitMfaCode)
        advanceUntilIdle()

        assertEquals(AuthEffect.ShowMfaFailed(FakeAuthClient.WRONG_CODE_MESSAGE), viewModel.effect.first())
        assertEquals("", viewModel.state.value.mfaCode)
        assertEquals(MfaStep.Code, viewModel.state.value.mfaStep)
    }

    @Test
    fun backToSignIn_leavesMfa() = runTest(dispatcher) {
        authClient.signInStep = SignInStep.PhoneNumberRequired
        val viewModel = viewModel()
        signIn(viewModel)

        viewModel.onIntent(AuthIntent.BackToLogin)

        assertNull(viewModel.state.value.mfaStep)
    }
}
