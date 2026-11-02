package com.cypressit.cyvault.feature.vault.home.data.mapper

import com.cypressit.cyvault.feature.vault.home.data.remote.dto.BalanceResponseDto
import com.cypressit.cyvault.feature.vault.home.domain.model.Balance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BalanceMapperTest {

    private fun response(cardId: String) = BalanceResponseDto(
        id = "b1",
        name = "Lidl",
        amount = -12.5,
        currency = "EUR",
        cardId = cardId,
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
}
