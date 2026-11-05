// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.activity.FullyDrawnReporter
import androidx.activity.FullyDrawnReporterOwner
import androidx.activity.compose.LocalFullyDrawnReporterOwner
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.testing.FakePdfDocument
import io.github.myxomatom.lutrin.core.ui.theme.LutrinTheme
import io.github.myxomatom.lutrin.feature.reader.render.PageRenderer
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The activity is reported fully drawn once the first pages are rendered: the end of the
 * "open a document" measure of the `:benchmark` module (ADR 0012).
 */
@RunWith(AndroidJUnit4::class)
class PdfPageFullyDrawnTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val document = FakePdfDocument()
    private var fullyDrawn = false

    /** Stands for the activity's reporter, with a report run at once instead of at the next frame. */
    private val owner =
        object : FullyDrawnReporterOwner {
            override val fullyDrawnReporter = FullyDrawnReporter(Executor { it.run() }) { fullyDrawn = true }
        }

    private fun show(renderDispatcher: CoroutineDispatcher) {
        composeRule.setContent {
            CompositionLocalProvider(LocalFullyDrawnReporterOwner provides owner) {
                LutrinTheme(dynamicColor = false) {
                    PdfPagesView(
                        pageSizes = document.pageSizes,
                        renderer = PageRenderer(document, document.pageSizes, renderDispatcher),
                        listState = rememberLazyListState(),
                        zoom = 1f,
                        onZoomChange = {},
                        onTap = {},
                        contentDescription = DOCUMENT,
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `the activity is reported fully drawn once the visible pages are rendered`() {
        show(Dispatchers.Unconfined)

        composeRule.waitUntil(TIMEOUT_MS) { fullyDrawn }

        assertThat(document.renders).isNotEmpty()
    }

    @Test
    fun `the activity is not reported fully drawn while the pages are still rendering`() {
        // A dispatcher that never runs: the renderings never finish.
        show(StandardTestDispatcher())

        composeRule.mainClock.advanceTimeBy(WAIT_MS)
        composeRule.waitForIdle()

        assertThat(fullyDrawn).isFalse()
        assertThat(document.renders).isEmpty()
    }

    private companion object {
        const val DOCUMENT = "Document"
        const val TIMEOUT_MS = 5_000L
        const val WAIT_MS = 1_000L
    }
}
