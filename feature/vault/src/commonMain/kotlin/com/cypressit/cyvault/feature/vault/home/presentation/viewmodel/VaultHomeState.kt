package com.cypressit.cyvault.feature.vault.home.presentation.viewmodel

import com.cypressit.cyvault.feature.vault.card.domain.model.Card
import com.cypressit.cyvault.feature.vault.card.domain.model.CardInput
import com.cypressit.cyvault.feature.vault.home.domain.model.Balance

data class VaultHomeState(
    val isLoading: Boolean = false,
    val cards: List<Card> = emptyList(),
    val error: String? = null,
    val userName: String = "",
    val userEmail: String = "",
    /** JPEG bytes of the user's profile photo, or null to show the initials avatar. */
    val profilePhoto: ByteArray? = null,
    val isUpdatingPhoto: Boolean = false,
    val isAddItemDialogVisible: Boolean = false,
    val balances: List<Balance> = emptyList(),
    /** True while the balances are being fetched, so the list doesn't claim there are none. */
    val isLoadingBalances: Boolean = false,
    /** The balance being edited in the add-item dialog, or null when adding a new one. */
    val editingBalance: Balance? = null,
    val isSavingBalance: Boolean = false,
    /** The user's cards: named on each balance row, and offered in the add-item dialog. */
    val itemCards: List<Card> = emptyList(),
    val isLoadingItemCards: Boolean = false,
    /** The card the item goes on: required to save. */
    val itemCardId: String? = null,
    val isItemCardMenuOpen: Boolean = false,
    /** True in debug builds, where :feature:vault-debug can add sample balances. */
    val canAddSampleBalances: Boolean = false,
    val isAddingSampleBalances: Boolean = false,
    /** The balance waiting for the user to confirm its deletion, or null when nothing is asked. */
    val pendingDeleteBalance: Balance? = null,
    /** IDs of the balances selected in selection mode; empty outside it. */
    val selectedBalanceIds: Set<String> = emptySet(),
    val isDeleteSelectedConfirmVisible: Boolean = false,
    val isDeletingBalances: Boolean = false,
    val isTransferDialogVisible: Boolean = false,
    /** The user's cards to send from; loaded when the transfer dialog opens. */
    val transferCards: List<Card> = emptyList(),
    val isLoadingTransferCards: Boolean = false,
    /** The card the money is sent from, or null until the user has a card. */
    val transferCardId: String? = null,
    val isTransferCardMenuOpen: Boolean = false,
    /** The receiver's phone number, as typed. */
    val transferPhoneNumber: String = "",
    /** The amount, as typed; a comma or a point separates the decimals. */
    val transferAmount: String = "",
    val transferCurrency: String = "EUR",
    val isTransferCurrencyMenuOpen: Boolean = false,
    val isSendingTransfer: Boolean = false,
    /** Why the last Send failed, shown in the dialog; null when it didn't, or the input changed since. */
    val transferError: TransferError? = null,
) {
    val itemCard: Card?
        get() = itemCards.firstOrNull { it.id == itemCardId }

    /**
     * The name shown on [balance]'s row: its card's bank, or the brand when the card has no bank.
     * Null when the balance has no card, or its card is gone or not loaded yet.
     */
    fun cardNameOf(balance: Balance): String? = itemCards
        .firstOrNull { it.id == balance.cardId }
        ?.let { card -> card.bankName.ifBlank { card.brand }.ifBlank { null } }

    val transferCard: Card?
        get() = transferCards.firstOrNull { it.id == transferCardId }

    /** The amount as a number above zero, or null while it isn't one. */
    val transferAmountValue: Double?
        get() = transferAmount.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }

    val isTransferAmountInvalid: Boolean
        get() = transferAmount.isNotBlank() && transferAmountValue == null

    val isTransferPhoneNumberInvalid: Boolean
        get() = transferPhoneNumber.isNotBlank() && CardInput.normalizePhoneNumber(transferPhoneNumber) == null

    /** Send needs a card, a valid phone number and an amount above zero, and no send in flight. */
    val isTransferEnabled: Boolean
        get() = transferCard != null &&
            CardInput.normalizePhoneNumber(transferPhoneNumber) != null &&
            transferAmountValue != null &&
            !isSendingTransfer

    val isSelectionMode: Boolean
        get() = selectedBalanceIds.isNotEmpty()

    val selectedBalanceCount: Int
        get() = selectedBalanceIds.size

    fun isBalanceSelected(id: String): Boolean = id in selectedBalanceIds

    val areAllBalancesSelected: Boolean
        get() = balances.isNotEmpty() && balances.all { it.id in selectedBalanceIds }

    /**
     * The balances added up per currency, since euros and pounds can't be summed together.
     * Each currency appears once, in the order it first appears in [balances].
     */
    val balanceTotals: List<Pair<String, Double>>
        get() = balances
            .groupBy { it.currency }
            .map { (currency, items) -> currency to items.sumOf { it.signedAmount } }
}

/** Why sending money failed. */
enum class TransferError {
    /** No card is linked to the receiver's phone number. */
    NoCardForPhoneNumber,

    /** The API rejected it, such as sending to the card the money comes from. */
    Invalid,

    /** This phone can't approve it: no fingerprint or face unlock, or older than Android 9. */
    SigningUnavailable,

    /** The approval came too late for its challenge: sending again asks for a new one. */
    ChallengeExpired,

    /** Too many failed approvals in a row: the API blocks sending for a while (PSD2). */
    SigningLocked,

    /** Anything else, such as no connection: the same transfer can be retried. */
    Failed,
}
