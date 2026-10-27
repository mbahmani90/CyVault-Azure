package com.cypressit.cyvault.feature.vault.home.presentation.ui

import androidx.compose.material3.DrawerState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.cypressit.cyvault.feature.vault.home.presentation.viewmodel.VaultHomeEffect
import kotlinx.coroutines.flow.Flow

@Composable
internal fun VaultScaffoldEffect(
    effects: Flow<VaultHomeEffect>,
    drawerState: DrawerState,
    snackBarHostState: SnackbarHostState,
    onSignedOut: () -> Unit,
    onBack: () -> Unit,
) {
    LaunchedEffect(Unit) {
        effects.collect { effect ->
            when (effect) {
                VaultHomeEffect.NavigateToLogin -> onSignedOut()
                is VaultHomeEffect.ShowError -> snackBarHostState.showSnackbar(effect.message)
                VaultHomeEffect.OpenDrawer -> drawerState.open()
                VaultHomeEffect.CloseDrawer -> drawerState.close()
                VaultHomeEffect.NavigateBack -> onBack()
            }
        }
    }
}
