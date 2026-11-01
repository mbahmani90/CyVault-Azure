package com.cypressit.cyvault.feature.vault.card.data.remote

import com.cypressit.cyvault.auth.AuthClient
import com.cypressit.cyvault.feature.vault.card.data.remote.dto.CardRequestDto
import com.cypressit.cyvault.feature.vault.card.data.remote.dto.CardResponseDto
import com.cypressit.cyvault.feature.vault.card.domain.repository.InvalidCardException
import com.cypressit.cyvault.feature.vault.card.domain.repository.PhoneNumberAlreadyLinkedException
import com.cypressit.cyvault.feature.vault.home.data.remote.CyVaultApiConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess

/**
 * The signed-in user's cards on the CyVault Azure API. Like the balance routes, the routes carry
 * no user ID: the API finds the user from the access token.
 */
class CardApiService(
    private val client: HttpClient,
    private val authClient: AuthClient,
    config: CyVaultApiConfig,
) {

    private val cardsUrl = "${config.baseUrl}/me/cards"

    /** The CyVault API app registration's card scope in the environment's Entra tenant. */
    private val cardScope = "${config.appIdUri}/Card.ReadWrite"

    suspend fun getCards(): List<CardResponseDto> =
        client.get(cardsUrl) { bearerAuth(accessToken()) }.requireSuccess().body()

    /**
     * Adds the card, or changes the phone number of the card with this [id]; returns it as stored.
     * Throws [PhoneNumberAlreadyLinkedException] when another card already has the phone number,
     * and [InvalidCardException] when the API rejects the card or phone number.
     */
    suspend fun putCard(id: String, request: CardRequestDto): CardResponseDto =
        client.put(urlFor(id)) {
            bearerAuth(accessToken())
            contentType(ContentType.Application.Json)
            setBody(request)
        }.requireSuccess().body()

    suspend fun deleteCard(id: String) {
        client.delete(urlFor(id)) { bearerAuth(accessToken()) }.requireSuccess()
    }

    private fun urlFor(id: String) = "$cardsUrl/${id.encodeURLPathPart()}"

    private suspend fun accessToken(): String = authClient.accessToken(listOf(cardScope))

    private fun HttpResponse.requireSuccess(): HttpResponse {
        if (status == HttpStatusCode.Conflict) throw PhoneNumberAlreadyLinkedException()
        if (status == HttpStatusCode.BadRequest) throw InvalidCardException()
        if (!status.isSuccess()) throw Exception("Card request failed (${status.value})")
        return this
    }
}
