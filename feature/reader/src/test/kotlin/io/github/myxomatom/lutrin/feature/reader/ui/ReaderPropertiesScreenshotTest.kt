// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.myxomatom.lutrin.core.domain.document.DocumentProperties
import io.github.myxomatom.lutrin.core.testing.FakePdfDocument
import io.github.myxomatom.lutrin.core.ui.theme.LutrinTheme
import io.github.myxomatom.lutrin.feature.reader.ReaderUiState
import io.github.myxomatom.lutrin.feature.reader.render.PageRenderer
import java.time.Instant
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot references of the properties sheet (V5, ADR 0027) over the reader: light and dark,
 * with the text at 200 %, in French, and with the values the document does not give. The sheet is a
 * separate window, captured as the whole screen. The dates are shown in UTC so that the images do
 * not depend on the machine that records them.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class ReaderPropertiesScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val zone = TimeZone.getDefault()

    private val full =
        DocumentProperties(
            fileName = "contrat_location_2026.pdf",
            title = "Contrat de location",
            author = "Alice Martin",
            pageCount = 8,
            sizeBytes = 421_888,
            pdfVersion = "1.7",
            createdAt = Instant.parse("2026-09-12T10:14:00Z"),
            modifiedAt = Instant.parse("2026-09-14T16:02:00Z"),
        )

    /** A document with no information dictionary, from a provider that gives neither name nor size. */
    private val bare = DocumentProperties(null, null, null, 3, null, null, null, null)

    @Before
    fun useUtc() = TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

    @After
    fun restoreZone() = TimeZone.setDefault(zone)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        name: String,
        properties: DocumentProperties,
        darkTheme: Boolean = false,
        canPrint: Boolean = true,
    ) {
        val document = FakePdfDocument()
        val state =
            ReaderUiState.Ready(
                "Contrat de location",
                document.pageSizes,
                PageRenderer(document, document.pageSizes, Dispatchers.Unconfined),
                canPrint = canPrint,
            )
        composeRule.setContent {
            LutrinTheme(darkTheme = darkTheme, dynamicColor = false) {
                ReaderScreen(state, onBack = {}, properties = ReaderProperties(value = properties))
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun properties() = capture("reader_properties_light", full)

    @Test
    fun propertiesDark() = capture("reader_properties_dark", full, darkTheme = true)

    @Test
    @Config(qualifiers = "w360dp-h800dp-xhdpi", fontScale = 2f)
    fun propertiesLargeFont() = capture("reader_properties_light_font200", full)

    @Test
    @Config(qualifiers = "fr-w360dp-h800dp-xhdpi")
    fun propertiesFrench() = capture("reader_properties_fr", full)

    @Test
    fun propertiesMissingValues() = capture("reader_properties_missing_light", bare)

    @Test
    @Config(qualifiers = "fr-w360dp-h800dp-xhdpi")
    fun propertiesMissingValuesFrench() = capture("reader_properties_missing_fr", bare)

    @Test
    fun propertiesPrintForbidden() = capture("reader_properties_print_forbidden_light", full, canPrint = false)
}
