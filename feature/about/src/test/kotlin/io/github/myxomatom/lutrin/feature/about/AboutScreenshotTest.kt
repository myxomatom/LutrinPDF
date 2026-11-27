// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.about

import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.myxomatom.lutrin.core.ui.theme.LutrinTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot references of E4 « À propos et licences »: the main page (light, dark, large font,
 * French), the privacy policy, the license text of a component and the confirmation before the
 * source code opens.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class AboutScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val components =
        listOf(
            component(
                "Fraunces",
                listOf(LicenseText("OFL-1.1", "SIL OPEN FONT LICENSE Version 1.1")),
                version = "1.000",
            ),
            component("Jetpack Compose"),
            component("Kotlin Coroutines"),
            component(
                "MuPDF",
                listOf(LicenseText("AGPL-3.0-or-later", "GNU AFFERO GENERAL PUBLIC LICENSE")),
                version = "1.28.6",
            ),
            component(
                "URW++ Base 35 fonts (the 14 standard PDF fonts)",
                listOf(LicenseText("OFL-1.1", "SIL OPEN FONT LICENSE Version 1.1")),
            ),
        )

    private fun show(darkTheme: Boolean, page: AboutPage = AboutPage.Main, confirming: Boolean = false) {
        composeRule.setContent {
            LutrinTheme(darkTheme = darkTheme, dynamicColor = false) {
                AboutScreen(
                    state =
                        AboutUiState(
                            versionName = "1.0.0",
                            licenses = LicensesState.Loaded(components),
                            page = page,
                            confirmingSourceLink = confirming,
                        ),
                    onBack = {},
                )
            }
        }
    }

    private fun capture(name: String, darkTheme: Boolean = false, page: AboutPage = AboutPage.Main) {
        show(darkTheme, page)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    /** The list of third-party licenses, scrolled into view as a user would. */
    private fun captureList(name: String, darkTheme: Boolean = false) {
        show(darkTheme)
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("MuPDF"))
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun captureDialog(name: String, darkTheme: Boolean = false) {
        show(darkTheme, confirming = true)
        composeRule.waitForIdle()
        captureScreenRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun light() = capture("about_light")

    @Test
    fun dark() = capture("about_dark", darkTheme = true)

    @Test
    @Config(fontScale = 2f)
    fun largeFont() = capture("about_light_font200")

    @Test
    @Config(qualifiers = "+fr")
    fun french() = capture("about_light_fr")

    @Test
    fun licenses() = captureList("about_licenses_light")

    @Test
    @Config(qualifiers = "+fr")
    fun licensesFrench() = captureList("about_licenses_light_fr")

    @Test
    fun licensesDark() = captureList("about_licenses_dark", darkTheme = true)

    @Test
    @Config(fontScale = 2f)
    fun licensesLargeFont() = captureList("about_licenses_light_font200")

    @Test
    fun privacy() = capture("about_privacy_light", page = AboutPage.Privacy)

    @Test
    @Config(qualifiers = "+fr")
    fun privacyFrench() = capture("about_privacy_light_fr", page = AboutPage.Privacy)

    @Test
    @Config(fontScale = 2f)
    fun privacyLargeFont() = capture("about_privacy_light_font200", page = AboutPage.Privacy)

    @Test
    fun privacyDark() = capture("about_privacy_dark", darkTheme = true, page = AboutPage.Privacy)

    @Test
    fun componentLicense() = capture("about_component_light", page = AboutPage.Component("org.example:mupdf"))

    @Test
    @Config(fontScale = 2f)
    fun componentLicenseLargeFont() =
        capture("about_component_light_font200", page = AboutPage.Component("org.example:mupdf"))

    @Test
    @Config(qualifiers = "+fr")
    fun componentLicenseFrench() = capture("about_component_light_fr", page = AboutPage.Component("org.example:mupdf"))

    @Test
    fun componentLicenseDark() =
        capture("about_component_dark", darkTheme = true, page = AboutPage.Component("org.example:mupdf"))

    @Test
    fun sourceCodeConfirmation() = captureDialog("about_source_dialog_light")

    @Test
    fun sourceCodeConfirmationDark() = captureDialog("about_source_dialog_dark", darkTheme = true)

    @Test
    @Config(qualifiers = "+fr", fontScale = 2f)
    fun sourceCodeConfirmationFrenchLargeFont() = captureDialog("about_source_dialog_light_fr_font200")
}
