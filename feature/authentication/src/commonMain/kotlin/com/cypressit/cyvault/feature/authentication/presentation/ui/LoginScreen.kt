package com.cypressit.cyvault.feature.authentication.presentation.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import cyvault.authentication.generated.resources.Res
import cyvault.authentication.generated.resources.cyvault_logo
import cyvault.authentication.generated.resources.login_email_label
import cyvault.authentication.generated.resources.login_forgot_password
import cyvault.authentication.generated.resources.login_logo_description
import cyvault.authentication.generated.resources.login_password_hide
import cyvault.authentication.generated.resources.login_password_label
import cyvault.authentication.generated.resources.login_password_show
import cyvault.authentication.generated.resources.login_register_link
import cyvault.authentication.generated.resources.login_sign_in_button
import com.cypressit.cyvault.feature.authentication.presentation.viewmodel.AuthIntent
import com.cypressit.cyvault.feature.authentication.presentation.viewmodel.AuthState
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun LoginScreen(
    state: AuthState,
    onIntent: (AuthIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Logo pinned to the top; the card centred on the whole screen.
    Box(
        modifier = modifier
            .statusBarsPadding()
            .padding(horizontal = 24.dp)
            .imePadding(),
    ) {
        Image(
            painter = painterResource(Res.drawable.cyvault_logo),
            contentDescription = stringResource(Res.string.login_logo_description),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp)
                .size(64.dp),
        )

        Card(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(),
        ) {
            LoginForm(
                state = state,
                onIntent = onIntent,
                modifier = Modifier.padding(24.dp),
            )
        }
    }
}

/** The sign-in fields and actions, shown inside the login card. */
@Composable
private fun LoginForm(
    state: AuthState,
    onIntent: (AuthIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    var passwordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OutlinedTextField(
            value = state.email,
            onValueChange = { onIntent(AuthIntent.EmailChanged(it)) },
            label = { Text(stringResource(Res.string.login_email_label)) },
            isError = state.emailError != null,
            supportingText = state.emailError?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = state.password,
            onValueChange = { onIntent(AuthIntent.PasswordChanged(it)) },
            label = { Text(stringResource(Res.string.login_password_label)) },
            isError = state.passwordError != null,
            supportingText = state.passwordError?.let { { Text(it) } },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { passwordVisible = !passwordVisible }) {
                    Text(
                        text = stringResource(if (passwordVisible) Res.string.login_password_hide else Res.string.login_password_show),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
                onIntent(AuthIntent.SubmitLogin)
            }),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(24.dp))

        if (state.isLoading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = { onIntent(AuthIntent.SubmitLogin) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.login_sign_in_button))
            }
        }

        TextButton(onClick = { onIntent(AuthIntent.ShowForgotPassword) }) {
            Text(stringResource(Res.string.login_forgot_password))
        }

        Spacer(Modifier.height(8.dp))

        TextButton(onClick = { onIntent(AuthIntent.ToggleMode) }) {
            Text(stringResource(Res.string.login_register_link))
        }

        Spacer(Modifier.height(16.dp))

        HorizontalDivider()

        Spacer(Modifier.height(16.dp))

        OutlinedButton(
            onClick = { onIntent(AuthIntent.SignInWithGoogle) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isLoading,
        ) {
            Text("Continue with Google")
        }
    }
}
