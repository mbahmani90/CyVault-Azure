package com.cypressit.cyvault.feature.authentication.presentation.viewmodel

import com.cypressit.cyvault.auth.AuthClient
import com.cypressit.cyvault.feature.authentication.data.repository.AuthRepositoryImpl
import com.cypressit.cyvault.feature.authentication.di.authModule
import com.cypressit.cyvault.feature.authentication.domain.debug.ForgotPasswordPreview
import com.cypressit.cyvault.feature.authentication.domain.repository.AuthRepository
import com.cypressit.cyvault.feature.authentication.fake.FakeAuthClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class) // setMain, resetMain
class AuthViewModelForgotPasswordTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    /** Built through authModule, as the app does; debug builds also provide ForgotPasswordPreview. */
    private fun viewModel(debugBuild: Boolean): AuthViewModel = koinApplication {
        modules(
            authModule,
            module {
                single<AuthClient> { FakeAuthClient() }
                // Koin 4.2 keeps authModule's singles across apps; this keeps another test's out.
                single<AuthRepository> { AuthRepositoryImpl(get()) }
                if (debugBuild) single { ForgotPasswordPreview }
            },
        )
    }.koin.get()

    @Test
    fun releaseBuild_hidesForgotPasswordAndIgnoresTheIntent() = runTest(dispatcher) {
        val viewModel = viewModel(debugBuild = false)
        assertFalse(viewModel.state.value.canResetPassword)

        viewModel.onIntent(AuthIntent.ShowForgotPassword)

        assertFalse(viewModel.state.value.isForgotPasswordMode)
    }

    @Test
    fun debugBuild_showsForgotPassword() = runTest(dispatcher) {
        val viewModel = viewModel(debugBuild = true)
        assertTrue(viewModel.state.value.canResetPassword)

        viewModel.onIntent(AuthIntent.ShowForgotPassword)

        assertTrue(viewModel.state.value.isForgotPasswordMode)
    }
}
