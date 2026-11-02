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

@Serializable
data class BalanceRequestDto(
    @SerialName("name") val name: String,
    @SerialName("description") val description: String,
    @SerialName("amount") val amount: Double,
    @SerialName("currency") val currency: String,
    /** ISO 8601, or null to let the API date a new balance now and keep an edited one's date. */
    @SerialName("date") val date: String? = null,
    /** Required: one of the user's cards. */
    @SerialName("cardId") val cardId: String? = null,
    /** Purchase or Income: transfers are only made with [TransferRequestDto]. */
    @SerialName("type") val type: String? = null,
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
