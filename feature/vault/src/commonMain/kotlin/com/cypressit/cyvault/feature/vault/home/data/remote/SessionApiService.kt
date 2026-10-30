package com.cypressit.cyvault.feature.vault.home.data.remote

import com.cypressit.cyvault.auth.AuthClient
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.http.isSuccess

/**
 * Server-side sign-out on the CyVault Azure API. The API revokes the user's Entra refresh tokens
 * on every device, so a token copied off this phone stops working too.
 */
class SessionApiService(
    private val client: HttpClient,
    private val authClient: AuthClient,
    config: CyVaultApiConfig,
) {

    private val signOutUrl = "${config.baseUrl}/me/signout"

    /** Any CyVault API scope will do: the API only needs the token's user. */
    private val profileScope = "${config.appIdUri}/Profile.ReadWrite"

    /** Must run before the local sign-out: it needs a token for the API. */
    suspend fun revokeSessions() {
        val response = client.post(signOutUrl) {
            bearerAuth(authClient.accessToken(listOf(profileScope)))
        }
        if (!response.status.isSuccess()) throw Exception("Sign-out request failed (${response.status.value})")
    }
}
