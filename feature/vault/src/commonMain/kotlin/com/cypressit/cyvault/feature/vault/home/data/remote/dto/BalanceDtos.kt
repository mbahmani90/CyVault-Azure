package com.cypressit.cyvault.feature.vault.home.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BalanceResponseDto(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("description") val description: String = "",
    @SerialName("amount") val amount: Double,
    @SerialName("currency") val currency: String,
    /** ISO 8601: when the purchase or income happened. */
    @SerialName("date") val date: String? = null,
    @SerialName("updatedAt") val updatedAt: String? = null,
    /** Empty on balances stored before cards. */
    @SerialName("cardId") val cardId: String = "",
    /** Purchase, Income, Transfer or Receive; [amount] is always above zero. */
    @SerialName("type") val type: String = "",
    /** On a transfer or receive, the other side's phone number; else empty. */
    @SerialName("counterparty") val counterparty: String = "",
)

/** One page of balances, newest first; a null token means it's the last. */
@Serializable
data class BalancePageDto(
    @SerialName("items") val items: List<BalanceResponseDto>,
    @SerialName("continuationToken") val continuationToken: String? = null,
)

@Serializable
data class BalanceTotalsDto(
    /** ISO 8601: when the user's oldest balance happened; null when they have none. */
    @SerialName("oldestDate") val oldestDate: String? = null,
    @SerialName("totals") val totals: List<BalanceTotalDto> = emptyList(),
)

@Serializable
data class BalanceTotalDto(
    @SerialName("currency") val currency: String,
    @SerialName("total") val total: Double,
)

@Serializable
data class BalanceRequestDto(
    @SerialName("name") val name: String,
    @SerialName("description") val description: String,
    @SerialName("amount") val amount: Double,
    @SerialName("currency") val currency: String,
    /** ISO 8601. Required when creating; without it, an edit keeps the balance's date. */
    @SerialName("date") val date: String? = null,
    /** Required: one of the user's cards. */
    @SerialName("cardId") val cardId: String? = null,
    /** Purchase or Income: transfers are only made with [TransferRequestDto]. */
    @SerialName("type") val type: String? = null,
    /** Required when creating: picked once per new balance, so a retry reaches the same row. */
    @SerialName("id") val id: String? = null,
)

/** Sends money from one of the user's cards to the card linked to [phoneNumber]. */
@Serializable
data class TransferRequestDto(
    /** Picked once per send, so a retry repeats the same transfer instead of sending twice. */
    @SerialName("transferId") val transferId: String,
    @SerialName("cardId") val cardId: String,
    @SerialName("phoneNumber") val phoneNumber: String,
    /** Above zero. */
    @SerialName("amount") val amount: Double,
    @SerialName("currency") val currency: String,
    /** The enrolled device whose key signed [nonce]'s challenge; null when asking for the challenge. */
    @SerialName("deviceId") val deviceId: String? = null,
    @SerialName("nonce") val nonce: String? = null,
    /** Base64 DER ECDSA signature over the challenge's payload. */
    @SerialName("signature") val signature: String? = null,
)

/** The one-time challenge the device signs to approve one transfer. */
@Serializable
data class TransferChallengeDto(
    @SerialName("nonce") val nonce: String,
    /** The exact text to sign as UTF-8: the nonce and the transfer's details. */
    @SerialName("payload") val payload: String,
    /** ISO 8601. */
    @SerialName("expiresAt") val expiresAt: String,
)
