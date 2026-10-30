package com.cypressit.cyvault.feature.vault.home.presentation.viewmodel

sealed class VaultHomeEffect {
    data class ShowError(val message: String) : VaultHomeEffect()
    data object CardDeleted : VaultHomeEffect()
    data object ProfilePhotoUpdated : VaultHomeEffect()
    data object ProfilePhotoUpdateFailed : VaultHomeEffect()
    data object BalanceSaved : VaultHomeEffect()
    data object BalanceDeleted : VaultHomeEffect()
    data class BalancesDeleted(val count: Int) : VaultHomeEffect()
    data object BalanceRequestFailed : VaultHomeEffect()
    data class SampleBalancesAdded(val count: Int) : VaultHomeEffect()
    data object NavigateToLogin : VaultHomeEffect()
    data object NavigateToAddCard : VaultHomeEffect()
    data object OpenDrawer : VaultHomeEffect()
    data object CloseDrawer : VaultHomeEffect()
}
