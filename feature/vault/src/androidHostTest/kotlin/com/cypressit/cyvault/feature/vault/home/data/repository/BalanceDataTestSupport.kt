package com.cypressit.cyvault.feature.vault.home.data.repository

import androidx.paging.PagingSource
import androidx.room.Room
import com.cypressit.cyvault.feature.vault.fake.FakeAuthClient
import com.cypressit.cyvault.feature.vault.home.data.local.BalanceCacheDao
import com.cypressit.cyvault.feature.vault.home.data.local.BalanceCacheDatabase
import com.cypressit.cyvault.feature.vault.home.data.local.CachedBalance
import com.cypressit.cyvault.feature.vault.home.data.remote.BalanceApiService
import com.cypressit.cyvault.feature.vault.home.data.remote.CyVaultApiConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertIs

internal fun inMemoryBalanceCache(): BalanceCacheDatabase = Room.inMemoryDatabaseBuilder(
    RuntimeEnvironment.getApplication(),
    BalanceCacheDatabase::class.java,
).allowMainThreadQueries().build()

internal fun balanceApi(
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
) = BalanceApiService(
    client = HttpClient(MockEngine { request -> handler(request) }) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    },
    authClient = FakeAuthClient(),
    config = CyVaultApiConfig(baseUrl = "https://api.test/api", appIdUri = "api://test-api"),
)

internal fun MockRequestHandleScope.respondJson(json: String) =
    respond(json, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))

internal fun balanceJson(id: String, name: String = id) =
    """{"id":"$id","name":"$name","amount":1.5,"currency":"EUR","date":"2026-10-28T09:00:00Z","cardId":"card-1","type":"Income"}"""

internal fun pageJson(vararg ids: String, token: String? = null): String {
    val tokenJson = token?.let { "\"$it\"" } ?: "null"
    return """{"items":[${ids.joinToString(",") { balanceJson(it) }}],"continuationToken":$tokenJson}"""
}

internal fun cachedBalance(id: String, name: String = id) = CachedBalance(
    id = id,
    name = name,
    description = "",
    amount = 1.5,
    currency = "EUR",
    date = "2026-10-28T09:00:00Z",
    cardId = "card-1",
    type = "Income",
    counterparty = null,
)

internal suspend fun BalanceCacheDao.cachedRows(): List<CachedBalance> {
    val params = PagingSource.LoadParams.Refresh<Int>(key = null, loadSize = 100, placeholdersEnabled = false)
    val result = balances().load(params)
    return assertIs<PagingSource.LoadResult.Page<Int, CachedBalance>>(result).data
}

internal suspend fun BalanceCacheDao.cachedIds(): List<String> = cachedRows().map { it.id }
