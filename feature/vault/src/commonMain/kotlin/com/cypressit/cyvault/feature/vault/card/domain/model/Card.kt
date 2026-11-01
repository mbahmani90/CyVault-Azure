package com.cypressit.cyvault.feature.vault.card.domain.model

/** A card linked to a phone number. The API keeps only the last four digits, never the full number. */
data class Card(
    val id: String,
    val last4: String,
    /** Visa, Mastercard, Maestro or Other; empty for cards added before brands were stored. */
    val brand: String,
    /** The bank the user named for the card; empty when they didn't name one. */
    val bankName: String,
    /** E.164, such as +351912345678. */
    val phoneNumber: String,
    /** ISO 8601: when the card was added. */
    val createdAt: String,
)
