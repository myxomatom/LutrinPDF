// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.myxomatom.lutrin.core.domain.pdf.OutlineItem
import io.github.myxomatom.lutrin.core.domain.pdf.PdfDestination
import io.github.myxomatom.lutrin.core.testing.A4
import io.github.myxomatom.lutrin.core.testing.FakePdfDocument
import io.github.myxomatom.lutrin.core.ui.theme.LutrinTheme
import io.github.myxomatom.lutrin.feature.reader.ReaderOutline
import io.github.myxomatom.lutrin.feature.reader.ReaderUiState
import io.github.myxomatom.lutrin.feature.reader.flattenOutline
import io.github.myxomatom.lutrin.feature.reader.render.PageRenderer
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot references of the table of contents (V2) over the reader, on the contract of the
 * mock-up: read on page 3, « 2. » expanded by the user, the current section highlighted;
 * light, dark, font at 200 %, French and right to left. The last one is the page preview of the
 * slider with the title of the section (E2).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class OutlineDrawerScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun entry(title: String, page: Int?, uri: String? = null, kids: List<OutlineItem> = emptyList()) =
        OutlineItem(title, page?.let { PdfDestination(it) }, uri, kids)

    private fun contract(withExternal: Boolean = false): List<OutlineItem> = listOf(
        entry("1. Objet du contrat", 0),
        entry(
            "2. Durée du bail",
            1,
            kids = listOf(entry("2.1 Date de prise d’effet", 1), entry("2.2 Renouvellement", 1)),
        ),
        entry(
            "3. Loyer et charges",
            2,
            kids = listOf(entry("3.1 Révision annuelle", 3), entry("3.2 Charges locatives", 3)),
        ),
        entry("4. Résiliation", 4, kids = listOf(entry("4.1 Préavis", 4))),
        entry("5. Dépôt de garantie", 5),
        entry("6. État des lieux", 6),
        entry("Annexes", 7),
    ) +
        if (withExternal) {
            listOf(
                entry("Informations en ligne", null, uri = "https://example.org/bail"),
            )
        } else {
            emptyList()
        }

    private fun state(withExternal: Boolean, page: Int = 2): ReaderUiState.Ready {
        val document = FakePdfDocument(List(PAGES) { A4 })
        val outline =
            ReaderOutline(flattenOutline(contract(withExternal), PAGES), expanded = setOf(1))
                .withCurrentSection(page)
                .revealingCurrentSection()
        return ReaderUiState.Ready(
            title = "Contrat de location",
            pageSizes = document.pageSizes,
            renderer = PageRenderer(document, document.pageSizes, Dispatchers.Unconfined),
            currentPage = page,
            outline = outline,
        )
    }

    private fun capture(
        name: String,
        darkTheme: Boolean = false,
        direction: LayoutDirection = LayoutDirection.Ltr,
        withExternal: Boolean = false,
    ) {
        val ready = state(withExternal)
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                LutrinTheme(darkTheme = darkTheme, dynamicColor = false) {
                    // The drawer is shown open from the start: no animation to wait for.
                    OutlineDrawer(
                        drawerState = rememberDrawerState(DrawerValue.Open),
                        outline = ready.outline,
                        documentTitle = ready.title,
                        pageCount = ready.pageSizes.size,
                        onToggle = {},
                        onEntryClick = {},
                        onClose = {},
                    ) {
                        ReaderScreen(ready, onBack = {})
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun light() = capture("outline_light")

    @Test
    fun dark() = capture("outline_dark", darkTheme = true)

    // The font scale comes from the configuration, like on a device: icons keep their size.
    @Test
    @Config(fontScale = 2f)
    fun largeFont() = capture("outline_light_font200")

    @Test
    @Config(qualifiers = "+fr")
    fun french() = capture("outline_light_fr")

    @Test
    fun rightToLeft() = capture("outline_light_rtl", direction = LayoutDirection.Rtl)

    @Test
    fun externalEntry() = capture("outline_external_light", withExternal = true)

    /** The slider dragged to page 5: the card of the preview names its section under « Page 5 ». */
    @Test
    fun previewShowsTheSection() {
        val ready = state(withExternal = false)
        composeRule.setContent { LutrinTheme(dynamicColor = false) { ReaderScreen(ready, onBack = {}) } }
        composeRule.onNodeWithContentDescription("Go to page").performTouchInput {
            fun thumbAt(page: Int) =
                Offset(THUMB_RADIUS_PX + (width - 2 * THUMB_RADIUS_PX) * page / (PAGES - 1), centerY)
            val start = thumbAt(START_PAGE)
            val target = thumbAt(AIMED_PAGE) + Offset(TOUCH_SLOP_PX, 0f)
            down(start)
            val step = (target - start) / DRAG_STEPS.toFloat()
            repeat(DRAG_STEPS) { moveBy(step) }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/outline_preview_section_light.png")
        composeRule.onNodeWithContentDescription("Go to page").performTouchInput { cancel() }
    }

    private companion object {
        const val PAGES = 8
        const val START_PAGE = 2
        const val AIMED_PAGE = 4

        /** Half the 20 dp thumb, at the xhdpi density of these captures. */
        const val THUMB_RADIUS_PX = 20f

        /** Touch slop at xhdpi (8 dp), lost at the start of a drag. */
        const val TOUCH_SLOP_PX = 16f
        const val DRAG_STEPS = 20
    }
}
