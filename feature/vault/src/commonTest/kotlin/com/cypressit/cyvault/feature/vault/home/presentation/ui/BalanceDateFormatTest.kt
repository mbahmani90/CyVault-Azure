package com.cypressit.cyvault.feature.vault.home.presentation.ui

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

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
    fun datePickerMillis_isTheUtcMidnightOfTheLocalDay() {
        // 23:30 UTC on 27 Jul is already 28 Jul in Lisbon (UTC+1), so the picker shows the 28th.
        val instant = Instant.parse("2026-07-27T23:30:00Z")
        val expected = Instant.parse("2026-07-28T00:00:00Z").toEpochMilliseconds()

        assertEquals(expected, toDatePickerMillis(instant, TimeZone.of("Europe/Lisbon")))
    }

    @Test
    fun fromPickers_isThePickedDayAtThePickedLocalTime() {
        val day = Instant.parse("2026-07-28T00:00:00Z").toEpochMilliseconds()

        assertEquals(
            Instant.parse("2026-07-28T08:15:00Z"),
            fromPickers(day, hour = 9, minute = 15, timeZone = TimeZone.of("Europe/Lisbon")),
        )
    }

    @Test
    fun pickers_roundTripTheSameLocalDay() {
        val zone = TimeZone.of("Europe/Lisbon")
        val original = Instant.parse("2026-10-28T22:02:00Z")

        assertEquals(original, fromPickers(toDatePickerMillis(original, zone), 22, 2, zone))
    }

    @Test
    fun missingOrUnreadableDate_isNull() {
        assertNull(formatBalanceDate(null))
        assertNull(formatBalanceDate("yesterday"))
    }
}
