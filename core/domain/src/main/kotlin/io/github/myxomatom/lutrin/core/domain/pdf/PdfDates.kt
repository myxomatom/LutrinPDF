// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.domain.pdf

import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

private val PDF_DATE =
    Regex(
        "^(?:D:)?(?<year>\\d{4})(?<month>\\d{2})?(?<day>\\d{2})?(?<hour>\\d{2})?(?<minute>\\d{2})?(?<second>\\d{2})?" +
            "(?:[Zz](?:00'?(?:00'?)?)?|(?<sign>[+-])(?<offsetHours>\\d{2})'?(?:(?<offsetMinutes>\\d{2})'?)?)?$",
    )

/**
 * Parses a PDF date string (ISO 32000-1 §7.9.4), such as `D:20261103205400+01'00'`.
 *
 * Missing month, day or time fields default to their lowest value; a missing time zone means UTC.
 * `Z` may be followed by a zero offset (`Z00'00'`), as written by some producers. Out-of-range
 * fields (month 13, hour 24, second 60, offset minutes above 59…) make the date invalid.
 *
 * @param value the raw date string from the document information dictionary.
 * @return the instant, or `null` when [value] is blank or not a valid PDF date.
 */
@Suppress("SwallowedException")
fun parsePdfDate(value: String?): Instant? {
    val match = value?.trim()?.let(PDF_DATE::matchEntire) ?: return null
    fun field(name: String, default: Int = 0): Int = match.groups[name]?.value?.toInt() ?: default
    return try {
        val local = LocalDateTime.of(
            field("year"),
            field("month", default = 1),
            field("day", default = 1),
            field("hour"),
            field("minute"),
            field("second"),
        )
        val sign = if (match.groups["sign"]?.value == "-") -1 else 1
        // ofHoursMinutes rejects minutes above 59 and offsets beyond ±18:00.
        local.toInstant(ZoneOffset.ofHoursMinutes(sign * field("offsetHours"), sign * field("offsetMinutes")))
    } catch (error: DateTimeException) {
        // Invalid field values are an expected outcome for untrusted metadata: no date.
        null
    }
}
