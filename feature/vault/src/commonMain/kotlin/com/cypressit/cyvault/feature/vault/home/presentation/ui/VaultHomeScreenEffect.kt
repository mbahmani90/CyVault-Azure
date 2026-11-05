package com.cypressit.cyvault.feature.vault.home.presentation.ui

import androidx.compose.material3.DrawerState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.cypressit.cyvault.feature.vault.home.presentation.viewmodel.VaultHomeEffect
import cyvault.vault.generated.resources.Res
import cyvault.vault.generated.resources.transfer_cards_load_failed
import cyvault.vault.generated.resources.transfer_sent
import cyvault.vault.generated.resources.vault_home_add_item_no_cards
import cyvault.vault.generated.resources.vault_home_balance_deleted
import cyvault.vault.generated.resources.vault_home_balance_request_failed
import cyvault.vault.generated.resources.vault_home_balance_saved
import cyvault.vault.generated.resources.vault_home_balances_deleted
import cyvault.vault.generated.resources.vault_home_photo_update_failed
import cyvault.vault.generated.resources.vault_home_photo_updated
import cyvault.vault.generated.resources.vault_home_sample_balances_added
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString

@Composable
internal fun VaultHomeScreenEffect(
    effects: Flow<VaultHomeEffect>,
    drawerState: DrawerState,
    snackBarHostState: SnackbarHostState,
    onSignedOut: () -> Unit,
    onAddCard: () -> Unit,
    onRefreshBalances: () -> Unit,
) {
    LaunchedEffect(Unit) {
        effects.collect { effect ->
            when (effect) {
                VaultHomeEffect.NavigateToLogin -> onSignedOut()
                VaultHomeEffect.NavigateToAddCard -> onAddCard()
                VaultHomeEffect.RefreshBalances -> onRefreshBalances()
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
                is VaultHomeEffect.BalancesDeleted -> snackBarHostState.showSnackbar(
                    getPluralString(Res.plurals.vault_home_balances_deleted, effect.count, effect.count),
                )
                VaultHomeEffect.BalanceRequestFailed ->
                    snackBarHostState.showSnackbar(getString(Res.string.vault_home_balance_request_failed))
                VaultHomeEffect.TransferCardsLoadFailed ->
                    snackBarHostState.showSnackbar(getString(Res.string.transfer_cards_load_failed))
                is VaultHomeEffect.TransferSent -> snackBarHostState.showSnackbar(
                    getString(Res.string.transfer_sent, effect.amount, effect.currency, effect.phoneNumber),
                )
                is VaultHomeEffect.SampleBalancesAdded ->
                    snackBarHostState.showSnackbar(getString(Res.string.vault_home_sample_balances_added, effect.count))
                VaultHomeEffect.SampleBalancesNeedACard ->
                    snackBarHostState.showSnackbar(getString(Res.string.vault_home_add_item_no_cards))
                VaultHomeEffect.OpenDrawer -> drawerState.open()
                VaultHomeEffect.CloseDrawer -> drawerState.close()
            }
        }
    }
}
