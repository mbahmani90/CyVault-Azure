package com.cypressit.cyvault.feature.vault.home.presentation.ui

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BalanceDateFormatTest {

    @Test
    fun formatsInTheGivenTimeZone() {
        // Lisbon is UTC+1 in July (summer time).
        assertEquals(
            "27 Jul 2026, 10:30",
            formatBalanceDate("2026-07-27T09:30:00+00:00", TimeZone.of("Europe/Lisbon")),
        )
    }

    @Test
    fun readsTheApisSevenDigitFractions() {
        assertEquals(
            "5 Jan 2026, 09:05",
            formatBalanceDate("2026-01-05T09:05:12.1234567+00:00", TimeZone.UTC),
        )
    }

    @Test
    fun oldestBalanceDate_comparesInstantsNotText() {
        // As text "2026-10-28T08…+02:00" sorts after "2026-10-28T07…Z", but it's earlier.
        assertEquals(
            "28 Oct 2026, 06:00",
            oldestBalanceDate(
                listOf("2026-10-28T07:00:00Z", null, "2026-10-28T08:00:00+02:00", "not a date"),
                TimeZone.UTC,
            ),
        )
    }

    @Test
    fun oldestBalanceDate_withoutDates_isNull() {
        assertNull(oldestBalanceDate(emptyList()))
        assertNull(oldestBalanceDate(listOf(null)))
    }

    @Test
    fun missingOrUnreadableDate_isNull() {
        assertNull(formatBalanceDate(null))
        assertNull(formatBalanceDate("yesterday"))
    }
}
