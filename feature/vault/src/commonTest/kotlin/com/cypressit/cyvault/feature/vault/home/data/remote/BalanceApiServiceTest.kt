package com.cypressit.cyvault.feature.vault.home.data.remote

import com.cypressit.cyvault.feature.vault.fake.FakeAuthClient
import com.cypressit.cyvault.feature.vault.home.data.remote.dto.BalanceRequestDto
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
        )

    private fun MockRequestHandleScope.respondJson(json: String) =
        respond(json, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))

    private val mainJson =
        """{"id":"main","name":"Main account","amount":1250.5,"currency":"EUR","date":"2026-10-27T09:30:00+00:00","updatedAt":"2026-10-28T15:00:00+00:00"}"""

    @Test
    fun getBalances_sendsABalanceScopeTokenAndParsesTheList() = runTest {
        val balances = api { respondJson("[$mainJson]") }.getBalances()

        val request = requests.single()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals(BalanceApiService.BALANCES_URL, request.url.toString())
        assertEquals("Bearer test-token", request.headers[HttpHeaders.Authorization])
        assertEquals(listOf(listOf(BalanceApiService.BALANCE_SCOPE)), authClient.requestedScopes)
        assertEquals("Main account", balances.single().name)
        assertEquals(1250.5, balances.single().amount)
        assertEquals("2026-10-27T09:30:00+00:00", balances.single().date)
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
                ),
            )

        val request = requests.single()
        assertEquals(HttpMethod.Put, request.method)
        assertEquals("${BalanceApiService.BALANCES_URL}/main", request.url.toString())
        val body = request.body as TextContent
        assertEquals(ContentType.Application.Json, body.contentType.withoutParameters())
        val json = Json.parseToJsonElement(body.text).jsonObject
        assertEquals("Main account", json["name"]?.jsonPrimitive?.content)
        assertEquals("Daily spending", json["description"]?.jsonPrimitive?.content)
        assertEquals("1250.5", json["amount"]?.jsonPrimitive?.content)
        assertEquals("EUR", json["currency"]?.jsonPrimitive?.content)
        assertEquals("main", saved.id)
    }

    @Test
    fun deleteBalance_sendsADeleteToTheBalanceUrl() = runTest {
        api { respond("", HttpStatusCode.NoContent) }.deleteBalance("main")

        val request = requests.single()
        assertEquals(HttpMethod.Delete, request.method)
        assertEquals("${BalanceApiService.BALANCES_URL}/main", request.url.toString())
        assertEquals("Bearer test-token", request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun deleteBalance_encodesTheIdInThePath() = runTest {
        api { respond("", HttpStatusCode.NoContent) }.deleteBalance("a b")

        assertEquals("${BalanceApiService.BALANCES_URL}/a%20b", requests.single().url.toString())
    }

    @Test
    fun getBalances_serverError_throws() = runTest {
        assertFailsWith<Exception> {
            api { respond("", HttpStatusCode.Unauthorized) }.getBalances()
        }
    }
}
