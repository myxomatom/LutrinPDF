// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.about

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.ui.theme.LutrinTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** E4 through its route and a real ViewModel: content, privacy policy, licenses, source code link. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h900dp-xhdpi")
class AboutScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val application: Application = ApplicationProvider.getApplicationContext()
    private var backClicks = 0

    private fun show(source: LicensesSource = FakeLicensesSource(), version: String? = "1.2.3") {
        val viewModel = AboutViewModel({ version }, source, UnconfinedTestDispatcher())
        composeRule.setContent {
            LutrinTheme(dynamicColor = false) { AboutRoute(onBack = { backClicks++ }, viewModel = viewModel) }
        }
    }

    private fun scrollTo(text: String) {
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(text))
    }

    private fun clickSourceCode() {
        scrollTo(SOURCE_URL)
        composeRule.onNodeWithText(SOURCE_URL).performClick()
    }

    @Test
    fun `shows the name, the version and the promise of no data collected`() {
        show()

        composeRule.onNodeWithText("Lutrin").assertIsDisplayed()
        composeRule.onNodeWithText("Version 1.2.3").assertIsDisplayed()
        composeRule.onNodeWithText("No data collected").assertIsDisplayed()
        composeRule.onNodeWithText("Free PDF reader, with no ads and no trackers.").assertIsDisplayed()
    }

    @Test
    fun `hides the version when it is unknown`() {
        show(version = null)

        composeRule.onAllNodesWithText("Version", substring = true).assertCountEquals(0)
    }

    @Test
    fun `says the app is under the GPL but distributed under the AGPL because of MuPDF`() {
        show()

        composeRule.onNodeWithText("GNU GPL v3 or later, distributed under the GNU AGPL v3").assertIsDisplayed()
        composeRule.onNodeWithText("MuPDF, which is under the GNU AGPL v3", substring = true).assertIsDisplayed()
    }

    @Test
    fun `the source code shows its address and asks before opening it`() {
        show()

        scrollTo(SOURCE_URL)
        composeRule.onNodeWithText(SOURCE_URL).assertHasClickAction()
        clickSourceCode()

        composeRule.onNodeWithText("Open the source code?").assertIsDisplayed()
        assertThat(shadowOf(application).nextStartedActivity).isNull()
    }

    @Test
    fun `cancelling the confirmation opens nothing`() {
        show()
        clickSourceCode()

        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.onNodeWithText("Open the source code?").assertDoesNotExist()
        assertThat(shadowOf(application).nextStartedActivity).isNull()
    }

    @Test
    fun `confirming hands the address to another app`() {
        show()
        clickSourceCode()

        composeRule.onNodeWithText("Open").performClick()
        composeRule.waitForIdle()

        val intent = shadowOf(application).nextStartedActivity
        assertThat(intent.action).isEqualTo(android.content.Intent.ACTION_VIEW)
        assertThat(intent.dataString).isEqualTo(SOURCE_URL)
        assertThat(intent.categories).contains(android.content.Intent.CATEGORY_BROWSABLE)
    }

    @Test
    fun `without an app to open it, a message says so`() {
        shadowOf(application).checkActivities(true)
        show()
        clickSourceCode()

        composeRule.onNodeWithText("Open").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("No app can open this link.").assertIsDisplayed()
    }

    @Test
    fun `the privacy policy is local text, reached and left by the back arrow`() {
        show()

        scrollTo("Privacy policy")
        composeRule.onNodeWithText("Privacy policy").performClick()

        composeRule.onNodeWithText("Lutrin does not collect, transmit or sell any data.").assertIsDisplayed()
        composeRule.onNodeWithText("Free PDF reader, with no ads and no trackers.").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("Free PDF reader, with no ads and no trackers.").assertIsDisplayed()
        assertThat(backClicks).isEqualTo(0)
    }

    @Test
    fun `the back arrow of the main page leaves the screen`() {
        show()

        composeRule.onNodeWithContentDescription("Back").performClick()

        assertThat(backClicks).isEqualTo(1)
    }

    @Test
    fun `lists the third-party licenses sorted, with the license of each`() {
        show()

        scrollTo("MuPDF")
        composeRule.onNodeWithText("Third-party licenses").assertIsDisplayed()
        composeRule.onNodeWithText("MuPDF").assertIsDisplayed()
        composeRule.onNodeWithText("AGPL-3.0-or-later").assertIsDisplayed()
    }

    @Test
    fun `a component opens its details and the full text of its license`() {
        show()
        scrollTo("MuPDF")

        composeRule.onNodeWithText("MuPDF").performClick()

        composeRule.onNodeWithText("Version 1.28.6").assertIsDisplayed()
        composeRule.onNodeWithText("GNU AFFERO text").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("Free PDF reader, with no ads and no trackers.").assertIsDisplayed()
    }

    @Test
    fun `a license without text says so`() {
        show()
        scrollTo("alpha")

        composeRule.onNodeWithText("alpha").performClick()

        composeRule.onNodeWithText("The text of this license is not available.").assertIsDisplayed()
    }

    @Test
    fun `a list that is read shows no error`() {
        show()

        scrollTo("Third-party licenses")
        composeRule.onNodeWithText("Third-party licenses").assertIsDisplayed()
        composeRule.onAllNodesWithText("The list of licenses could not be read.").assertCountEquals(0)
    }

    @Test
    fun `an unreadable list is replaced by a message`() {
        show(FakeLicensesSource(json = null))

        scrollTo("The list of licenses could not be read.")
        composeRule.onNodeWithText("The list of licenses could not be read.").assertIsDisplayed()
    }

    @Test
    fun `the confirmation is a dialog`() {
        show()

        clickSourceCode()

        composeRule.onNode(isDialog()).assertIsDisplayed()
    }

    private companion object {
        const val SOURCE_URL = "https://github.com/myxomatom/LutrinPDF"
    }
}
