package com.cypressit.cyvault.feature.vault.home.domain.model

/** The signed-in user, as the vault shows it. */
data class Account(
    /** Entra object ID: names the user's cached profile photo on the device. */
    val id: String,
    val name: String,
    val email: String,
)
