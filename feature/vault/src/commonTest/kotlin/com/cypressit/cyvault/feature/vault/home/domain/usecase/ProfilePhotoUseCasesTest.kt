package com.cypressit.cyvault.feature.vault.home.domain.usecase

import com.cypressit.cyvault.feature.vault.fake.FakeProfilePhotoRepository
import com.cypressit.cyvault.feature.vault.home.domain.model.PickedPhoto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class ProfilePhotoUseCasesTest {

    private val repository = FakeProfilePhotoRepository()

    @Test
    fun getProfilePhoto_returnsTheUsersPhoto() = runTest {
        repository.photo = flowOf(byteArrayOf(1))

        val photo = GetProfilePhotoUseCase(repository)("user-1").first()

        assertContentEquals(byteArrayOf(1), photo)
        assertEquals(listOf("user-1"), repository.photoRequests)
    }

    @Test
    fun changeProfilePhoto_returnsTheStoredPhoto() = runTest {
        repository.changeResult = Result.success(byteArrayOf(7))
        val photo = PickedPhoto("content://photo")

        val result = ChangeProfilePhotoUseCase(repository)("user-1", photo)

        assertContentEquals(byteArrayOf(7), result.getOrThrow())
        assertEquals(listOf("user-1" to photo), repository.changes)
    }

    @Test
    fun clearProfilePhotoCache_clearsTheCache() = runTest {
        ClearProfilePhotoCacheUseCase(repository)()

        assertEquals(1, repository.clearCount)
    }
}
