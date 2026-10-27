package com.cypressit.cyvault.feature.vault.vaultlist.presentation.viewmodel

sealed class VaultListEffect {
    data class ShowError(val message: String) : VaultListEffect()
    data object CardDeleted : VaultListEffect()
}
