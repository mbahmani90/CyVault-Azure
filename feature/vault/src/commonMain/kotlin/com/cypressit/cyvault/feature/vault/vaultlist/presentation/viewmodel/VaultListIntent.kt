package com.cypressit.cyvault.feature.vault.vaultlist.presentation.viewmodel

sealed class VaultListIntent {
    data object LoadCards : VaultListIntent()
    data class DeleteCard(val id: String) : VaultListIntent()
}
