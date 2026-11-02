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
import cyvault.authentication.generated.resources.mfa_phone_invalid
import cyvault.authentication.generated.resources.mfa_phone_label
import cyvault.authentication.generated.resources.mfa_phone_placeholder
import cyvault.authentication.generated.resources.mfa_phone_send_code
import cyvault.authentication.generated.resources.mfa_phone_subtitle
import cyvault.authentication.generated.resources.mfa_phone_title
import org.jetbrains.compose.resources.stringResource

/** The account's first MFA sign-in: the phone number SMS codes will go to. */
@Composable
fun MfaPhoneNumberScreen(
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
        Text(text = stringResource(Res.string.mfa_phone_title), style = MaterialTheme.typography.headlineMedium)

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(Res.string.mfa_phone_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = state.mfaPhoneNumber,
            onValueChange = { onIntent(AuthIntent.MfaPhoneNumberChanged(it)) },
            label = { Text(stringResource(Res.string.mfa_phone_label)) },
            placeholder = { Text(stringResource(Res.string.mfa_phone_placeholder)) },
            isError = state.mfaPhoneNumberError != null,
            supportingText = if (state.mfaPhoneNumberError == MfaInputError.InvalidPhoneNumber) {
                { Text(stringResource(Res.string.mfa_phone_invalid)) }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
                onIntent(AuthIntent.SubmitMfaPhoneNumber)
            }),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag(MfaTags.PHONE_NUMBER_FIELD),
        )

        Spacer(Modifier.height(24.dp))

        if (state.isLoading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = { onIntent(AuthIntent.SubmitMfaPhoneNumber) },
                modifier = Modifier.fillMaxWidth().testTag(MfaTags.SEND_CODE_BUTTON),
            ) {
                Text(stringResource(Res.string.mfa_phone_send_code))
            }
        }

        Spacer(Modifier.height(16.dp))

        TextButton(onClick = { onIntent(AuthIntent.BackToLogin) }) {
            Text(stringResource(Res.string.mfa_back_to_sign_in))
        }
    }
}
