package com.cypressit.cyvault.feature.vault.card.domain.repository

import com.cypressit.cyvault.feature.vault.card.domain.model.Card

interface CardRepository {
    suspend fun getCards(): Result<List<Card>>

    /**
     * Adds a card linked to [phoneNumber]. The caller picks [id] once per add, so a retry updates
     * the same card instead of adding a second one. Fails with [PhoneNumberAlreadyLinkedException]
     * when another card already has the phone number.
     */
    suspend fun createCard(id: String, cardNumber: String, phoneNumber: String, bankName: String): Result<Card>

    suspend fun deleteCard(id: String): Result<Unit>
}

/** One phone number links to one card: this one is already linked to another. */
class PhoneNumberAlreadyLinkedException : Exception("This phone number is already linked to a card.")

/** The API rejected the card number or phone number. */
class InvalidCardException : Exception("The card number or phone number isn't valid.")
