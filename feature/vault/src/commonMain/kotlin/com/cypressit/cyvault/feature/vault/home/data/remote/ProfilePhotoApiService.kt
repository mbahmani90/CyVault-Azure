package com.cypressit.cyvault.feature.vault.home.data.remote

import com.cypressit.cyvault.auth.AuthClient
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess

/**
 * The signed-in user's photo on the CyVault Azure API. The routes carry no user ID: the API
 * finds the user from the access token, so the token decides whose photo this is.
 */
class ProfilePhotoApiService(
    private val client: HttpClient,
    private val authClient: AuthClient,
    config: CyVaultApiConfig,
) {

    private val avatarUrl = "${config.baseUrl}/me/avatar"

    /** The CyVault API app registration's scope in the environment's Entra tenant. */
    private val profileScope = "${config.appIdUri}/Profile.ReadWrite"

    /** The photo as JPEG bytes, or null when the user hasn't set one. */
    suspend fun getPhoto(): ByteArray? {
        val response = client.get(avatarUrl) { bearerAuth(accessToken()) }
        return when (response.status) {
            HttpStatusCode.NotFound -> null
            else -> response.requireSuccess().body()
        }
    }

    /** Uploads a photo; the API re-encodes it as a 512 px JPEG without metadata. */
    suspend fun putPhoto(jpeg: ByteArray) {
        client.put(avatarUrl) {
            bearerAuth(accessToken())
            contentType(ContentType.Image.JPEG)
            setBody(jpeg)
        }.requireSuccess()
    }

    private suspend fun accessToken(): String = authClient.accessToken(listOf(profileScope))

    private fun HttpResponse.requireSuccess(): HttpResponse {
        if (!status.isSuccess()) throw Exception("Profile photo request failed (${status.value})")
        return this
    }
}
