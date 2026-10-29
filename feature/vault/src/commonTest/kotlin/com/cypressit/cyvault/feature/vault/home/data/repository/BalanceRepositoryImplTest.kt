package com.cypressit.cyvault.feature.vault.home.data.repository

import com.cypressit.cyvault.feature.vault.home.data.remote.BalanceApiService
import com.cypressit.cyvault.feature.vault.home.data.remote.CyVaultApiConfig
import com.cypressit.cyvault.feature.vault.home.domain.model.Balance
import com.cypressit.cyvault.feature.vault.fake.FakeAuthClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BalanceRepositoryImplTest {

    private fun repository(status: HttpStatusCode, json: String = "") = BalanceRepositoryImpl(
        BalanceApiService(
            client = HttpClient(MockEngine {
                respond(json, status, headersOf(HttpHeaders.ContentType, "application/json"))
            }) { install(ContentNegotiation) { json() } },
            authClient = FakeAuthClient(),
            config = CyVaultApiConfig.Production,
        )
    )

    @Test
    fun saveBalance_returnsTheBalanceAsTheServerStoredIt() = runTest {
        val result = repository(
            HttpStatusCode.OK,
            """{"id":"main","name":"Main account","amount":1250.5,"currency":"EUR"}""",
        ).saveBalance(Balance(id = "main", name = " Main account ", amount = 1250.5, currency = "eur"))

        assertEquals(Balance(id = "main", name = "Main account", amount = 1250.5, currency = "EUR"), result.getOrThrow())
    }

    @Test
    fun getBalances_serverError_isAFailedResult() = runTest {
        val result = repository(HttpStatusCode.InternalServerError).getBalances()

        assertTrue(result.isFailure)
    }

    @Test
    fun deleteBalance_succeeds() = runTest {
        val result = repository(HttpStatusCode.NoContent).deleteBalance("main")

        assertTrue(result.isSuccess)
    }
}
