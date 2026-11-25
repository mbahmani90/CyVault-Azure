package com.cypressit.cyvault.feature.authentication.presentation.viewmodel

import com.cypressit.cyvault.auth.AuthClient
import com.cypressit.cyvault.auth.SignInStep
import com.cypressit.cyvault.feature.authentication.data.repository.AuthRepositoryImpl
import com.cypressit.cyvault.feature.authentication.di.authModule
import com.cypressit.cyvault.feature.authentication.domain.repository.AuthRepository
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
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Sign-in with MFA across the sign-in, phone number and code screens' ViewModels. */
@OptIn(ExperimentalCoroutinesApi::class) // setMain, resetMain, advanceUntilIdle
class MfaFlowTest {

    private val dispatcher = StandardTestDispatcher()
    private val authClient = FakeAuthClient()

    // authModule is one shared object and Koin 4.2 keeps its singles in it even after the app
    // closes, so the next test would get an earlier test's repository, holding that test's fake.
    // Each test declares its own repository, which overrides the shared one.
    private val koinApp = koinApplication {
        modules(
            authModule,
            module {
                single<AuthClient> { authClient }
                single<AuthRepository> { AuthRepositoryImpl(get()) }
            },
        )
    }

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() {
        koinApp.close()
        Dispatchers.resetMain()
    }

    private fun loginViewModel(): LoginViewModel = koinApp.koin.get()
    private fun phoneNumberViewModel(): MfaPhoneNumberViewModel = koinApp.koin.get()
    private fun codeViewModel(codeLength: Int = 6): MfaCodeViewModel =
        koinApp.koin.get { parametersOf("+351*****678", codeLength) }

    /** Signs in and returns where the sign-in screen goes next. */
    private suspend fun TestScope.signIn(): AuthEffect {
        val viewModel = loginViewModel()
        viewModel.onIntent(LoginIntent.EmailChanged("ada@example.com"))
        viewModel.onIntent(LoginIntent.PasswordChanged("password123"))
        viewModel.onIntent(LoginIntent.SubmitLogin)
        advanceUntilIdle()
        return viewModel.effect.first()
    }

    @Test
    fun signInWithoutMfa_goesHome() = runTest(dispatcher) {
        assertEquals(AuthEffect.Navigate(AuthNavEvent.Home), signIn())
    }

    @Test
    fun firstMfaSignIn_asksForThePhoneNumber() = runTest(dispatcher) {
        authClient.signInStep = SignInStep.PhoneNumberRequired

        assertEquals(AuthEffect.Navigate(AuthNavEvent.MfaPhoneNumber), signIn())
    }

    @Test
    fun laterMfaSignIn_goesStraightToTheCode() = runTest(dispatcher) {
        authClient.signInStep = SignInStep.CodeRequired(sentTo = "+351*****678", codeLength = 8)

        assertEquals(AuthEffect.Navigate(AuthNavEvent.MfaCode("+351*****678", 8)), signIn())
    }

    @Test
    fun phoneNumber_isSentWithoutSpaces_thenAsksForTheCode() = runTest(dispatcher) {
        val viewModel = phoneNumberViewModel()

        viewModel.onIntent(MfaPhoneNumberIntent.PhoneNumberChanged("+351 912 345 678"))
        viewModel.onIntent(MfaPhoneNumberIntent.SubmitPhoneNumber)
        advanceUntilIdle()

        assertEquals(listOf("+351912345678"), authClient.submittedPhoneNumbers)
        val effect = viewModel.effect.first()
        assertTrue(effect is AuthEffect.Navigate && effect.to is AuthNavEvent.MfaCode, "Got $effect")
        assertEquals("+351*****678", effect.to.sentTo)
    }

    @Test
    fun phoneNumberWithoutCountryCode_isRejectedLocally() = runTest(dispatcher) {
        val viewModel = phoneNumberViewModel()

        viewModel.onIntent(MfaPhoneNumberIntent.PhoneNumberChanged("912345678"))
        viewModel.onIntent(MfaPhoneNumberIntent.SubmitPhoneNumber)
        advanceUntilIdle()

        assertEquals(MfaInputError.InvalidPhoneNumber, viewModel.state.value.phoneNumberError)
        assertTrue(authClient.submittedPhoneNumbers.isEmpty())
    }

    @Test
    fun codeInput_keepsDigitsUpToTheCodeLength() = runTest(dispatcher) {
        val viewModel = codeViewModel(codeLength = 6)

        viewModel.onIntent(MfaCodeIntent.CodeChanged("12a3-45678"))

        assertEquals("123456", viewModel.state.value.code)
    }

    @Test
    fun incompleteCode_isRejectedLocally() = runTest(dispatcher) {
        val viewModel = codeViewModel()

        viewModel.onIntent(MfaCodeIntent.CodeChanged("123"))
        viewModel.onIntent(MfaCodeIntent.SubmitCode)
        advanceUntilIdle()

        assertEquals(MfaInputError.IncompleteCode, viewModel.state.value.codeError)
        assertTrue(authClient.submittedMfaCodes.isEmpty())
    }

    @Test
    fun rightCode_goesHome() = runTest(dispatcher) {
        val viewModel = codeViewModel()

        viewModel.onIntent(MfaCodeIntent.CodeChanged(authClient.validMfaCode))
        viewModel.onIntent(MfaCodeIntent.SubmitCode)
        advanceUntilIdle()

        assertEquals(AuthEffect.Navigate(AuthNavEvent.Home), viewModel.effect.first())
    }

    @Test
    fun wrongCode_showsEntrasMessageAndClearsTheField() = runTest(dispatcher) {
        val viewModel = codeViewModel()

        viewModel.onIntent(MfaCodeIntent.CodeChanged("000000"))
        viewModel.onIntent(MfaCodeIntent.SubmitCode)
        advanceUntilIdle()

        assertEquals(AuthEffect.ShowMfaFailed(FakeAuthClient.WRONG_CODE_MESSAGE), viewModel.effect.first())
        assertEquals("", viewModel.state.value.code)
    }

    @Test
    fun backToSignIn_leavesMfa() = runTest(dispatcher) {
        val viewModel = codeViewModel()

        viewModel.onIntent(MfaCodeIntent.BackToLogin)
        advanceUntilIdle()

        assertEquals(AuthEffect.Navigate(AuthNavEvent.BackToLogin()), viewModel.effect.first())
    }
}
