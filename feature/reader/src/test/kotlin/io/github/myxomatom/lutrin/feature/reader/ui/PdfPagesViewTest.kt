// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.testing.FakePdfDocument
import io.github.myxomatom.lutrin.core.ui.theme.LutrinTheme
import io.github.myxomatom.lutrin.feature.reader.render.MAX_ZOOM
import io.github.myxomatom.lutrin.feature.reader.render.PageRenderer
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfPagesViewTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val document = FakePdfDocument()
    private var zoom by mutableFloatStateOf(1f)
    private var zoomChanges = 0

    private fun show() {
        composeRule.setContent {
            LutrinTheme(dynamicColor = false) {
                PdfPagesView(
                    pageSizes = document.pageSizes,
                    renderer = PageRenderer(document, document.pageSizes, Dispatchers.Unconfined),
                    listState = rememberLazyListState(),
                    zoom = zoom,
                    onZoomChange = {
                        zoom = it
                        zoomChanges++
                    },
                    onTap = {},
                    contentDescription = DOCUMENT,
                )
            }
        }
    }

    /** Spreads two fingers from [from] to [to] pixels away from the centre. */
    private fun pinch(from: Float, to: Float) {
        composeRule.onNodeWithContentDescription(DOCUMENT).performTouchInput {
            pinch(
                start0 = center - Offset(from, 0f),
                end0 = center - Offset(to, 0f),
                start1 = center + Offset(from, 0f),
                end1 = center + Offset(to, 0f),
            )
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `a pinch zooms by the ratio of the finger distances, applied once at the end`() {
        show()

        pinch(from = 50f, to = 100f)

        assertThat(zoom).isWithin(TOLERANCE).of(2f)
        assertThat(zoomChanges).isEqualTo(1)
    }

    @Test
    fun `successive pinches build on the current zoom`() {
        // Regression: the gesture kept the zoom of the first frame, so a second pinch
        // restarted from 100 % and the page shook instead of zooming further.
        show()

        pinch(from = 50f, to = 100f)
        pinch(from = 50f, to = 100f)

        assertThat(zoom).isWithin(TOLERANCE * 2).of(4f)
    }

    @Test
    fun `pinching in stops at the largest zoom`() {
        show()

        repeat(4) { pinch(from = 20f, to = 120f) }

        assertThat(zoom).isEqualTo(MAX_ZOOM)
    }

    private companion object {
        const val DOCUMENT = "Document"

        /** Touch injection moves the fingers in steps, so the measured ratio is approximate. */
        const val TOLERANCE = 0.15f
    }
}
