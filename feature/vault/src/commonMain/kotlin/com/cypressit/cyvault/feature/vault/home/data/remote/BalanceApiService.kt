package com.cypressit.cyvault.feature.vault.home.data.remote

import com.cypressit.cyvault.auth.AuthClient
import com.cypressit.cyvault.feature.vault.home.data.remote.dto.BalancePageDto
import com.cypressit.cyvault.feature.vault.home.data.remote.dto.BalanceRequestDto
import com.cypressit.cyvault.feature.vault.home.data.remote.dto.BalanceResponseDto
import com.cypressit.cyvault.feature.vault.home.data.remote.dto.BalanceTotalsDto
import com.cypressit.cyvault.feature.vault.home.data.remote.dto.TransferChallengeDto
import com.cypressit.cyvault.feature.vault.home.data.remote.dto.TransferRequestDto
import com.cypressit.cyvault.feature.vault.home.domain.repository.InvalidTransferException
import com.cypressit.cyvault.feature.vault.home.domain.repository.NoCardForPhoneNumberException
import com.cypressit.cyvault.feature.vault.home.domain.repository.SigningLockedException
import com.cypressit.cyvault.feature.vault.home.domain.repository.TransferChallengeExpiredException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
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
    private val totalsUrl = "${config.baseUrl}/me/balance-totals"
    private val transfersUrl = "${config.baseUrl}/me/transfers"
    private val challengeUrl = "${config.baseUrl}/me/transfers/challenge"

    /** The CyVault API app registration's balance scope in the environment's Entra tenant. */
    private val balanceScope = "${config.appIdUri}/Balance.ReadWrite"

    /** One page, newest first. [continuationToken] is the previous page's, or null for the first. */
    suspend fun getBalancesPage(pageSize: Int, continuationToken: String?): BalancePageDto =
        client.get(balancesUrl) {
            bearerAuth(accessToken())
            parameter("pageSize", pageSize)
            continuationToken?.let { parameter("continuationToken", it) }
        }.requireSuccess().body()

    /** What all the user's balances add up to per currency, and when the oldest one happened. */
    suspend fun getTotals(): BalanceTotalsDto =
        client.get(totalsUrl) { bearerAuth(accessToken()) }.requireSuccess().body()

    /**
     * Creates the balance, sent with its ID and date; returns it as stored, under the ID the API
     * built from them. Sending the same ones again replaces the row instead of adding another.
     */
    suspend fun postBalance(request: BalanceRequestDto): BalanceResponseDto =
        client.post(balancesUrl) {
            bearerAuth(accessToken())
            contentType(ContentType.Application.Json)
            setBody(request)
        }.requireSuccess().body()

    /** Replaces the balance [id]; returns it as stored, under a new ID when its date changed. */
    suspend fun putBalance(id: String, request: BalanceRequestDto): BalanceResponseDto =
        client.put(urlFor(id)) {
            bearerAuth(accessToken())
            contentType(ContentType.Application.Json)
            setBody(request)
        }.requireSuccess().body()

    suspend fun deleteBalance(id: String) {
        client.delete(urlFor(id)) { bearerAuth(accessToken()) }.requireSuccess()
    }

    /**
     * Asks for the one-time challenge that approves this transfer, sent without the signing fields.
     * The API checks the transfer first, so it fails like [postTransfer] does.
     */
    suspend fun postTransferChallenge(request: TransferRequestDto): TransferChallengeDto =
        client.post(challengeUrl) {
            bearerAuth(accessToken())
            contentType(ContentType.Application.Json)
            setBody(request)
        }.requireTransferSuccess().body()

    /**
     * Sends money, with the device's signature over its challenge; returns the sender's Transfer
     * balance. Fails with [NoCardForPhoneNumberException] when no card is linked to the phone
     * number, [InvalidTransferException] when the API rejects the transfer (such as sending to the
     * card the money comes from), [TransferChallengeExpiredException] when the challenge expired,
     * and [SigningLockedException] after too many wrong signatures.
     */
    suspend fun postTransfer(request: TransferRequestDto): BalanceResponseDto =
        client.post(transfersUrl) {
            bearerAuth(accessToken())
            contentType(ContentType.Application.Json)
            setBody(request)
        }.requireTransferSuccess().body()

    private fun urlFor(id: String) = "$balancesUrl/${id.encodeURLPathPart()}"

    private suspend fun accessToken(): String = authClient.accessToken(listOf(balanceScope))

    private fun HttpResponse.requireTransferSuccess(): HttpResponse = when (status) {
        HttpStatusCode.NotFound -> throw NoCardForPhoneNumberException()
        HttpStatusCode.BadRequest -> throw InvalidTransferException()
        HttpStatusCode.Gone -> throw TransferChallengeExpiredException()
        HttpStatusCode.Locked -> throw SigningLockedException()
        else -> requireSuccess()
    }

    private fun HttpResponse.requireSuccess(): HttpResponse {
        if (!status.isSuccess()) throw Exception("Balance request failed (${status.value})")
        return this
    }
}
