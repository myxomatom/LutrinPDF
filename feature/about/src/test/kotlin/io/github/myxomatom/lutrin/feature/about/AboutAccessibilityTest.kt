// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.about

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.myxomatom.lutrin.core.testing.assertControlsAreAccessible
import io.github.myxomatom.lutrin.core.ui.theme.LutrinTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * E4 against the accessibility rules of `AGENTS.md` §11 (F18): every control of every page has a
 * name for TalkBack and a touch target of 48 dp, also at 200 %.
 */
@RunWith(AndroidJUnit4::class)
// Tall, so that every row is composed and placed: the audit measures the controls where they are.
@Config(qualifiers = "w360dp-h3000dp-xhdpi")
class AboutAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val components = listOf(component("MuPDF", listOf(LicenseText("AGPL-3.0-or-later", "GNU AFFERO"))))

    private fun show(page: AboutPage = AboutPage.Main, confirming: Boolean = false) {
        composeRule.setContent {
            LutrinTheme(dynamicColor = false) {
                AboutScreen(
                    state = AboutUiState(
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

    @Test
    fun `the main page has named controls of 48 dp`() {
        show()

        composeRule.assertControlsAreAccessible()
    }

    @Test
    @Config(fontScale = 2f)
    fun `the main page stays accessible at 200 percent`() {
        show()

        composeRule.assertControlsAreAccessible()
    }

    @Test
    fun `the privacy policy has a named back button of 48 dp`() {
        show(page = AboutPage.Privacy)

        composeRule.assertControlsAreAccessible()
    }

    @Test
    fun `the license of a component has a named back button of 48 dp`() {
        show(page = AboutPage.Component("org.example:mupdf"))

        composeRule.assertControlsAreAccessible()
    }

    @Test
    fun `the confirmation before the source code opens is accessible`() {
        show(confirming = true)
        composeRule.waitForIdle()

        composeRule.assertControlsAreAccessible()
    }
}
