package com.cypressit.cyvault.feature.vault.addcard.data.mapper

import com.cypressit.cyvault.feature.vault.addcard.data.remote.dto.YapilyAccountDto
import com.cypressit.cyvault.feature.vault.addcard.data.remote.dto.YapilyInstitutionDto
import com.cypressit.cyvault.feature.vault.addcard.domain.model.BankAccount
import com.cypressit.cyvault.feature.vault.addcard.domain.model.Institution

fun YapilyInstitutionDto.toDomain(): Institution = Institution(
    id = id,
    name = name,
    bic = null,
    logo = media.firstOrNull { it.type == "icon" }?.source
        ?: media.firstOrNull()?.source,
    countries = countries.map { it.countryCode },
)

fun YapilyAccountDto.toDomain(): BankAccount = BankAccount(
    id = id,
    name = nickname ?: accountNames.firstOrNull()?.name ?: type ?: "Account",
    ownerName = accountNames.firstOrNull()?.name,
    iban = accountIdentifications.firstOrNull { it.type == "IBAN" }?.identification,
    balance = balance ?: 0.0,
    currency = currency ?: "EUR",
)
