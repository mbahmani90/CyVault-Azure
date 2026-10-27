package com.cypressit.cyvault.feature.vault.home.data.remote

import com.cypressit.cyvault.core.session.SessionManager
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
    private val sessionManager: SessionManager,
) {

    companion object {
        const val AVATAR_URL = "https://func-cyvault-24xpnyg5tfwui.azurewebsites.net/api/me/avatar"

        /** The CyVault API app registration's scope in the Entra External ID tenant. */
        const val PROFILE_SCOPE = "api://61a46c01-e17b-43da-8630-17d521ef888b/Profile.ReadWrite"
    }

    /** The photo as JPEG bytes, or null when the user hasn't set one. */
    suspend fun getPhoto(): ByteArray? {
        val response = client.get(AVATAR_URL) { bearerAuth(accessToken()) }
        return when (response.status) {
            HttpStatusCode.NotFound -> null
            else -> response.requireSuccess().body()
        }
    }

    /** Uploads a photo; the API re-encodes it as a 512 px JPEG without metadata. */
    suspend fun putPhoto(jpeg: ByteArray) {
        client.put(AVATAR_URL) {
            bearerAuth(accessToken())
            contentType(ContentType.Image.JPEG)
            setBody(jpeg)
        }.requireSuccess()
    }

    private suspend fun accessToken(): String = sessionManager.accessToken(listOf(PROFILE_SCOPE))

    private fun HttpResponse.requireSuccess(): HttpResponse {
        if (!status.isSuccess()) throw Exception("Profile photo request failed (${status.value})")
        return this
    }
}
