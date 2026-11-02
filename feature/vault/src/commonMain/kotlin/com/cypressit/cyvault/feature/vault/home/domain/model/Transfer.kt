package com.cypressit.cyvault.feature.vault.home.domain.model

/** The one-time challenge for one transfer: the device signs [payload] exactly as given. */
data class TransferChallenge(val nonce: String, val payload: String)

/** The device's approval of a transfer: its [signature] over the challenge [nonce] names. */
data class TransferApproval(val deviceId: String, val nonce: String, val signature: String)
