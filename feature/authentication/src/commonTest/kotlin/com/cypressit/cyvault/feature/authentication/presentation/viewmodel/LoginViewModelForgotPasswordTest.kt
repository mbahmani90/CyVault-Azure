package com.cypressit.cyvault.feature.authentication.presentation.viewmodel

import com.cypressit.cyvault.auth.AuthClient
import com.cypressit.cyvault.feature.authentication.data.repository.AuthRepositoryImpl
import com.cypressit.cyvault.feature.authentication.di.authModule
import com.cypressit.cyvault.feature.authentication.domain.debug.ForgotPasswordPreview
import com.cypressit.cyvault.feature.authentication.domain.repository.AuthRepository
import com.cypressit.cyvault.feature.authentication.fake.FakeAuthClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class) // setMain, resetMain, advanceUntilIdle
class LoginViewModelForgotPasswordTest {

    private val dispatcher = StandardTestDispatcher()
    private val authClient = FakeAuthClient()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    /** Built through authModule, as the app does; debug builds also provide ForgotPasswordPreview. */
    private fun koin(debugBuild: Boolean) = koinApplication {
        modules(
            authModule,
            module {
                single<AuthClient> { authClient }
                // Koin 4.2 keeps authModule's singles across apps; this keeps another test's out.
                single<AuthRepository> { AuthRepositoryImpl(get()) }
                if (debugBuild) single { ForgotPasswordPreview }
            },
        )
    }.koin

    @Test
    fun releaseBuild_hidesForgotPasswordAndIgnoresTheIntent() = runTest(dispatcher) {
        val viewModel: LoginViewModel = koin(debugBuild = false).get()
        assertFalse(viewModel.state.value.canResetPassword)

        viewModel.onIntent(LoginIntent.OpenForgotPassword)
        advanceUntilIdle()

        assertNull(withTimeoutOrNull(1) { viewModel.effect.first() })
    }

    @Test
    fun debugBuild_opensForgotPasswordWithTheTypedEmail() = runTest(dispatcher) {
        val viewModel: LoginViewModel = koin(debugBuild = true).get()
        assertTrue(viewModel.state.value.canResetPassword)

        viewModel.onIntent(LoginIntent.EmailChanged(" ada@example.com "))
        viewModel.onIntent(LoginIntent.OpenForgotPassword)
        advanceUntilIdle()

        assertEquals(AuthEffect.Navigate(AuthNavEvent.ForgotPassword("ada@example.com")), viewModel.effect.first())
    }

    @Test
    fun resetEmailSent_goesBackToSignInSayingSo() = runTest(dispatcher) {
        val viewModel: ForgotPasswordViewModel = koin(debugBuild = true).get { parametersOf("ada@example.com") }

        viewModel.onIntent(ForgotPasswordIntent.SubmitForgotPassword)
        advanceUntilIdle()

        assertEquals(listOf("ada@example.com"), authClient.resetPasswordEmails)
        assertEquals(
            AuthEffect.Navigate(AuthNavEvent.BackToLogin(resetEmailSent = true)),
            viewModel.effect.first(),
        )
    }
}
