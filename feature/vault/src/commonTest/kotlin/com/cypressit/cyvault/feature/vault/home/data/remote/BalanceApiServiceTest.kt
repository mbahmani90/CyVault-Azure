package com.cypressit.cyvault.feature.vault.home.data.remote

import com.cypressit.cyvault.feature.vault.fake.FakeAuthClient
import com.cypressit.cyvault.feature.vault.home.data.remote.dto.BalanceRequestDto
import com.cypressit.cyvault.feature.vault.home.data.remote.dto.TransferRequestDto
import com.cypressit.cyvault.feature.vault.home.domain.repository.InvalidTransferException
import com.cypressit.cyvault.feature.vault.home.domain.repository.NoCardForPhoneNumberException
import com.cypressit.cyvault.feature.vault.home.domain.repository.SigningLockedException
import com.cypressit.cyvault.feature.vault.home.domain.repository.TransferChallengeExpiredException
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
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class BalanceApiServiceTest {

    private val requests = mutableListOf<HttpRequestData>()
    private val authClient = FakeAuthClient()

    private fun api(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        BalanceApiService(
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

    private val mainJson =
        """{"id":"main","name":"Main account","amount":1250.5,"currency":"EUR","date":"2026-10-27T09:30:00+00:00","updatedAt":"2026-10-28T15:00:00+00:00","cardId":"card-1"}"""

    @Test
    fun getBalances_sendsABalanceScopeTokenAndParsesTheList() = runTest {
        val balances = api { respondJson("[$mainJson]") }.getBalances()

        val request = requests.single()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("https://api.test/api/me/balances", request.url.toString())
        assertEquals("Bearer test-token", request.headers[HttpHeaders.Authorization])
        assertEquals(listOf(listOf("api://test-api/Balance.ReadWrite")), authClient.requestedScopes)
        assertEquals("Main account", balances.single().name)
        assertEquals(1250.5, balances.single().amount)
        assertEquals("2026-10-27T09:30:00+00:00", balances.single().date)
        assertEquals("card-1", balances.single().cardId)
    }

    @Test
    fun postBalance_postsTheIdAndDateAndParsesTheStoredBalance() = runTest {
        val saved = api { respondJson(mainJson) }
            .postBalance(
                BalanceRequestDto(
                    name = "Main account",
                    description = "",
                    amount = 1250.5,
                    currency = "EUR",
                    date = "2026-10-27T09:30:00Z",
                    cardId = "card-1",
                    id = "uuid-1",
                ),
            )

        val request = requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("https://api.test/api/me/balances", request.url.toString())
        val json = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
        assertEquals("uuid-1", json["id"]?.jsonPrimitive?.content)
        assertEquals("2026-10-27T09:30:00Z", json["date"]?.jsonPrimitive?.content)
        assertEquals("main", saved.id)
    }

    @Test
    fun putBalance_sendsTheJsonBodyToTheBalanceUrl() = runTest {
        val saved = api { respondJson(mainJson) }
            .putBalance(
                "main",
                BalanceRequestDto(
                    name = "Main account",
                    description = "Daily spending",
                    amount = 1250.5,
                    currency = "EUR",
                    cardId = "card-1",
                ),
            )

        val request = requests.single()
        assertEquals(HttpMethod.Put, request.method)
        assertEquals("https://api.test/api/me/balances/main", request.url.toString())
        val body = request.body as TextContent
        assertEquals(ContentType.Application.Json, body.contentType.withoutParameters())
        val json = Json.parseToJsonElement(body.text).jsonObject
        assertEquals("Main account", json["name"]?.jsonPrimitive?.content)
        assertEquals("Daily spending", json["description"]?.jsonPrimitive?.content)
        assertEquals("1250.5", json["amount"]?.jsonPrimitive?.content)
        assertEquals("EUR", json["currency"]?.jsonPrimitive?.content)
        assertEquals("card-1", json["cardId"]?.jsonPrimitive?.content)
        assertEquals("main", saved.id)
    }

    @Test
    fun deleteBalance_sendsADeleteToTheBalanceUrl() = runTest {
        api { respond("", HttpStatusCode.NoContent) }.deleteBalance("main")

        val request = requests.single()
        assertEquals(HttpMethod.Delete, request.method)
        assertEquals("https://api.test/api/me/balances/main", request.url.toString())
        assertEquals("Bearer test-token", request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun deleteBalance_encodesTheIdInThePath() = runTest {
        api { respond("", HttpStatusCode.NoContent) }.deleteBalance("a b")

        assertEquals("https://api.test/api/me/balances/a%20b", requests.single().url.toString())
    }

    @Test
    fun getBalances_serverError_throws() = runTest {
        assertFailsWith<Exception> {
            api { respond("", HttpStatusCode.Unauthorized) }.getBalances()
        }
    }

    private val transfer = TransferRequestDto(
        transferId = "t1",
        cardId = "card-1",
        phoneNumber = "912345678",
        amount = 20.0,
        currency = "EUR",
    )

    @Test
    fun postTransfer_postsTheTransferAndParsesTheSentBalance() = runTest {
        val sentJson =
            """{"id":"t1-sent","name":"","amount":20.0,"currency":"EUR","cardId":"card-1","type":"Transfer","counterparty":"+351912345678"}"""

        val sent = api { respondJson(sentJson) }.postTransfer(transfer)

        val request = requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("https://api.test/api/me/transfers", request.url.toString())
        assertEquals("Bearer test-token", request.headers[HttpHeaders.Authorization])
        val json = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
        assertEquals("t1", json["transferId"]?.jsonPrimitive?.content)
        assertEquals("912345678", json["phoneNumber"]?.jsonPrimitive?.content)
        assertEquals("Transfer", sent.type)
        assertEquals("+351912345678", sent.counterparty)
    }

    @Test
    fun postTransfer_noCardForThePhoneNumber_throwsNoCardForPhoneNumber() = runTest {
        assertFailsWith<NoCardForPhoneNumberException> {
            api { respond("", HttpStatusCode.NotFound) }.postTransfer(transfer)
        }
    }

    @Test
    fun postTransfer_rejected_throwsInvalidTransfer() = runTest {
        assertFailsWith<InvalidTransferException> {
            api { respond("", HttpStatusCode.BadRequest) }.postTransfer(transfer)
        }
    }

    @Test
    fun postTransferChallenge_postsTheTransferWithoutSigningFieldsAndParsesTheChallenge() = runTest {
        val challengeJson =
            """{"nonce":"n1","payload":"CyVault transfer v1\nnonce:n1","expiresAt":"2026-11-02T10:02:00+00:00"}"""

        val challenge = api { respondJson(challengeJson) }.postTransferChallenge(transfer)

        val request = requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("https://api.test/api/me/transfers/challenge", request.url.toString())
        assertEquals("Bearer test-token", request.headers[HttpHeaders.Authorization])
        val json = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
        assertEquals("t1", json["transferId"]?.jsonPrimitive?.content)
        assertNull(json["signature"]?.jsonPrimitive?.contentOrNull)
        assertEquals("n1", challenge.nonce)
        assertEquals("CyVault transfer v1\nnonce:n1", challenge.payload)
    }

    @Test
    fun postTransferChallenge_noCardForThePhoneNumber_throwsNoCardForPhoneNumber() = runTest {
        assertFailsWith<NoCardForPhoneNumberException> {
            api { respond("", HttpStatusCode.NotFound) }.postTransferChallenge(transfer)
        }
    }

    @Test
    fun postTransfer_sendsTheApproval() = runTest {
        val sentJson =
            """{"id":"t1-sent","name":"","amount":20.0,"currency":"EUR","cardId":"card-1","type":"Transfer"}"""

        api { respondJson(sentJson) }
            .postTransfer(transfer.copy(deviceId = "device-1", nonce = "n1", signature = "c2ln"))

        val json = Json.parseToJsonElement((requests.single().body as TextContent).text).jsonObject
        assertEquals("device-1", json["deviceId"]?.jsonPrimitive?.content)
        assertEquals("n1", json["nonce"]?.jsonPrimitive?.content)
        assertEquals("c2ln", json["signature"]?.jsonPrimitive?.content)
    }

    @Test
    fun postTransfer_expiredChallenge_throwsChallengeExpired() = runTest {
        assertFailsWith<TransferChallengeExpiredException> {
            api { respond("", HttpStatusCode.Gone) }.postTransfer(transfer)
        }
    }

    @Test
    fun postTransfer_signingBlocked_throwsSigningLocked() = runTest {
        assertFailsWith<SigningLockedException> {
            api { respond("", HttpStatusCode.Locked) }.postTransfer(transfer)
        }
    }
}
