package com.cypressit.cyvault.feature.vault.home.presentation.viewmodel

sealed class VaultHomeIntent {
    data object LoadCards : VaultHomeIntent()
    data class DeleteCard(val id: String) : VaultHomeIntent()
    data object SignOut : VaultHomeIntent()
    data object OpenDrawer : VaultHomeIntent()
}
