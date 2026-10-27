package com.cypressit.cyvault.feature.authentication.presentation.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.cypressit.cyvault.feature.authentication.presentation.viewmodel.AuthEffect
import com.cypressit.cyvault.feature.authentication.presentation.viewmodel.AuthViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                AuthEffect.NavigateToHome -> onAuthSuccess()
                is AuthEffect.ShowError -> snackbarHostState.showSnackbar(effect.message)
                is AuthEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { _ ->
        when {
            state.isVerificationMode -> VerificationScreen(
                state = state,
                onIntent = viewModel::onIntent,
                modifier = modifier.fillMaxSize(),
            )
            state.isForgotPasswordMode -> ForgotPasswordScreen(
                state = state,
                onIntent = viewModel::onIntent,
                modifier = modifier.fillMaxSize(),
            )
            state.isRegisterMode -> RegisterScreen(
                state = state,
                onIntent = viewModel::onIntent,
                modifier = modifier.fillMaxSize(),
            )
            else -> LoginScreen(
                state = state,
                onIntent = viewModel::onIntent,
                modifier = modifier.fillMaxSize(),
            )
        }
    }
}
