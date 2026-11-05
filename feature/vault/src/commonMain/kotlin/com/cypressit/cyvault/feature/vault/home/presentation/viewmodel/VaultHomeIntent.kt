package com.cypressit.cyvault.feature.vault.home.presentation.viewmodel

import com.cypressit.cyvault.feature.vault.home.domain.model.Balance
import com.cypressit.cyvault.feature.vault.home.domain.model.PickedPhoto

sealed interface VaultHomeIntent {
    /** Paging's state for the list's first page changed: the screen reports it, as only it can see it. */
    data class BalancesLoadChanged(val isLoading: Boolean, val hasFailed: Boolean) : VaultHomeIntent

    data object LoadCards : VaultHomeIntent

    /** The user pulled the list down: fetch the balances and cards again. */
    data object Refresh : VaultHomeIntent
    data class DeleteCard(val id: String) : VaultHomeIntent
    data object SignOut : VaultHomeIntent
    data object OpenDrawer : VaultHomeIntent

    /** The drawer's "Add card" item: opens the add-card screen. */
    data object AddCardFromDrawer : VaultHomeIntent

    /** The transfer button next to Add on the total balance card: opens the transfer dialog. */
    data object TransferClicked : VaultHomeIntent
    data object TransferDismissed : VaultHomeIntent
    data class TransferPhoneNumberChanged(val value: String) : VaultHomeIntent
    data class TransferAmountChanged(val value: String) : VaultHomeIntent
    data class TransferCardMenuOpenChanged(val isOpen: Boolean) : VaultHomeIntent
    data class TransferCardSelected(val cardId: String) : VaultHomeIntent
    data class TransferCurrencyMenuOpenChanged(val isOpen: Boolean) : VaultHomeIntent
    data class TransferCurrencySelected(val currency: String) : VaultHomeIntent

    /** The dialog's Send button. */
    data object TransferSubmitted : VaultHomeIntent

    data object ShowAddItemDialog : VaultHomeIntent
    data object DismissAddItemDialog : VaultHomeIntent

    data class ItemCardMenuOpenChanged(val isOpen: Boolean) : VaultHomeIntent
    data class ItemCardSelected(val cardId: String) : VaultHomeIntent

    /** Saves the add-item dialog as a balance, on the chosen card: a new one, or the one being edited. */
    data class SaveItem(
        val title: String,
        val description: String,
        val value: Double,
        val currency: String,
        /** ISO 8601: when the purchase or income happened. */
        val date: String,
    ) : VaultHomeIntent

    /** A tap on a balance row: opens it for editing, or in selection mode selects or deselects it. */
    data class ClickBalance(val balance: Balance) : VaultHomeIntent

    /** A long press on a balance row: selects or deselects it; the first one starts selection mode. */
    data class LongClickBalance(val balance: Balance) : VaultHomeIntent

    /** The edit dialog's Delete: asks to confirm deleting the balance being edited. */
    data object RequestDeleteEditingBalance : VaultHomeIntent
    data object ConfirmDeleteBalance : VaultHomeIntent
    data object DismissDeleteBalance : VaultHomeIntent

    data object SelectAllBalances : VaultHomeIntent

    /** The selection bar's close button or system back: leaves selection mode. */
    data object ClearBalanceSelection : VaultHomeIntent

    /** The selection bar's delete: asks to confirm deleting the selected balances. */
    data object RequestDeleteSelectedBalances : VaultHomeIntent
    data object ConfirmDeleteSelectedBalances : VaultHomeIntent
    data object DismissDeleteSelectedBalances : VaultHomeIntent

    /** Debug builds only: saves a batch of random balances. */
    data object AddSampleBalances : VaultHomeIntent

    /** The user picked a new profile photo. */
    data class ChangeProfilePhoto(val photo: PickedPhoto) : VaultHomeIntent
}
