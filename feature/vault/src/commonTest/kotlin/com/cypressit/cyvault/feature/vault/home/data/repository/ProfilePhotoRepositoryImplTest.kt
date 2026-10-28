package com.cypressit.cyvault.feature.vault.home.data.repository

import com.cypressit.cyvault.feature.vault.fake.FakeAvatarFileStore
import com.cypressit.cyvault.feature.vault.fake.FakePickedPhotoReader
import com.cypressit.cyvault.feature.vault.fake.FakeAuthClient
import com.cypressit.cyvault.feature.vault.fake.FakeAuthClient.Companion.USER_ID
import com.cypressit.cyvault.feature.vault.home.data.remote.ProfilePhotoApiService
import com.cypressit.cyvault.feature.vault.home.domain.model.PickedPhoto
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The real API client over a MockEngine "server", with the device cache and reader faked. */
class ProfilePhotoRepositoryImplTest {

    private val fileStore = FakeAvatarFileStore()
    private val photoReader = FakePickedPhotoReader()
    private val requests = mutableListOf<HttpRequestData>()

    // What the server holds; it re-encodes every upload into SERVER_VERSION.
    private var serverPhoto: ByteArray? = null
    private var serverDown = false

    private companion object {
        val SERVER_VERSION = byteArrayOf(9, 9)
        val PHOTO = PickedPhoto("content://media/picker/0/1")
    }

    private fun TestScope.repository() = ProfilePhotoRepositoryImpl(
        apiService = ProfilePhotoApiService(HttpClient(MockEngine { request ->
            requests += request
            when {
                serverDown -> respond("", HttpStatusCode.ServiceUnavailable)
                request.method == HttpMethod.Put -> {
                    serverPhoto = SERVER_VERSION
                    respond("", HttpStatusCode.NoContent)
                }
                else -> serverPhoto
                    ?.let { respond(it, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "image/jpeg")) }
                    ?: respond("", HttpStatusCode.NotFound)
            }
        }), FakeAuthClient()),
        fileStore = fileStore,
        photoReader = photoReader,
        ioDispatcher = StandardTestDispatcher(testScheduler),
    )

    private fun List<ByteArray?>.asLists() = map { it?.toList() }

    @Test
    fun getPhoto_emitsTheCachedCopyThenTheServersCopy() = runTest {
        fileStore.files[USER_ID] = byteArrayOf(1)
        serverPhoto = byteArrayOf(2)

        val emitted = repository().getPhoto(USER_ID).toList()

        assertEquals(listOf(listOf<Byte>(1), listOf<Byte>(2)), emitted.asLists())
        assertContentEquals(byteArrayOf(2), fileStore.files[USER_ID])
    }

    @Test
    fun getPhoto_serverCopySameAsCache_emitsOnce() = runTest {
        fileStore.files[USER_ID] = byteArrayOf(1)
        serverPhoto = byteArrayOf(1)

        val emitted = repository().getPhoto(USER_ID).toList()

        assertEquals(listOf(listOf<Byte>(1)), emitted.asLists())
    }

    @Test
    fun getPhoto_noPhotoOnServer_emitsNullAndClearsTheCache() = runTest {
        fileStore.files[USER_ID] = byteArrayOf(1)

        val emitted = repository().getPhoto(USER_ID).toList()

        assertEquals(listOf(listOf<Byte>(1), null), emitted.asLists())
        assertTrue(fileStore.files.isEmpty())
    }

    @Test
    fun getPhoto_serverUnreachable_keepsTheCachedCopy() = runTest {
        fileStore.files[USER_ID] = byteArrayOf(1)
        serverDown = true

        val emitted = repository().getPhoto(USER_ID).toList()

        assertEquals(listOf(listOf<Byte>(1)), emitted.asLists())
        assertContentEquals(byteArrayOf(1), fileStore.files[USER_ID])
    }

    @Test
    fun changePhoto_uploadsThePickedPhotoAndCachesTheServersVersion() = runTest {
        photoReader.jpeg = byteArrayOf(5, 5)

        val result = repository().changePhoto(USER_ID, PHOTO)

        assertEquals(listOf(PHOTO), photoReader.read)
        val upload = requests.first { it.method == HttpMethod.Put }
        assertContentEquals(byteArrayOf(5, 5), (upload.body as OutgoingContent.ByteArrayContent).bytes())
        assertContentEquals(SERVER_VERSION, result.getOrThrow())
        assertContentEquals(SERVER_VERSION, fileStore.files[USER_ID])
    }

    @Test
    fun changePhoto_unreadablePhoto_failsWithoutUploading() = runTest {
        photoReader.failure = IllegalArgumentException("Not an image")

        val result = repository().changePhoto(USER_ID, PHOTO)

        assertTrue(result.isFailure)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun changePhoto_serverUnreachable_failsAndKeepsTheCache() = runTest {
        fileStore.files[USER_ID] = byteArrayOf(1)
        serverDown = true

        val result = repository().changePhoto(USER_ID, PHOTO)

        assertTrue(result.isFailure)
        assertContentEquals(byteArrayOf(1), fileStore.files[USER_ID])
    }

    @Test
    fun changePhoto_cancelled_rethrowsInsteadOfFailing() = runTest {
        photoReader.failure = CancellationException("Screen closed")

        assertFailsWith<CancellationException> { repository().changePhoto(USER_ID, PHOTO) }
    }

    @Test
    fun clearCache_deletesEveryCachedPhoto() = runTest {
        fileStore.files[USER_ID] = byteArrayOf(1)
        fileStore.files["another-user"] = byteArrayOf(2)

        repository().clearCache()

        assertTrue(fileStore.files.isEmpty())
    }
}
