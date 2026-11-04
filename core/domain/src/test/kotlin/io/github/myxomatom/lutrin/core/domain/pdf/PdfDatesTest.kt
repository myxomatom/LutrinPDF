// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.domain.pdf

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.Test

class PdfDatesTest {
    @Test
    fun `parses a full date in UTC`() {
        assertThat(parsePdfDate("D:20260101000000Z")).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"))
    }

    @Test
    fun `applies a positive time zone offset with apostrophes`() {
        assertThat(parsePdfDate("D:20261103205400+01'00'")).isEqualTo(Instant.parse("2026-11-03T19:54:00Z"))
    }

    @Test
    fun `applies a negative offset without apostrophes`() {
        assertThat(parsePdfDate("D:202611031200-0530")).isEqualTo(Instant.parse("2026-11-03T17:30:00Z"))
    }

    @Test
    fun `accepts a date without prefix nor time`() {
        assertThat(parsePdfDate("2026")).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"))
    }

    @Test
    fun `returns null for blank or invalid values`() {
        assertThat(parsePdfDate(null)).isNull()
        assertThat(parsePdfDate("  ")).isNull()
        assertThat(parsePdfDate("yesterday")).isNull()
        assertThat(parsePdfDate("D:20261399")).isNull()
    }
}
