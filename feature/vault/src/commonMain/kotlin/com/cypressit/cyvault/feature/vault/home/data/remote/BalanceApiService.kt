package com.cypressit.cyvault.feature.vault.home.data.remote

import com.cypressit.cyvault.auth.AuthClient
import com.cypressit.cyvault.feature.vault.home.data.remote.dto.BalanceRequestDto
import com.cypressit.cyvault.feature.vault.home.data.remote.dto.BalanceResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess

/**
 * The signed-in user's balances on the CyVault Azure API. Like the photo routes, the routes carry
 * no user ID: the API finds the user from the access token.
 */
class BalanceApiService(
    private val client: HttpClient,
    private val authClient: AuthClient,
) {

    companion object {
        const val BALANCES_URL = "https://func-cyvault-24xpnyg5tfwui.azurewebsites.net/api/me/balances"

        /** The CyVault API app registration's balance scope in the Entra External ID tenant. */
        const val BALANCE_SCOPE = "api://61a46c01-e17b-43da-8630-17d521ef888b/Balance.ReadWrite"
    }

    suspend fun getBalances(): List<BalanceResponseDto> =
        client.get(BALANCES_URL) { bearerAuth(accessToken()) }.requireSuccess().body()

    /** Creates the balance, or replaces it when [id] already exists; returns it as stored. */
    suspend fun putBalance(id: String, request: BalanceRequestDto): BalanceResponseDto =
        client.put(urlFor(id)) {
            bearerAuth(accessToken())
            contentType(ContentType.Application.Json)
            setBody(request)
        }.requireSuccess().body()

    suspend fun deleteBalance(id: String) {
        client.delete(urlFor(id)) { bearerAuth(accessToken()) }.requireSuccess()
    }

    private fun urlFor(id: String) = "$BALANCES_URL/${id.encodeURLPathPart()}"

    private suspend fun accessToken(): String = authClient.accessToken(listOf(BALANCE_SCOPE))

    private fun HttpResponse.requireSuccess(): HttpResponse {
        if (!status.isSuccess()) throw Exception("Balance request failed (${status.value})")
        return this
    }
}
