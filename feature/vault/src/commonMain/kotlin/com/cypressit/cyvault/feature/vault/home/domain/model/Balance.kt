package com.cypressit.cyvault.feature.vault.home.domain.model

import kotlin.math.abs

data class Balance(
    val id: String,
    val name: String,
    val description: String = "",
    /** Above zero: [type] gives the direction, and [signedAmount] the effect on a total. */
    val amount: Double,
    val currency: String, // ISO 4217, e.g. EUR
    /** ISO 8601: when the purchase or income happened; null lets the API choose when saving. */
    val date: String? = null,
    /** The card it belongs to; required when saving, null only on balances stored before cards. */
    val cardId: String? = null,
    /** Without one, the amount's sign gives it: negative is a purchase, the rest income. */
    val type: BalanceType = if (amount < 0) BalanceType.Purchase else BalanceType.Income,
    /** On a transfer or receive, the other side's phone number (E.164); else null. */
    val counterparty: String? = null,
) {
    /** What it adds to a total: negative for money going out. */
    val signedAmount: Double
        get() = if (type.isOutgoing) -abs(amount) else abs(amount)
}

/**
 * What a balance is. The user adds purchases and income; transfers and receives come only from
 * sending money, and can be deleted but not edited.
 */
enum class BalanceType {
    Purchase,
    Income,
    Transfer,
    Receive,
    ;

    /** Money going out of the card: a purchase or a transfer. */
    val isOutgoing: Boolean
        get() = this == Purchase || this == Transfer

    val isFromTransfer: Boolean
        get() = this == Transfer || this == Receive
}
