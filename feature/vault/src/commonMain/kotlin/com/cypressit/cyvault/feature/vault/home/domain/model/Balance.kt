package com.cypressit.cyvault.feature.vault.home.domain.model

data class Balance(
    val id: String,
    val name: String,
    val description: String = "",
    val amount: Double,
    val currency: String, // ISO 4217, e.g. EUR
    /** ISO 8601: when the purchase or income happened; null lets the API choose when saving. */
    val date: String? = null,
    /** The card it belongs to; required when saving, null only on balances stored before cards. */
    val cardId: String? = null,
)
