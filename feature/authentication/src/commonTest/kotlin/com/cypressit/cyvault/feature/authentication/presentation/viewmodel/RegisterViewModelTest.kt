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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class) // setMain, resetMain, advanceUntilIdle
class RegisterViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val viewModels = AuthTestViewModels()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun RegisterViewModel.fillIn(confirmPassword: String = "password123") {
        onIntent(RegisterIntent.NameChanged(" Ada "))
        onIntent(RegisterIntent.EmailChanged(" ada@example.com "))
        onIntent(RegisterIntent.PasswordChanged("password123"))
        onIntent(RegisterIntent.ConfirmPasswordChanged(confirmPassword))
    }

    @Test
    fun validForm_createsTheAccountAndAsksForTheCode() = runTest(dispatcher) {
        val viewModel = viewModels.register()
        viewModel.fillIn()

        viewModel.onIntent(RegisterIntent.SubmitRegister)
        advanceUntilIdle()

        assertEquals(listOf(Triple("Ada", "ada@example.com", "password123")), viewModels.client.signUps)
        assertEquals(
            AuthEffect.Navigate(AuthNavEvent.Verification("ada@example.com")),
            viewModel.effect.first(),
        )
    }

    @Test
    fun emptyForm_showsEveryError() = runTest(dispatcher) {
        val viewModel = viewModels.register()
        viewModel.onIntent(RegisterIntent.ConfirmPasswordChanged("x"))

        viewModel.onIntent(RegisterIntent.SubmitRegister)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("Name is required", state.nameError)
        assertEquals("Enter a valid email address", state.emailError)
        assertEquals("Password must be at least 6 characters", state.passwordError)
        assertEquals("Passwords do not match", state.confirmPasswordError)
        assertTrue(viewModels.client.signUps.isEmpty())
    }

    @Test
    fun typing_clearsTheFieldsError() = runTest(dispatcher) {
        val viewModel = viewModels.register()
        viewModel.onIntent(RegisterIntent.ConfirmPasswordChanged("x"))
        viewModel.onIntent(RegisterIntent.SubmitRegister)

        viewModel.fillIn()

        val state = viewModel.state.value
        assertNull(state.nameError)
        assertNull(state.emailError)
        assertNull(state.passwordError)
        assertNull(state.confirmPasswordError)
    }

    @Test
    fun failure_showsTheError() = runTest(dispatcher) {
        viewModels.client.failure = "Email already in use"
        val viewModel = viewModels.register()
        viewModel.fillIn()

        viewModel.onIntent(RegisterIntent.SubmitRegister)
        advanceUntilIdle()

        assertEquals(AuthEffect.ShowError("Email already in use"), viewModel.effect.first())
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun backToSignIn_goesBack() = runTest(dispatcher) {
        val viewModel = viewModels.register()

        viewModel.onIntent(RegisterIntent.BackToLogin)
        advanceUntilIdle()

        assertEquals(AuthEffect.Navigate(AuthNavEvent.BackToLogin()), viewModel.effect.first())
    }
}
