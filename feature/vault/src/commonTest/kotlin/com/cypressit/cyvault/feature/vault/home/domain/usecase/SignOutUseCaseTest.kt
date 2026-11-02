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

    /** Records each step, so the order of stopping pushes and signing out can be checked. */
    private val steps = mutableListOf<String>()

    private fun accounts(account: Account?) = object : AccountRepository {
        override suspend fun currentAccount(): Account? = account

        override suspend fun signOut(): Result<Unit> {
            steps += "signOut:${devices.stoppedPushUserIds}"
            return Result.success(Unit)
        }
    }

    @Test
    fun signOut_stopsTheAccountsPushesFirst() = runTest {
        val account = Account(id = "user-1", name = "Ana", email = "ana@example.com")

        val result = SignOutUseCase(accounts(account), devices)()

        assertTrue(result.isSuccess)
        // The pushes stopped while the user was still signed in: the API needs their token.
        assertEquals(listOf("signOut:[user-1]"), steps)
    }

    @Test
    fun signOut_nobodySignedIn_stillSignsOut() = runTest {
        val result = SignOutUseCase(accounts(account = null), devices)()

        assertTrue(result.isSuccess)
        assertEquals(emptyList(), devices.stoppedPushUserIds)
        assertEquals(1, steps.size)
    }
}
