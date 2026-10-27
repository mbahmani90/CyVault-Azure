package com.cypressit.cyvault.feature.vault.addcard.domain.model

data class BankAccount(
    val id: String,
    val name: String,
    val ownerName: String?,
    val iban: String?,
    val balance: Double,
    val currency: String,
)
