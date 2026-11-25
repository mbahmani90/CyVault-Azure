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
import com.cypressit.cyvault.feature.authentication.presentation.viewmodel.MfaInputError
import com.cypressit.cyvault.feature.authentication.presentation.viewmodel.MfaPhoneNumberIntent
import com.cypressit.cyvault.feature.authentication.presentation.viewmodel.MfaPhoneNumberState
import com.cypressit.cyvault.feature.authentication.presentation.viewmodel.MfaPhoneNumberViewModel
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
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MfaPhoneNumberScreen(
    onNavigate: (AuthNavEvent) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MfaPhoneNumberViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AuthScaffold(effects = viewModel.effect, onNavigate = onNavigate) {
        MfaPhoneNumberScreen(state = state, onIntent = viewModel::onIntent, modifier = modifier.fillMaxSize())
    }
}

/** The account's first MFA sign-in: the phone number SMS codes will go to. */
@Composable
internal fun MfaPhoneNumberScreen(
    state: MfaPhoneNumberState,
    onIntent: (MfaPhoneNumberIntent) -> Unit,
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
            value = state.phoneNumber,
            onValueChange = { onIntent(MfaPhoneNumberIntent.PhoneNumberChanged(it)) },
            label = { Text(stringResource(Res.string.mfa_phone_label)) },
            placeholder = { Text(stringResource(Res.string.mfa_phone_placeholder)) },
            isError = state.phoneNumberError != null,
            supportingText = if (state.phoneNumberError == MfaInputError.InvalidPhoneNumber) {
                { Text(stringResource(Res.string.mfa_phone_invalid)) }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
                onIntent(MfaPhoneNumberIntent.SubmitPhoneNumber)
            }),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag(MfaTags.PHONE_NUMBER_FIELD),
        )

        Spacer(Modifier.height(24.dp))

        if (state.isLoading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = { onIntent(MfaPhoneNumberIntent.SubmitPhoneNumber) },
                modifier = Modifier.fillMaxWidth().testTag(MfaTags.SEND_CODE_BUTTON),
            ) {
                Text(stringResource(Res.string.mfa_phone_send_code))
            }
        }

        Spacer(Modifier.height(16.dp))

        TextButton(onClick = { onIntent(MfaPhoneNumberIntent.BackToLogin) }) {
            Text(stringResource(Res.string.mfa_back_to_sign_in))
        }
    }
}
