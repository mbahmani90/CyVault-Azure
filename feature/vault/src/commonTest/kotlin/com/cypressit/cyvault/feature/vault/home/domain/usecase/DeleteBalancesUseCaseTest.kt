package com.cypressit.cyvault.feature.vault.home.domain.usecase

import com.cypressit.cyvault.feature.vault.fake.FakeBalanceRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DeleteBalancesUseCaseTest {

    private val repository = FakeBalanceRepository()

    @Test
    fun deletesEveryBalanceInOneRequest() = runTest {
        val deleted = DeleteBalancesUseCase(repository)(listOf("a", "b", "c"))

        assertEquals(setOf("a", "b", "c"), deleted)
        assertEquals(listOf(listOf("a", "b", "c")), repository.deleteRequests)
    }

    @Test
    fun splitsIntoRequestsTheApiTakes() = runTest {
        repository.maxDeletesPerRequest = 2

        val deleted = DeleteBalancesUseCase(repository)(listOf("a", "b", "c", "d", "e"))

        assertEquals(setOf("a", "b", "c", "d", "e"), deleted)
        assertEquals(listOf(listOf("a", "b"), listOf("c", "d"), listOf("e")), repository.deleteRequests)
    }

    @Test
    fun whenARequestFails_stillSendsTheRestAndLeavesItsIdsOut() = runTest {
        repository.maxDeletesPerRequest = 2
        repository.failingDeleteIds = setOf("c")

        val deleted = DeleteBalancesUseCase(repository)(listOf("a", "b", "c", "d", "e"))

        assertEquals(setOf("a", "b", "e"), deleted)
        assertEquals(listOf("a", "b", "e"), repository.deletedIds)
    }

    @Test
    fun sendsEachIdOnce() = runTest {
        DeleteBalancesUseCase(repository)(listOf("a", "a", "b"))

        assertEquals(listOf(listOf("a", "b")), repository.deleteRequests)
    }

    @Test
    fun withNoIds_sendsNothing() = runTest {
        val deleted = DeleteBalancesUseCase(repository)(emptyList())

        assertEquals(emptySet(), deleted)
        assertEquals(emptyList(), repository.deleteRequests)
    }
}
