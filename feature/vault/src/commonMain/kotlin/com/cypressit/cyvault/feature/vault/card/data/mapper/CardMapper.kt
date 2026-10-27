package com.cypressit.cyvault.feature.vault.card.data.mapper

import com.cypressit.cyvault.feature.vault.card.data.remote.dto.CardResponseDto
import com.cypressit.cyvault.feature.vault.card.domain.model.Card
import com.cypressit.cyvault.feature.vault.card.domain.model.CardType

fun CardResponseDto.toDomain(): Card = Card(
    id = id,
    bankName = bankName,
    cardHolderName = cardHolderName,
    cardNumber = cardNumber,
    cvv2 = cvv2,
    expiryDate = expiryDate,
    balance = balance,
    cardType = CardType.entries.firstOrNull { it.name == cardType } ?: CardType.OTHER,
)
