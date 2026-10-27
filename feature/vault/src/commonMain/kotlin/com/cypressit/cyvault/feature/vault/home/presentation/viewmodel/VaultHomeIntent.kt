package com.cypressit.cyvault.feature.vault.home.presentation.viewmodel

sealed class VaultHomeIntent {
    data object SignOut : VaultHomeIntent()
    data object OpenDrawer : VaultHomeIntent()
    data object NavigateBack : VaultHomeIntent()
}
