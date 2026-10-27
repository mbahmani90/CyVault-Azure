package com.cypressit.cyvault.feature.vault.navigation

import kotlinx.serialization.Serializable

/** The vault feature's route in the app's NavHost. */
@Serializable
data object VaultHomeRoute

/** Screens inside the vault's own NavHost. */
internal sealed class VaultRoute {

    @Serializable
    data object List : VaultRoute()

    @Serializable
    data object AddCard : VaultRoute()
}
