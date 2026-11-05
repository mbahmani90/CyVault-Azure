package com.cypressit.cyvault.feature.vault.home.data.mapper

import com.cypressit.cyvault.feature.vault.home.data.remote.dto.BalanceResponseDto
import com.cypressit.cyvault.feature.vault.home.domain.model.Balance
import com.cypressit.cyvault.feature.vault.home.domain.model.BalanceType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BalanceMapperTest {

    private fun response(cardId: String = "card-1", type: String = "Purchase", amount: Double = 12.5) =
        BalanceResponseDto(
            id = "b1",
            name = "Lidl",
            amount = amount,
            currency = "EUR",
            cardId = cardId,
            type = type,
        )

    @Test
    fun toDomain_keepsTheCard() {
        assertEquals("card-1", response(cardId = "card-1").toDomain().cardId)
    }

    @Test
    fun toDomain_aBalanceStoredBeforeCardsHasNoCard() {
        assertNull(response(cardId = "").toDomain().cardId)
    }

    @Test
    fun toRequest_sendsTheCard() {
        val balance = Balance(id = "b1", name = "Lidl", amount = -12.5, currency = "EUR", cardId = "card-1")

        assertEquals("card-1", balance.toRequest().cardId)
    }

    @Test
    fun toDomain_readsEveryType() {
        BalanceType.entries.forEach { type ->
            assertEquals(type, response(type = type.name).toDomain().type)
        }
    }

    @Test
    fun toDomain_aTransferKeepsItsCounterparty() {
        val transfer = response(type = "Transfer").copy(counterparty = "+351912345678").toDomain()

        assertEquals("+351912345678", transfer.counterparty)
        assertEquals(-12.5, transfer.signedAmount)
    }

    @Test
    fun toDomain_anUnknownTypeGoesByTheSign() {
        assertEquals(BalanceType.Purchase, response(type = "", amount = -3.0).toDomain().type)
        assertEquals(BalanceType.Income, response(type = "Refund", amount = 3.0).toDomain().type)
    }

    @Test
    fun toRequest_sendsAPositiveAmountAndTheType() {
        val purchase = Balance(id = "b1", name = "Lidl", amount = -12.5, currency = "EUR", cardId = "card-1")

        val request = purchase.toRequest()

        assertEquals(12.5, request.amount)
        assertEquals("Purchase", request.type)
    }

    @Test
    fun cached_keepsEveryField() {
        val balance = Balance(
            id = "b1",
            name = "To Ana",
            description = "Dinner",
            amount = 20.5,
            currency = "EUR",
            date = "2026-10-28T09:00:00Z",
            cardId = "card-1",
            type = BalanceType.Transfer,
            counterparty = "+351912345678",
        )

        assertEquals(balance, balance.toCached().toDomain())
    }

    @Test
    fun cached_anUnknownTypeGoesByTheSign() {
        val cached = Balance(id = "b1", name = "", amount = -5.0, currency = "EUR").toCached().copy(type = "New")

        assertEquals(BalanceType.Purchase, cached.toDomain().type)
    }

    @Test
    fun signedAmount_isNegativeForMoneyGoingOut() {
        fun signed(type: BalanceType) =
            Balance(id = "b1", name = "", amount = 10.0, currency = "EUR", type = type).signedAmount

        assertEquals(-10.0, signed(BalanceType.Purchase))
        assertEquals(10.0, signed(BalanceType.Income))
        assertEquals(-10.0, signed(BalanceType.Transfer))
        assertEquals(10.0, signed(BalanceType.Receive))
    }
}
