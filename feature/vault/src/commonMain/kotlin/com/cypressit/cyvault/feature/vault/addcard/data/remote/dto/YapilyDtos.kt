package com.cypressit.cyvault.feature.vault.addcard.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── Institutions ──────────────────────────────────────────────────────────────

@Serializable
data class YapilyInstitutionsResponseDto(
    @SerialName("data") val data: List<YapilyInstitutionDto>,
)

@Serializable
data class YapilyInstitutionDto(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("countries") val countries: List<YapilyCountryDto> = emptyList(),
    @SerialName("media") val media: List<YapilyMediaDto> = emptyList(),
    @SerialName("features") val features: List<String> = emptyList(),
)

@Serializable
data class YapilyCountryDto(
    @SerialName("countryCode2") val countryCode: String,
    @SerialName("displayName") val displayName: String? = null,
)

@Serializable
data class YapilyMediaDto(
    @SerialName("source") val source: String,
    @SerialName("type") val type: String? = null,
)

// ── Account Authorisation ─────────────────────────────────────────────────────

@Serializable
data class YapilyAccountAuthRequestDto(
    @SerialName("applicationUserId") val applicationUserId: String,
    @SerialName("institutionId") val institutionId: String,
    @SerialName("callback") val callback: String,
)

@Serializable
data class YapilyAccountAuthResponseDto(
    @SerialName("data") val data: YapilyAuthDataDto,
)

@Serializable
data class YapilyAuthDataDto(
    @SerialName("id") val id: String,
    @SerialName("authorisationUrl") val authorisationUrl: String,
    @SerialName("qrCodeUrl") val qrCodeUrl: String? = null,
)

// ── Accounts ──────────────────────────────────────────────────────────────────

@Serializable
data class YapilyAccountsResponseDto(
    @SerialName("data") val data: List<YapilyAccountDto>,
)

@Serializable
data class YapilyAccountDto(
    @SerialName("id") val id: String,
    @SerialName("type") val type: String? = null,
    @SerialName("balance") val balance: Double? = null,
    @SerialName("currency") val currency: String? = null,
    @SerialName("usageType") val usageType: String? = null,
    @SerialName("accountType") val accountType: String? = null,
    @SerialName("nickname") val nickname: String? = null,
    @SerialName("details") val details: String? = null,
    @SerialName("accountNames") val accountNames: List<YapilyAccountNameDto> = emptyList(),
    @SerialName("accountIdentifications") val accountIdentifications: List<YapilyAccountIdentificationDto> = emptyList(),
)

@Serializable
data class YapilyAccountNameDto(
    @SerialName("name") val name: String,
)

@Serializable
data class YapilyAccountIdentificationDto(
    @SerialName("type") val type: String,
    @SerialName("identification") val identification: String,
)
