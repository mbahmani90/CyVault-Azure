package com.cypressit.cyvault.feature.vault.addcard.domain.model

data class Institution(
    val id: String,
    val name: String,
    val bic: String?,
    val logo: String?,
    val countries: List<String>,
)
