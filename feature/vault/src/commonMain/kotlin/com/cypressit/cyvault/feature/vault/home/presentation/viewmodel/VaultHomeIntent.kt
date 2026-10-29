package com.cypressit.cyvault.feature.vault.home.presentation.viewmodel

import com.cypressit.cyvault.feature.vault.home.domain.model.Balance
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

    /** Saves the add-item dialog as a balance: a new one, or the one being edited. */
    data class SaveItem(
        val title: String,
        val description: String,
        val value: Double,
        val currency: String,
        /** ISO 8601: when the purchase or income happened. */
        val date: String,
    ) : VaultHomeIntent()

    /** Opens the add-item dialog on an existing balance. */
    data class EditBalance(val balance: Balance) : VaultHomeIntent()
    data class DeleteBalance(val id: String) : VaultHomeIntent()

    /** Debug builds only: saves a batch of random balances. */
    data object AddSampleBalances : VaultHomeIntent()

    /** The user picked a new profile photo. */
    data class ChangeProfilePhoto(val photo: PickedPhoto) : VaultHomeIntent()
}
