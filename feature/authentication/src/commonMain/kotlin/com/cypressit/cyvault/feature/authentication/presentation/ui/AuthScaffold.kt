package com.cypressit.cyvault.feature.authentication.presentation.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.cypressit.cyvault.feature.authentication.presentation.viewmodel.AuthEffect
import com.cypressit.cyvault.feature.authentication.presentation.viewmodel.AuthNavEvent
import com.cypressit.cyvault.testtags.LoginTags
import cyvault.authentication.generated.resources.Res
import cyvault.authentication.generated.resources.mfa_failed
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.getString

/**
 * The frame every auth screen sits in: shows its ViewModel's messages in a snackbar and
 * hands its navigation events to the app.
 */
@Composable
internal fun AuthScaffold(
    effects: Flow<AuthEffect>,
    onNavigate: (AuthNavEvent) -> Unit,
    content: @Composable () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(effects, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            effects.collect { effect ->
                when (effect) {
                    is AuthEffect.Navigate -> onNavigate(effect.to)
                    is AuthEffect.ShowError -> snackbarHostState.showSnackbar(effect.message)
                    is AuthEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
                    is AuthEffect.ShowMfaFailed ->
                        snackbarHostState.showSnackbar(effect.message ?: getString(Res.string.mfa_failed))
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState, modifier = Modifier.testTag(LoginTags.SNACKBAR)) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { _ ->
        content()
    }
}
