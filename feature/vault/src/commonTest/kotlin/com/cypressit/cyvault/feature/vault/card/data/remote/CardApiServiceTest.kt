package com.cypressit.cyvault.feature.vault.card.data.remote

import com.cypressit.cyvault.feature.vault.card.data.remote.dto.CardRequestDto
import com.cypressit.cyvault.feature.vault.card.data.repository.CardRepositoryImpl
import com.cypressit.cyvault.feature.vault.card.domain.repository.InvalidCardException
import com.cypressit.cyvault.feature.vault.card.domain.repository.PhoneNumberAlreadyLinkedException
import com.cypressit.cyvault.feature.vault.fake.FakeAuthClient
import com.cypressit.cyvault.feature.vault.home.data.remote.CyVaultApiConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class CardApiServiceTest {

    private val requests = mutableListOf<HttpRequestData>()
    private val authClient = FakeAuthClient()

    private fun api(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        CardApiService(
            client = HttpClient(
                MockEngine { request ->
                    requests += request
                    handler(request)
                },
            ) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            },
            authClient = authClient,
            config = CyVaultApiConfig(baseUrl = "https://api.test/api", appIdUri = "api://test-api"),
        )

    private fun MockRequestHandleScope.respondJson(json: String) =
        respond(json, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))

    private val request =
        CardRequestDto(cardNumber = "4111111111111111", phoneNumber = "912345678", bankName = "Millennium")

    private val visaJson =
        """{"id":"visa","last4":"1111","brand":"Visa","bankName":"Millennium",""" +
            """"phoneNumber":"+351912345678","createdAt":"2026-11-01T09:30:00+00:00"}"""

    @Test
    fun getCards_sendsACardScopeTokenAndParsesTheList() = runTest {
        val cards = api { respondJson("[$visaJson]") }.getCards()

        val request = requests.single()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("https://api.test/api/me/cards", request.url.toString())
        assertEquals("Bearer test-token", request.headers[HttpHeaders.Authorization])
        assertEquals(listOf(listOf("api://test-api/Card.ReadWrite")), authClient.requestedScopes)
        assertEquals("1111", cards.single().last4)
        assertEquals("+351912345678", cards.single().phoneNumber)
        assertEquals("Visa", cards.single().brand)
        assertEquals("Millennium", cards.single().bankName)
    }

    @Test
    fun getCards_withoutBrandOrBankName_defaultsToEmpty() = runTest {
        val oldCardJson =
            """{"id":"old","last4":"4444","phoneNumber":"+351954564594","createdAt":"2026-11-01T18:05:01+00:00"}"""
        val cards = api { respondJson("[$oldCardJson]") }.getCards()

        assertEquals("", cards.single().brand)
        assertEquals("", cards.single().bankName)
    }

    @Test
    fun putCard_sendsTheCardNumberAndPhoneToTheCardUrl() = runTest {
        val saved = api { respondJson(visaJson) }
            .putCard("visa", request)

        val request = requests.single()
        assertEquals(HttpMethod.Put, request.method)
        assertEquals("https://api.test/api/me/cards/visa", request.url.toString())
        val body = request.body as TextContent
        assertEquals(ContentType.Application.Json, body.contentType.withoutParameters())
        val json = Json.parseToJsonElement(body.text).jsonObject
        assertEquals("4111111111111111", json["cardNumber"]?.jsonPrimitive?.content)
        assertEquals("912345678", json["phoneNumber"]?.jsonPrimitive?.content)
        assertEquals("Millennium", json["bankName"]?.jsonPrimitive?.content)
        assertEquals("visa", saved.id)
    }

    @Test
    fun putCard_conflict_throwsPhoneNumberAlreadyLinked() = runTest {
        assertFailsWith<PhoneNumberAlreadyLinkedException> {
            api { respond("", HttpStatusCode.Conflict) }
                .putCard("visa", request)
        }
    }

    @Test
    fun putCard_badRequest_throwsInvalidCard() = runTest {
        assertFailsWith<InvalidCardException> {
            api { respond("", HttpStatusCode.BadRequest) }
                .putCard("visa", request)
        }
    }

    @Test
    fun deleteCard_encodesTheIdInThePath() = runTest {
        api { respond("", HttpStatusCode.NoContent) }.deleteCard("a b")

        val request = requests.single()
        assertEquals(HttpMethod.Delete, request.method)
        assertEquals("https://api.test/api/me/cards/a%20b", request.url.toString())
    }

    @Test
    fun getCards_serverError_throws() = runTest {
        assertFailsWith<Exception> {
            api { respond("", HttpStatusCode.Unauthorized) }.getCards()
        }
    }

    @Test
    fun repository_createCard_mapsTheResponse() = runTest {
        val card = CardRepositoryImpl(api { respondJson(visaJson) })
            .createCard(id = "visa", cardNumber = "4111111111111111", phoneNumber = "912345678", bankName = "")
            .getOrThrow()

        assertEquals("1111", card.last4)
        assertEquals("+351912345678", card.phoneNumber)
        assertEquals("2026-11-01T09:30:00+00:00", card.createdAt)
    }

    @Test
    fun repository_createCard_conflict_failsWithPhoneNumberAlreadyLinked() = runTest {
        val result = CardRepositoryImpl(api { respond("", HttpStatusCode.Conflict) })
            .createCard(id = "visa", cardNumber = "4111111111111111", phoneNumber = "912345678", bankName = "")

        assertIs<PhoneNumberAlreadyLinkedException>(result.exceptionOrNull())
    }
}
