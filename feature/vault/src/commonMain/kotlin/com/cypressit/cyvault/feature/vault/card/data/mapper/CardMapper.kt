package com.cypressit.cyvault.feature.vault.card.data.mapper

import com.cypressit.cyvault.feature.vault.card.data.remote.dto.CardResponseDto
import com.cypressit.cyvault.feature.vault.card.domain.model.Card

fun CardResponseDto.toDomain(): Card = Card(
    id = id,
    last4 = last4,
    phoneNumber = phoneNumber,
    createdAt = createdAt,
)
