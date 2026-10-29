package com.cypressit.cyvault.feature.vault.home.data.remote

/**
 * Which CyVault Azure API environment the app talks to. Release builds and iOS use [Production];
 * Android debug builds provide the debug environment's through Koin, from androidApp's src/debug.
 * The values come from `azd env get-values` in azure-backend/.
 */
data class CyVaultApiConfig(
    /** The Function App's API root, without a trailing slash: API_BASE_URL. */
    val baseUrl: String,
    /** The CyVault API app registration's Application ID URI: api://<ENTRA_API_CLIENT_ID>. */
    val appIdUri: String,
) {
    companion object {
        /** azd environment "dev", in the cyvault.onmicrosoft.com tenant. */
        val Production = CyVaultApiConfig(
            baseUrl = "https://func-cyvault-24xpnyg5tfwui.azurewebsites.net/api",
            appIdUri = "api://61a46c01-e17b-43da-8630-17d521ef888b",
        )
    }
}
