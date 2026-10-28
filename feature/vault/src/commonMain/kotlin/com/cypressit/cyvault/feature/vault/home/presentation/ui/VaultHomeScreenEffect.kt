package com.cypressit.cyvault.feature.vault.home.presentation.ui

import androidx.compose.material3.DrawerState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.cypressit.cyvault.feature.vault.home.presentation.viewmodel.VaultHomeEffect
import cyvault.vault.generated.resources.Res
import cyvault.vault.generated.resources.vault_home_balance_deleted
import cyvault.vault.generated.resources.vault_home_balance_request_failed
import cyvault.vault.generated.resources.vault_home_balance_saved
import cyvault.vault.generated.resources.vault_home_photo_update_failed
import cyvault.vault.generated.resources.vault_home_photo_updated
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.getString

@Composable
internal fun VaultHomeScreenEffect(
    effects: Flow<VaultHomeEffect>,
    drawerState: DrawerState,
    snackBarHostState: SnackbarHostState,
    onSignedOut: () -> Unit,
    onAddCard: () -> Unit,
) {
    LaunchedEffect(Unit) {
        effects.collect { effect ->
            when (effect) {
                VaultHomeEffect.NavigateToLogin -> onSignedOut()
                VaultHomeEffect.NavigateToAddCard -> onAddCard()
                is VaultHomeEffect.ShowError -> snackBarHostState.showSnackbar(effect.message)
                VaultHomeEffect.CardDeleted -> snackBarHostState.showSnackbar("Card deleted")
                VaultHomeEffect.ProfilePhotoUpdated ->
                    snackBarHostState.showSnackbar(getString(Res.string.vault_home_photo_updated))
                VaultHomeEffect.ProfilePhotoUpdateFailed ->
                    snackBarHostState.showSnackbar(getString(Res.string.vault_home_photo_update_failed))
                VaultHomeEffect.BalanceSaved ->
                    snackBarHostState.showSnackbar(getString(Res.string.vault_home_balance_saved))
                VaultHomeEffect.BalanceDeleted ->
                    snackBarHostState.showSnackbar(getString(Res.string.vault_home_balance_deleted))
                VaultHomeEffect.BalanceRequestFailed ->
                    snackBarHostState.showSnackbar(getString(Res.string.vault_home_balance_request_failed))
                VaultHomeEffect.OpenDrawer -> drawerState.open()
                VaultHomeEffect.CloseDrawer -> drawerState.close()
            }
        }
    }
}
