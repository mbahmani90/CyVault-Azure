package com.cypressit.cyvault.feature.vault.home.data.mapper

import com.cypressit.cyvault.feature.vault.home.data.remote.dto.BalanceRequestDto
import com.cypressit.cyvault.feature.vault.home.data.remote.dto.BalanceResponseDto
import com.cypressit.cyvault.feature.vault.home.domain.model.Balance

fun BalanceResponseDto.toDomain(): Balance = Balance(
    id = id,
    name = name,
    description = description,
    amount = amount,
    currency = currency,
    date = date,
)

fun Balance.toRequest(): BalanceRequestDto = BalanceRequestDto(
    name = name,
    description = description,
    amount = amount,
    currency = currency,
    date = date,
)
