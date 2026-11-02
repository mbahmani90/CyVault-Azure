package com.cypressit.cyvault.feature.vault.device.data.remote

import com.cypressit.cyvault.auth.AuthClient
import com.cypressit.cyvault.feature.vault.device.data.remote.dto.DeviceRequestDto
import com.cypressit.cyvault.feature.vault.device.data.remote.dto.DeviceResponseDto
import com.cypressit.cyvault.feature.vault.home.data.remote.CyVaultApiConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess

/**
 * The signed-in user's enrolled devices on the CyVault Azure API. Like the other routes, they
 * carry no user ID: the API finds the user from the access token.
 */
class DeviceApiService(
    private val client: HttpClient,
    private val authClient: AuthClient,
    config: CyVaultApiConfig,
) {

    private val devicesUrl = "${config.baseUrl}/me/devices"

    /** The device routes share the profile scope of the CyVault API app registration. */
    private val profileScope = "${config.appIdUri}/Profile.ReadWrite"

    /**
     * Enrolls the device holding the private key for this public key; returns it with its ID. The
     * same key again returns the same device. Throws [DeviceKeyRevokedException] when the key
     * belongs to a device the user removed.
     */
    suspend fun enroll(request: DeviceRequestDto): DeviceResponseDto =
        client.post(devicesUrl) {
            bearerAuth(authClient.accessToken(listOf(profileScope)))
            contentType(ContentType.Application.Json)
            setBody(request)
        }.requireSuccess().body()

    private fun HttpResponse.requireSuccess(): HttpResponse {
        if (status == HttpStatusCode.Conflict) throw DeviceKeyRevokedException()
        if (!status.isSuccess()) throw Exception("Device request failed (${status.value})")
        return this
    }
}

/** The key belongs to a device the user removed: it can't be enrolled again, only replaced. */
class DeviceKeyRevokedException : Exception("This device was removed. Create a new key.")
