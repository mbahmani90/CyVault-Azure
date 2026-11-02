package com.cypressit.cyvault.feature.vault.card.domain.repository

import com.cypressit.cyvault.feature.vault.card.domain.model.Card
import kotlinx.coroutines.flow.StateFlow

/**
 * The user's cards, kept in memory so every screen shares one list: [getCards], [createCard] and
 * [deleteCard] update [cards], so a card added on one screen shows on the others at once.
 */
interface CardRepository {
    /** The last list fetched, with every add and delete since; empty until the first fetch. */
    val cards: StateFlow<List<Card>>

    /** Fetches the cards from the API and publishes them to [cards]. */
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
