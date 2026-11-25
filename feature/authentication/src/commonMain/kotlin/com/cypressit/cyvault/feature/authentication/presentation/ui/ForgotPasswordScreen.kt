package com.cypressit.cyvault.feature.authentication.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cypressit.cyvault.feature.authentication.presentation.viewmodel.AuthNavEvent
import com.cypressit.cyvault.feature.authentication.presentation.viewmodel.ForgotPasswordIntent
import com.cypressit.cyvault.feature.authentication.presentation.viewmodel.ForgotPasswordState
import com.cypressit.cyvault.feature.authentication.presentation.viewmodel.ForgotPasswordViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Sends a password reset email; starts with the [email] typed on the sign-in screen. */
@Composable
fun ForgotPasswordScreen(
    email: String,
    onNavigate: (AuthNavEvent) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ForgotPasswordViewModel = koinViewModel { parametersOf(email) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AuthScaffold(effects = viewModel.effect, onNavigate = onNavigate) {
        ForgotPasswordScreen(state = state, onIntent = viewModel::onIntent, modifier = modifier.fillMaxSize())
    }
}

@Composable
internal fun ForgotPasswordScreen(
    state: ForgotPasswordState,
    onIntent: (ForgotPasswordIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier
            .padding(horizontal = 24.dp)
            .imePadding(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Forgot Password?",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = "Enter your email and we'll send you a reset link.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = state.email,
            onValueChange = { onIntent(ForgotPasswordIntent.EmailChanged(it)) },
            label = { Text("Email") },
            isError = state.emailError != null,
            supportingText = state.emailError?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
                onIntent(ForgotPasswordIntent.SubmitForgotPassword)
            }),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(24.dp))

        if (state.isLoading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = { onIntent(ForgotPasswordIntent.SubmitForgotPassword) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Send Reset Link")
            }
        }

        Spacer(Modifier.height(16.dp))

        TextButton(onClick = { onIntent(ForgotPasswordIntent.BackToLogin) }) {
            Text("Back to Sign In")
        }
    }
}
