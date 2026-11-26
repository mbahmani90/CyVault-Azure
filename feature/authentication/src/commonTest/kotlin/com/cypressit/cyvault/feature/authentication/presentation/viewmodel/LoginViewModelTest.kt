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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class) // setMain, resetMain, advanceUntilIdle
class LoginViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val viewModels = AuthTestViewModels()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun alreadySignedIn_goesStraightHome() = runTest(dispatcher) {
        viewModels.client.signedInUser = FakeAuthClient.USER
        val viewModel = viewModels.login()
        advanceUntilIdle()

        assertEquals(AuthEffect.Navigate(AuthNavEvent.Home), viewModel.effect.first())
    }

    @Test
    fun badEmailAndShortPassword_areRejectedLocally() = runTest(dispatcher) {
        val viewModel = viewModels.login()

        viewModel.onIntent(LoginIntent.EmailChanged("ada"))
        viewModel.onIntent(LoginIntent.PasswordChanged("123"))
        viewModel.onIntent(LoginIntent.SubmitLogin)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("Enter a valid email address", state.emailError)
        assertEquals("Password must be at least 6 characters", state.passwordError)
        assertFalse(state.isLoading)
    }

    @Test
    fun typing_clearsTheFieldsError() = runTest(dispatcher) {
        val viewModel = viewModels.login()
        viewModel.onIntent(LoginIntent.SubmitLogin)

        viewModel.onIntent(LoginIntent.EmailChanged("ada@example.com"))
        viewModel.onIntent(LoginIntent.PasswordChanged("password123"))

        assertNull(viewModel.state.value.emailError)
        assertNull(viewModel.state.value.passwordError)
    }

    @Test
    fun register_opensRegister() = runTest(dispatcher) {
        val viewModel = viewModels.login()

        viewModel.onIntent(LoginIntent.OpenRegister)
        advanceUntilIdle()

        assertEquals(AuthEffect.Navigate(AuthNavEvent.Register), viewModel.effect.first())
    }

    @Test
    fun google_goesHome() = runTest(dispatcher) {
        val viewModel = viewModels.login()

        viewModel.onIntent(LoginIntent.SignInWithGoogle)
        advanceUntilIdle()

        assertEquals(AuthEffect.Navigate(AuthNavEvent.Home), viewModel.effect.first())
    }

    @Test
    fun googleFailure_showsTheError() = runTest(dispatcher) {
        viewModels.client.failure = "Cancelled"
        val viewModel = viewModels.login()

        viewModel.onIntent(LoginIntent.SignInWithGoogle)
        advanceUntilIdle()

        assertEquals(AuthEffect.ShowError("Cancelled"), viewModel.effect.first())
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun resetEmailSent_saysSo() = runTest(dispatcher) {
        val viewModel = viewModels.login()

        viewModel.onIntent(LoginIntent.ResetEmailSent)
        advanceUntilIdle()

        assertTrue(viewModel.effect.first() is AuthEffect.ShowMessage)
    }
}
