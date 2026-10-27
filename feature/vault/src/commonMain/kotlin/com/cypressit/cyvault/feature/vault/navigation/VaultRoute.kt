package com.cypressit.cyvault.feature.vault.navigation

import kotlinx.serialization.Serializable

/** The vault's root screen: the card list. */
@Serializable
data object VaultListRoute

/** Add a card through open banking. */
@Serializable
data object AddCardRoute
