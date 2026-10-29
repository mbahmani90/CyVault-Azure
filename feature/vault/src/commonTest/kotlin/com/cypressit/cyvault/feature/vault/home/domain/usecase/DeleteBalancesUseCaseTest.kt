package com.cypressit.cyvault.feature.vault.home.domain.usecase

import com.cypressit.cyvault.feature.vault.fake.FakeBalanceRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DeleteBalancesUseCaseTest {

    private val repository = FakeBalanceRepository()

    @Test
    fun deletesEveryBalance() = runTest {
        val deleted = DeleteBalancesUseCase(repository)(listOf("a", "b", "c"))

        assertEquals(setOf("a", "b", "c"), deleted)
        assertEquals(listOf("a", "b", "c"), repository.deletedIds)
    }

    @Test
    fun whenOneFails_stillDeletesTheRestAndLeavesItOut() = runTest {
        repository.failingDeleteIds = setOf("b")

        val deleted = DeleteBalancesUseCase(repository)(listOf("a", "b", "c"))

        assertEquals(setOf("a", "c"), deleted)
        assertEquals(listOf("a", "c"), repository.deletedIds)
    }

    @Test
    fun withNoIds_deletesNothing() = runTest {
        val deleted = DeleteBalancesUseCase(repository)(emptyList())

        assertEquals(emptySet(), deleted)
        assertEquals(emptyList(), repository.deletedIds)
    }
}
