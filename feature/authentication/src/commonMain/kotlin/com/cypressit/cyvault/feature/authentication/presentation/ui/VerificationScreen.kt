package com.cypressit.cyvault.feature.authentication.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cypressit.cyvault.feature.authentication.presentation.viewmodel.AuthNavEvent
import com.cypressit.cyvault.feature.authentication.presentation.viewmodel.VerificationIntent
import com.cypressit.cyvault.feature.authentication.presentation.viewmodel.VerificationState
import com.cypressit.cyvault.feature.authentication.presentation.viewmodel.VerificationViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Confirms a new account with the code emailed to [email]. */
@Composable
fun VerificationScreen(
    email: String,
    onNavigate: (AuthNavEvent) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VerificationViewModel = koinViewModel { parametersOf(email) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AuthScaffold(effects = viewModel.effect, onNavigate = onNavigate) {
        VerificationScreen(state = state, onIntent = viewModel::onIntent, modifier = modifier.fillMaxSize())
    }
}

@Composable
internal fun VerificationScreen(
    state: VerificationState,
    onIntent: (VerificationIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier
            .padding(horizontal = 24.dp)
            .imePadding()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "Verify your email", style = MaterialTheme.typography.headlineMedium)

        Spacer(Modifier.height(8.dp))

        Text(
            text = "We sent a verification code to\n${state.email}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = state.verificationCode,
            onValueChange = { onIntent(VerificationIntent.CodeChanged(it)) },
            label = { Text("Verification Code") },
            isError = state.verificationCodeError != null,
            supportingText = state.verificationCodeError?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
                onIntent(VerificationIntent.SubmitVerification)
            }),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(24.dp))

        if (state.isLoading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = { onIntent(VerificationIntent.SubmitVerification) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Verify")
            }
        }

        Spacer(Modifier.height(16.dp))

        TextButton(onClick = { onIntent(VerificationIntent.BackToLogin) }) {
            Text("Back to Sign In")
        }
    }
}
