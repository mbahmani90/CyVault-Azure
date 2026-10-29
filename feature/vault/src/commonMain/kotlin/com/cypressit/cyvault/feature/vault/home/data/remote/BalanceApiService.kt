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
    config: CyVaultApiConfig,
) {

    private val balancesUrl = "${config.baseUrl}/me/balances"

    /** The CyVault API app registration's balance scope in the environment's Entra tenant. */
    private val balanceScope = "${config.appIdUri}/Balance.ReadWrite"

    suspend fun getBalances(): List<BalanceResponseDto> =
        client.get(balancesUrl) { bearerAuth(accessToken()) }.requireSuccess().body()

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

    private fun urlFor(id: String) = "$balancesUrl/${id.encodeURLPathPart()}"

    private suspend fun accessToken(): String = authClient.accessToken(listOf(balanceScope))

    private fun HttpResponse.requireSuccess(): HttpResponse {
        if (!status.isSuccess()) throw Exception("Balance request failed (${status.value})")
        return this
    }
}
