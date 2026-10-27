package com.cypressit.cyvault.navigation

import kotlinx.serialization.Serializable

/** Every route in the app. Features don't know these exist; only App.kt uses them. */

@Serializable
data object AuthRoute

@Serializable
data object VaultListRoute

@Serializable
data object AddCardRoute
