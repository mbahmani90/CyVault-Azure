package com.cypressit.cyvault.feature.vault.device.data.repository

import com.cypressit.cyvault.feature.vault.device.data.remote.DeviceApiService
import com.cypressit.cyvault.feature.vault.device.domain.repository.ApprovalPrompt
import com.cypressit.cyvault.feature.vault.device.domain.repository.DeviceEnrollment
import com.cypressit.cyvault.feature.vault.device.domain.repository.DeviceSignature
import com.cypressit.cyvault.feature.vault.device.domain.repository.SigningCancelledException
import com.cypressit.cyvault.feature.vault.device.domain.repository.SigningUnavailableException
import com.cypressit.cyvault.feature.vault.fake.FakeAuthClient
import com.cypressit.cyvault.feature.vault.fake.FakeDeviceIdStore
import com.cypressit.cyvault.feature.vault.fake.FakeDeviceKeyStore
import com.cypressit.cyvault.feature.vault.home.data.remote.CyVaultApiConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DeviceRepositoryImplTest {

    private val userId = FakeAuthClient.USER_ID
    private val requests = mutableListOf<HttpRequestData>()
    private val authClient = FakeAuthClient()
    private val keyStore = FakeDeviceKeyStore()
    private val idStore = FakeDeviceIdStore()

    private fun TestScope.repository(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        DeviceRepositoryImpl(
            apiService = DeviceApiService(
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
            ),
            keyStore = keyStore,
            idStore = idStore,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

    private fun MockRequestHandleScope.respondDevice(id: String) = respond(
        """{"id":"$id","algorithm":"ES256","deviceName":"Pixel Test","createdAt":"2026-11-02T10:00:00+00:00"}""",
        HttpStatusCode.Created,
        headersOf(HttpHeaders.ContentType, "application/json"),
    )

    private fun HttpRequestData.jsonBody() =
        Json.parseToJsonElement((body as TextContent).text).jsonObject

    @Test
    fun enroll_firstTime_createsAKeyAndPostsItsPublicKey() = runTest {
        val result = repository { respondDevice("device-1") }.enroll(userId)

        assertEquals(DeviceEnrollment.Enrolled("device-1"), result.getOrThrow())
        val request = requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("https://api.test/api/me/devices", request.url.toString())
        assertEquals("Bearer test-token", request.headers[HttpHeaders.Authorization])
        assertEquals(listOf(listOf("api://test-api/Profile.ReadWrite")), authClient.requestedScopes)
        // The fake's first key is the single byte 0x01.
        assertEquals("AQ==", request.jsonBody()["publicKey"]!!.jsonPrimitive.content)
        assertEquals("Pixel Test", request.jsonBody()["deviceName"]!!.jsonPrimitive.content)
        assertEquals("device-1", idStore.ids[userId])
    }

    @Test
    fun enroll_alreadyEnrolled_callsNothing() = runTest {
        keyStore.createKey(userId)
        idStore.ids[userId] = "device-1"

        val result = repository { error("No request expected") }.enroll(userId)

        assertEquals(DeviceEnrollment.Enrolled("device-1"), result.getOrThrow())
        assertTrue(requests.isEmpty())
        assertEquals(1, keyStore.createdKeyCount)
    }

    @Test
    fun enroll_keyWithoutDeviceId_sendsTheSameKeyAgain() = runTest {
        keyStore.createKey(userId)

        val result = repository { respondDevice("device-1") }.enroll(userId)

        assertEquals(DeviceEnrollment.Enrolled("device-1"), result.getOrThrow())
        assertEquals(1, keyStore.createdKeyCount)
        assertEquals("AQ==", requests.single().jsonBody()["publicKey"]!!.jsonPrimitive.content)
    }

    @Test
    fun enroll_revokedKey_replacesItWithANewOne() = runTest {
        keyStore.createKey(userId)

        val result = repository {
            if (requests.size == 1) respond("", HttpStatusCode.Conflict) else respondDevice("device-2")
        }.enroll(userId)

        assertEquals(DeviceEnrollment.Enrolled("device-2"), result.getOrThrow())
        assertEquals(2, keyStore.createdKeyCount)
        assertEquals("Ag==", requests[1].jsonBody()["publicKey"]!!.jsonPrimitive.content)
        assertEquals("device-2", idStore.ids[userId])
    }

    @Test
    fun enroll_newKey_dropsTheOldKeysDeviceIdFirst() = runTest {
        idStore.ids[userId] = "old-device"

        val result = repository { respond("", HttpStatusCode.InternalServerError) }.enroll(userId)

        // No key existed, so a new one was made: the old ID must not be paired with it.
        assertTrue(result.isFailure)
        assertEquals(null, idStore.ids[userId])
    }

    @Test
    fun enroll_withoutBiometrics_reportsItAndSendsNothing() = runTest {
        keyStore.hasBiometrics = false

        val result = repository { error("No request expected") }.enroll(userId)

        assertEquals(DeviceEnrollment.BiometricsUnavailable, result.getOrThrow())
        assertTrue(requests.isEmpty())
    }

    @Test
    fun enroll_serverError_failsAndKeepsTheKeyForNextTime() = runTest {
        val result = repository { respond("", HttpStatusCode.InternalServerError) }.enroll(userId)

        assertTrue(result.isFailure)
        assertEquals(null, idStore.ids[userId])
        assertEquals(1, keyStore.keys.size)
    }

    private val prompt =
        ApprovalPrompt(title = "Approve transfer", description = "Send 20 EUR to +351912345678", cancelLabel = "Cancel")

    @Test
    fun sign_enrolledDevice_showsThePromptAndSignsWithItsKey() = runTest {
        keyStore.createKey(userId)
        idStore.ids[userId] = "device-1"

        val result = repository { error("No request expected") }.sign(userId, "payload", prompt)

        // The fake's signature is the key byte (0x01) followed by the message: base64 of 01 "payload".
        assertEquals(DeviceSignature("device-1", "AXBheWxvYWQ="), result.getOrThrow())
        assertEquals("Send 20 EUR to +351912345678", keyStore.prompts.single().description)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun sign_notEnrolledYet_enrollsFirst() = runTest {
        val result = repository { respondDevice("device-1") }.sign(userId, "payload", prompt)

        assertEquals("device-1", result.getOrThrow().deviceId)
        assertEquals(1, requests.size)
    }

    @Test
    fun sign_keyDisabledByANewBiometric_enrollsANewKeyAndSigns() = runTest {
        keyStore.createKey(userId)
        idStore.ids[userId] = "device-1"
        keyStore.invalidatedKeys += userId

        val result = repository { respondDevice("device-2") }.sign(userId, "payload", prompt)

        assertEquals("device-2", result.getOrThrow().deviceId)
        assertEquals(2, keyStore.createdKeyCount)
        assertEquals("Ag==", requests.single().jsonBody()["publicKey"]!!.jsonPrimitive.content)
        assertEquals("device-2", idStore.ids[userId])
    }

    @Test
    fun sign_userBacksOut_isCancelled() = runTest {
        keyStore.createKey(userId)
        idStore.ids[userId] = "device-1"
        keyStore.cancelsPrompt = true

        val result = repository { error("No request expected") }.sign(userId, "payload", prompt)

        assertIs<SigningCancelledException>(result.exceptionOrNull())
    }

    @Test
    fun sign_withoutBiometrics_isUnavailable() = runTest {
        keyStore.hasBiometrics = false

        val result = repository { error("No request expected") }.sign(userId, "payload", prompt)

        assertIs<SigningUnavailableException>(result.exceptionOrNull())
    }
}
