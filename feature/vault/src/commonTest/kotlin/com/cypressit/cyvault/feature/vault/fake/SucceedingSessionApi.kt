package com.cypressit.cyvault.feature.vault.fake

import com.cypressit.cyvault.auth.AuthClient
import com.cypressit.cyvault.feature.vault.home.data.remote.CyVaultApiConfig
import com.cypressit.cyvault.feature.vault.home.data.remote.SessionApiService
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode

/** A [SessionApiService] whose server-side sign-out always succeeds, for tests that don't look at it. */
fun succeedingSessionApi(authClient: AuthClient) = SessionApiService(
    client = HttpClient(MockEngine { respond("", HttpStatusCode.NoContent) }),
    authClient = authClient,
    config = CyVaultApiConfig(baseUrl = "https://api.test/api", appIdUri = "api://test-api"),
)
