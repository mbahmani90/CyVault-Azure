package com.cypressit.cyvault.feature.vault.home.data.remote

import com.cypressit.cyvault.feature.vault.fake.FakeAuthClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SessionApiServiceTest {

    private val requests = mutableListOf<HttpRequestData>()
    private val authClient = FakeAuthClient()

    private fun api(status: HttpStatusCode) = SessionApiService(
        client = HttpClient(MockEngine { request -> requests += request; respond("", status) }),
        authClient = authClient,
    )

    @Test
    fun revokeSessions_postsAProfileScopeTokenToTheSignOutUrl() = runTest {
        api(HttpStatusCode.NoContent).revokeSessions()

        val request = requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals(SessionApiService.SIGN_OUT_URL, request.url.toString())
        assertEquals("Bearer test-token", request.headers[HttpHeaders.Authorization])
        assertEquals(listOf(listOf(ProfilePhotoApiService.PROFILE_SCOPE)), authClient.requestedScopes)
    }

    @Test
    fun revokeSessions_serverError_throws() = runTest {
        assertFailsWith<Exception> { api(HttpStatusCode.BadGateway).revokeSessions() }
    }
}
