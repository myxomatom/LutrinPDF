// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PageScrubberTest {
    @Test
    fun `with the preview on, sliding only aims at the page`() {
        val scrubber = PageScrubber(previewEnabled = true)

        assertThat(scrubber.onSlide(4)).isNull()
        assertThat(scrubber.onSlide(5)).isNull()

        assertThat(scrubber.isPreviewing).isTrue()
        assertThat(scrubber.previewPage).isEqualTo(5)
        assertThat(scrubber.aimedPage).isEqualTo(5)
    }

    @Test
    fun `with the preview on, the release gives the page aimed at and keeps it until arrival`() {
        val scrubber = PageScrubber(previewEnabled = true)
        scrubber.onSlide(5)

        assertThat(scrubber.onRelease()).isEqualTo(5)

        assertThat(scrubber.isPreviewing).isFalse()
        assertThat(scrubber.previewPage).isNull()
        assertThat(scrubber.aimedPage).isEqualTo(5)
        scrubber.onArrived()
        assertThat(scrubber.aimedPage).isNull()
    }

    @Test
    fun `a new drag before the arrival keeps its own page`() {
        val scrubber = PageScrubber(previewEnabled = true)
        scrubber.onSlide(5)
        scrubber.onRelease()

        scrubber.onSlide(2)
        scrubber.onArrived()

        assertThat(scrubber.aimedPage).isEqualTo(2)
        assertThat(scrubber.isPreviewing).isTrue()
    }

    @Test
    fun `a cancelled gesture goes nowhere`() {
        val scrubber = PageScrubber(previewEnabled = true)
        scrubber.onSlide(5)

        scrubber.cancel()

        assertThat(scrubber.onRelease()).isNull()
        assertThat(scrubber.aimedPage).isNull()
        assertThat(scrubber.isPreviewing).isFalse()
    }

    @Test
    fun `a release without a move goes nowhere`() {
        assertThat(PageScrubber(previewEnabled = true).onRelease()).isNull()
        assertThat(PageScrubber(previewEnabled = false).onRelease()).isNull()
    }

    @Test
    fun `with the preview off, every move shows its page at once`() {
        val scrubber = PageScrubber(previewEnabled = false)

        assertThat(scrubber.onSlide(4)).isEqualTo(4)
        assertThat(scrubber.onSlide(0)).isEqualTo(0)

        assertThat(scrubber.isPreviewing).isFalse()
        assertThat(scrubber.aimedPage).isNull()
        assertThat(scrubber.onRelease()).isNull()
    }
}
