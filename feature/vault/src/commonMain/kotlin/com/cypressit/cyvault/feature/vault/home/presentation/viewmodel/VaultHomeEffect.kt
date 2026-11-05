package com.cypressit.cyvault.feature.vault.home.presentation.viewmodel

sealed interface VaultHomeEffect {
    data class ShowError(val message: String) : VaultHomeEffect
    data object CardDeleted : VaultHomeEffect
    data object ProfilePhotoUpdated : VaultHomeEffect
    data object ProfilePhotoUpdateFailed : VaultHomeEffect
    data object BalanceSaved : VaultHomeEffect
    data object BalanceDeleted : VaultHomeEffect
    data class BalancesDeleted(val count: Int) : VaultHomeEffect
    data object BalanceRequestFailed : VaultHomeEffect

    /** Fetch the list again from its first page: only the screen's Paging handle can ask for that. */
    data object RefreshBalances : VaultHomeEffect
    data object TransferCardsLoadFailed : VaultHomeEffect

    /** The money left: [amount] as the user typed it, in [currency], to [phoneNumber] (E.164). */
    data class TransferSent(val amount: String, val currency: String, val phoneNumber: String) : VaultHomeEffect
    data class SampleBalancesAdded(val count: Int) : VaultHomeEffect
    data object SampleBalancesNeedACard : VaultHomeEffect
    data object NavigateToLogin : VaultHomeEffect
    data object NavigateToAddCard : VaultHomeEffect
    data object OpenDrawer : VaultHomeEffect
    data object CloseDrawer : VaultHomeEffect
}
