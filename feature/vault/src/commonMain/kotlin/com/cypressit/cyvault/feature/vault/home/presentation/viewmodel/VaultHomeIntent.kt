package com.cypressit.cyvault.feature.vault.home.presentation.viewmodel

import com.cypressit.cyvault.feature.vault.home.domain.model.PickedPhoto

sealed class VaultHomeIntent {
    data object LoadCards : VaultHomeIntent()
    data class DeleteCard(val id: String) : VaultHomeIntent()
    data object SignOut : VaultHomeIntent()
    data object OpenDrawer : VaultHomeIntent()

    /** The drawer's "Add card" item: opens the add-card screen. */
    data object AddCardFromDrawer : VaultHomeIntent()

    data object ShowAddItemDialog : VaultHomeIntent()
    data object DismissAddItemDialog : VaultHomeIntent()
    data class SaveItem(val title: String, val description: String, val value: String) : VaultHomeIntent()

    /** The user picked a new profile photo. */
    data class ChangeProfilePhoto(val photo: PickedPhoto) : VaultHomeIntent()
}
