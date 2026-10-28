package com.cypressit.cyvault.feature.vault.home.presentation.ui

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

private val balanceDateFormat = LocalDateTime.Format {
    day(padding = Padding.NONE)
    char(' ')
    monthName(MonthNames.ENGLISH_ABBREVIATED)
    char(' ')
    year()
    chars(", ")
    hour()
    char(':')
    minute()
}

/**
 * An ISO 8601 date from the API, such as `2026-10-27T09:30:00+00:00`, in the device's time zone:
 * `27 Oct 2026, 10:30`. Null when there is no date or it can't be read.
 */
internal fun formatBalanceDate(iso: String?, timeZone: TimeZone = TimeZone.currentSystemDefault()): String? =
    iso?.let { parseOrNull(it)?.let { instant -> format(instant, timeZone) } }

/** The earliest of [isoDates], formatted like [formatBalanceDate]; null when none can be read. */
internal fun oldestBalanceDate(
    isoDates: List<String?>,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String? = isoDates.mapNotNull { it?.let(::parseOrNull) }.minOrNull()?.let { format(it, timeZone) }

/** [instant] in the device's time zone, formatted like [formatBalanceDate]. */
internal fun formatBalanceDate(instant: Instant, timeZone: TimeZone = TimeZone.currentSystemDefault()): String =
    format(instant, timeZone)

/** The ISO 8601 date as an instant, or null when there is none or it can't be read. */
internal fun parseBalanceDate(iso: String?): Instant? = iso?.let(::parseOrNull)

/**
 * The Material date picker works in UTC midnights: this is the picker value for the local
 * calendar day of [instant].
 */
internal fun toDatePickerMillis(instant: Instant, timeZone: TimeZone = TimeZone.currentSystemDefault()): Long =
    instant.toLocalDateTime(timeZone).date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

/** The day picked in the date picker ([datePickerMillis], a UTC midnight) at [hour]:[minute] local time. */
internal fun fromPickers(
    datePickerMillis: Long,
    hour: Int,
    minute: Int,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): Instant {
    val day = Instant.fromEpochMilliseconds(datePickerMillis).toLocalDateTime(TimeZone.UTC).date
    return LocalDateTime(day, LocalTime(hour, minute)).toInstant(timeZone)
}

private fun parseOrNull(iso: String): Instant? = runCatching { Instant.parse(iso) }.getOrNull()

private fun format(instant: Instant, timeZone: TimeZone): String =
    balanceDateFormat.format(instant.toLocalDateTime(timeZone))
