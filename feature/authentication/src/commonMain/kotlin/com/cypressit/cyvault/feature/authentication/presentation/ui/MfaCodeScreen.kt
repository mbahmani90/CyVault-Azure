package com.cypressit.cyvault.feature.authentication.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cypressit.cyvault.feature.authentication.presentation.viewmodel.AuthIntent
import com.cypressit.cyvault.feature.authentication.presentation.viewmodel.AuthState
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

/** The SMS code that completes an MFA sign-in. */
@Composable
fun MfaCodeScreen(
    state: AuthState,
    onIntent: (AuthIntent) -> Unit,
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
            text = stringResource(Res.string.mfa_code_subtitle, state.mfaSentTo),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = state.mfaCode,
            onValueChange = { onIntent(AuthIntent.MfaCodeChanged(it)) },
            label = { Text(stringResource(Res.string.mfa_code_label)) },
            isError = state.mfaCodeError != null,
            supportingText = if (state.mfaCodeError == MfaInputError.IncompleteCode) {
                { Text(stringResource(Res.string.mfa_code_incomplete, state.mfaCodeLength)) }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
                onIntent(AuthIntent.SubmitMfaCode)
            }),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag(MfaTags.CODE_FIELD),
        )

        Spacer(Modifier.height(24.dp))

        if (state.isLoading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = { onIntent(AuthIntent.SubmitMfaCode) },
                modifier = Modifier.fillMaxWidth().testTag(MfaTags.VERIFY_BUTTON),
            ) {
                Text(stringResource(Res.string.mfa_code_verify))
            }
        }

        Spacer(Modifier.height(16.dp))

        TextButton(onClick = { onIntent(AuthIntent.BackToLogin) }) {
            Text(stringResource(Res.string.mfa_back_to_sign_in))
        }
    }
}
