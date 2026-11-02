package com.cypressit.cyvault.feature.vault.home.domain.usecase

import com.cypressit.cyvault.feature.vault.fake.FakeDeviceRepository
import com.cypressit.cyvault.feature.vault.home.domain.model.Account
import com.cypressit.cyvault.feature.vault.home.domain.repository.AccountRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SignOutUseCaseTest {

    private val devices = FakeDeviceRepository()

    /** How many times pushes had been stopped when the sign-out ran. */
    private val stopsAtSignOut = mutableListOf<Int>()

    private val accounts = object : AccountRepository {
        override suspend fun currentAccount(): Account? = null

        override suspend fun signOut(): Result<Unit> {
            stopsAtSignOut += devices.stopPushesCount
            return Result.success(Unit)
        }
    }

    @Test
    fun signOut_stopsThePushesFirst() = runTest {
        val result = SignOutUseCase(accounts, devices)()

        assertTrue(result.isSuccess)
        // The pushes stopped while the user was still signed in: the API needs their token.
        assertEquals(listOf(1), stopsAtSignOut)
    }
}
