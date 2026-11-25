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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cypressit.cyvault.feature.authentication.presentation.viewmodel.AuthNavEvent
import com.cypressit.cyvault.feature.authentication.presentation.viewmodel.MfaCodeIntent
import com.cypressit.cyvault.feature.authentication.presentation.viewmodel.MfaCodeState
import com.cypressit.cyvault.feature.authentication.presentation.viewmodel.MfaCodeViewModel
import com.cypressit.cyvault.feature.authentication.presentation.viewmodel.MfaInputError
import com.cypressit.cyvault.testtags.MfaTags
import cyvault.authentication.generated.resources.Res
import cyvault.authentication.generated.resources.mfa_back_to_sign_in
import cyvault.authentication.generated.resources.mfa_code_incomplete
import cyvault.authentication.generated.resources.mfa_code_label
import cyvault.authentication.generated.resources.mfa_code_subtitle
import cyvault.authentication.generated.resources.mfa_code_title
import cyvault.authentication.generated.resources.mfa_code_verify
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Asks for the [codeLength]-digit code texted to [sentTo]. */
@Composable
fun MfaCodeScreen(
    sentTo: String,
    codeLength: Int,
    onNavigate: (AuthNavEvent) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MfaCodeViewModel = koinViewModel { parametersOf(sentTo, codeLength) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AuthScaffold(effects = viewModel.effect, onNavigate = onNavigate) {
        MfaCodeScreen(state = state, onIntent = viewModel::onIntent, modifier = modifier.fillMaxSize())
    }
}

/** The SMS code that completes an MFA sign-in. */
@Composable
internal fun MfaCodeScreen(
    state: MfaCodeState,
    onIntent: (MfaCodeIntent) -> Unit,
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
        Text(text = stringResource(Res.string.mfa_code_title), style = MaterialTheme.typography.headlineMedium)

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(Res.string.mfa_code_subtitle, state.sentTo),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = state.code,
            onValueChange = { onIntent(MfaCodeIntent.CodeChanged(it)) },
            label = { Text(stringResource(Res.string.mfa_code_label)) },
            isError = state.codeError != null,
            supportingText = if (state.codeError == MfaInputError.IncompleteCode) {
                { Text(stringResource(Res.string.mfa_code_incomplete, state.codeLength)) }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
                onIntent(MfaCodeIntent.SubmitCode)
            }),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag(MfaTags.CODE_FIELD),
        )

        Spacer(Modifier.height(24.dp))

        if (state.isLoading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = { onIntent(MfaCodeIntent.SubmitCode) },
                modifier = Modifier.fillMaxWidth().testTag(MfaTags.VERIFY_BUTTON),
            ) {
                Text(stringResource(Res.string.mfa_code_verify))
            }
        }

        Spacer(Modifier.height(16.dp))

        TextButton(onClick = { onIntent(MfaCodeIntent.BackToLogin) }) {
            Text(stringResource(Res.string.mfa_back_to_sign_in))
        }
    }
}
