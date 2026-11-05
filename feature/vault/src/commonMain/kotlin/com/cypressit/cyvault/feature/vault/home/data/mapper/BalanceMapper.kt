package com.cypressit.cyvault.feature.vault.home.data.mapper

import com.cypressit.cyvault.feature.vault.home.data.local.CachedBalance
import com.cypressit.cyvault.feature.vault.home.data.remote.dto.BalanceRequestDto
import com.cypressit.cyvault.feature.vault.home.data.remote.dto.BalanceResponseDto
import com.cypressit.cyvault.feature.vault.home.domain.model.Balance
import com.cypressit.cyvault.feature.vault.home.domain.model.BalanceType
import kotlin.math.abs

fun BalanceResponseDto.toDomain(): Balance = Balance(
    id = id,
    name = name,
    description = description,
    amount = amount,
    currency = currency,
    date = date,
    cardId = cardId.ifEmpty { null },
    // An unknown type, from a newer API, shows by the amount's sign rather than failing the list.
    type = BalanceType.entries.firstOrNull { it.name == type }
        ?: if (amount < 0) BalanceType.Purchase else BalanceType.Income,
    counterparty = counterparty.ifEmpty { null },
)

fun CachedBalance.toDomain(): Balance = Balance(
    id = id,
    name = name,
    description = description,
    amount = amount,
    currency = currency,
    date = date,
    cardId = cardId,
    type = BalanceType.entries.firstOrNull { it.name == type }
        ?: if (amount < 0) BalanceType.Purchase else BalanceType.Income,
    counterparty = counterparty,
)

fun Balance.toCached(): CachedBalance = CachedBalance(
    id = id,
    name = name,
    description = description,
    amount = amount,
    currency = currency,
    date = date,
    cardId = cardId,
    type = type.name,
    counterparty = counterparty,
)

fun Balance.toRequest(): BalanceRequestDto = BalanceRequestDto(
    name = name,
    description = description,
    // Above zero, whatever the sign the caller used: the type gives the direction.
    amount = abs(amount),
    currency = currency,
    date = date,
    cardId = cardId,
    type = type.name,
)
