package com.cypressit.cyvault.feature.vault.home.presentation.viewmodel

sealed class VaultHomeEffect {
    data object NavigateToLogin : VaultHomeEffect()
    data class ShowError(val message: String) : VaultHomeEffect()
    data object OpenDrawer : VaultHomeEffect()
    data object CloseDrawer : VaultHomeEffect()
    data object NavigateBack : VaultHomeEffect()
}
