package com.cypressit.cyvault.feature.vault.home.presentation.viewmodel

import com.cypressit.cyvault.feature.vault.home.domain.model.Balance
import kotlin.test.Test
import kotlin.test.assertEquals

class NewestFirstTest {

    private fun balance(id: String, date: String?) =
        Balance(id = id, name = id, amount = 1.0, currency = "EUR", date = date)

    @Test
    fun sortsByInstantNewestFirst() {
        val sorted = listOf(
            balance("oldest", "2026-10-01T00:00:00Z"),
            // Later as text than "…T07:00Z" below, but 06:00 UTC: earlier.
            balance("middle", "2026-10-28T08:00:00+02:00"),
            balance("newest", "2026-10-28T07:00:00Z"),
        ).newestFirst()

        assertEquals(listOf("newest", "middle", "oldest"), sorted.map { it.id })
    }

    @Test
    fun undatedGoLast_andTiesKeepAStableOrder() {
        val sorted = listOf(
            balance("b", null),
            balance("dated", "2026-10-01T00:00:00Z"),
            balance("a", "not a date"),
        ).newestFirst()

        assertEquals(listOf("dated", "a", "b"), sorted.map { it.id })
    }
}
