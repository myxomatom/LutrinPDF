// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
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

    @Test
    fun `shows the welcome screen in English`() {
        composeRule.onNodeWithText("Lutrin").assertIsDisplayed()
        composeRule.onNodeWithText("Free PDF reader. No ads, no trackers, fully offline.").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "fr")
    fun `shows the welcome screen in French`() {
        composeRule.onNodeWithText("Ouvrir un PDF").assertIsDisplayed()
    }

    @Test
    fun `runs inside the Hilt application`() {
        assertThat(composeRule.activity.application).isInstanceOf(LutrinApplication::class.java)
    }
}
