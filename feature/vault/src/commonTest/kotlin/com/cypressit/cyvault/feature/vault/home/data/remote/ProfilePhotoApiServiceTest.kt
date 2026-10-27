package com.cypressit.cyvault.feature.vault.home.data.remote

import com.cypressit.cyvault.feature.vault.fake.FakeSessionManager
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ProfilePhotoApiServiceTest {

    private val requests = mutableListOf<HttpRequestData>()
    private val session = FakeSessionManager()

    private fun api(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        ProfilePhotoApiService(
            client = HttpClient(MockEngine { request -> requests += request; handler(request) }),
            sessionManager = session,
        )

    private fun MockRequestHandleScope.respondJpeg(bytes: ByteArray) =
        respond(bytes, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "image/jpeg"))

    @Test
    fun getPhoto_sendsAProfileScopeTokenToTheAvatarUrl() = runTest {
        api { respondJpeg(byteArrayOf(1)) }.getPhoto()

        val request = requests.single()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals(ProfilePhotoApiService.AVATAR_URL, request.url.toString())
        assertEquals("Bearer test-token", request.headers[HttpHeaders.Authorization])
        assertEquals(listOf(listOf(ProfilePhotoApiService.PROFILE_SCOPE)), session.requestedScopes)
    }

    @Test
    fun getPhoto_returnsTheJpegBytes() = runTest {
        val photo = api { respondJpeg(byteArrayOf(1, 2, 3)) }.getPhoto()

        assertContentEquals(byteArrayOf(1, 2, 3), photo)
    }

    @Test
    fun getPhoto_withoutPhoto_returnsNull() = runTest {
        val photo = api { respond("", HttpStatusCode.NotFound) }.getPhoto()

        assertNull(photo)
    }

    @Test
    fun getPhoto_serverError_throws() = runTest {
        assertFailsWith<Exception> {
            api { respond("", HttpStatusCode.InternalServerError) }.getPhoto()
        }
    }

    @Test
    fun putPhoto_uploadsTheJpegWithAProfileScopeToken() = runTest {
        api { respond("", HttpStatusCode.NoContent) }.putPhoto(byteArrayOf(4, 5))

        val request = requests.single()
        assertEquals(HttpMethod.Put, request.method)
        assertEquals("Bearer test-token", request.headers[HttpHeaders.Authorization])
        assertEquals(ContentType.Image.JPEG, request.body.contentType)
        assertContentEquals(byteArrayOf(4, 5), (request.body as OutgoingContent.ByteArrayContent).bytes())
    }

    @Test
    fun putPhoto_rejected_throws() = runTest {
        assertFailsWith<Exception> {
            api { respond("", HttpStatusCode.UnsupportedMediaType) }.putPhoto(byteArrayOf(4, 5))
        }
    }
}
