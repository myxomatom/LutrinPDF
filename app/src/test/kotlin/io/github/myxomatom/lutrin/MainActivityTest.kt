// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Starts the real activity and Hilt application under Robolectric. */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    /** The recent list is read by Room on a background thread, which `waitForIdle` does not wait for. */
    private fun waitForText(text: String) {
        composeRule.waitUntil(TIMEOUT_MS) { composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `starts on the library in English`() {
        waitForText("No recent documents")
        composeRule.onNodeWithText("Lutrin").assertIsDisplayed()
        composeRule.onNodeWithText("No recent documents").assertIsDisplayed()
        composeRule.onNodeWithText("Open a file").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "fr")
    fun `starts on the library in French`() {
        waitForText("Aucun document récent")
        composeRule.onNodeWithText("Aucun document récent").assertIsDisplayed()
    }

    @Test
    fun `runs inside the Hilt application`() {
        assertThat(composeRule.activity.application).isInstanceOf(LutrinApplication::class.java)
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
