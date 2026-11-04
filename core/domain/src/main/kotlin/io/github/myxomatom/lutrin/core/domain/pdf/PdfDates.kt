// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.domain.pdf

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

private val PDF_DATE =
    Regex(
        "^(?:D:)?(?<year>\\d{4})(?<month>\\d{2})?(?<day>\\d{2})?(?<hour>\\d{2})?(?<minute>\\d{2})?(?<second>\\d{2})?" +
            "(?:[Zz]|(?<sign>[+-])(?<offsetHours>\\d{2})'?(?:(?<offsetMinutes>\\d{2})'?)?)?$",
    )
private const val SECONDS_PER_MINUTE = 60
private const val MINUTES_PER_HOUR = 60

/**
 * Parses a PDF date string (ISO 32000-1 §7.9.4), such as `D:20261103205400+01'00'`.
 *
 * Missing month, day or time fields default to their lowest value; a missing time zone means UTC.
 *
 * @param value the raw date string from the document information dictionary.
 * @return the instant, or `null` when [value] is blank or not a valid PDF date.
 */
fun parsePdfDate(value: String?): Instant? {
    val match = value?.trim()?.let(PDF_DATE::matchEntire) ?: return null
    fun field(name: String, default: Int = 0): Int = match.groups[name]?.value?.toInt() ?: default
    return runCatching {
        val local = LocalDateTime.of(
            field("year"),
            field("month", default = 1),
            field("day", default = 1),
            field("hour"),
            field("minute"),
            field("second"),
        )
        val sign = if (match.groups["sign"]?.value == "-") -1 else 1
        val offsetMinutes = field("offsetHours") * MINUTES_PER_HOUR + field("offsetMinutes")
        local.toInstant(ZoneOffset.ofTotalSeconds(sign * offsetMinutes * SECONDS_PER_MINUTE))
    }.getOrNull()
}
