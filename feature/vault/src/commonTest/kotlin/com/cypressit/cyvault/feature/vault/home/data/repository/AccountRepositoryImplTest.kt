package com.cypressit.cyvault.feature.vault.home.data.repository

import com.cypressit.cyvault.feature.vault.fake.FakeAuthClient
import com.cypressit.cyvault.feature.vault.fake.FakeBalanceCache
import com.cypressit.cyvault.feature.vault.home.data.remote.CyVaultApiConfig
import com.cypressit.cyvault.feature.vault.home.data.remote.SessionApiService
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AccountRepositoryImplTest {

    private val authClient = FakeAuthClient()
    private val balanceCache = FakeBalanceCache()
    private val signOutCountsAtRevoke = mutableListOf<Int>()

    private fun repository(status: HttpStatusCode) = AccountRepositoryImpl(
        authClient = authClient,
        sessionApi = SessionApiService(
            client = HttpClient(
                MockEngine {
                    signOutCountsAtRevoke += authClient.signOutCount
                    respond("", status)
                },
            ),
            authClient = authClient,
            config = CyVaultApiConfig(baseUrl = "https://api.test/api", appIdUri = "api://test-api"),
        ),
        balanceCache = balanceCache,
    )

    @Test
    fun signOut_revokesTheSessionsBeforeTheLocalSignOut() = runTest {
        val result = repository(HttpStatusCode.NoContent).signOut()

        assertTrue(result.isSuccess)
        assertEquals(listOf(0), signOutCountsAtRevoke)
        assertEquals(1, authClient.signOutCount)
    }

    @Test
    fun signOut_whenTheRevokeFails_stillSignsOutLocally() = runTest {
        val result = repository(HttpStatusCode.BadGateway).signOut()

        assertTrue(result.isSuccess)
        assertEquals(1, authClient.signOutCount)
    }

    @Test
    fun signOut_clearsTheCachedBalances() = runTest {
        repository(HttpStatusCode.NoContent).signOut()

        assertEquals(1, balanceCache.clearCount)
    }

    @Test
    fun signOut_whenClearingTheCacheFails_stillSucceeds() = runTest {
        balanceCache.failure = Exception("disk full")

        val result = repository(HttpStatusCode.NoContent).signOut()

        assertTrue(result.isSuccess)
        assertEquals(1, authClient.signOutCount)
    }
}
