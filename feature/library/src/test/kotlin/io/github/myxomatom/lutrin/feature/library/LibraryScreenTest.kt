// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.library

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.ui.theme.LutrinTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class LibraryScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var openClicks = 0
    private var settingsClicks = 0

    private fun show() {
        composeRule.setContent {
            LutrinTheme(dynamicColor = false) {
                LibraryScreen(onOpenFile = { openClicks++ }, onOpenSettings = { settingsClicks++ })
            }
        }
    }

    @Test
    fun `empty library explains how to open a document`() {
        show()

        composeRule.onNodeWithText("Lutrin").assertIsDisplayed()
        composeRule.onNodeWithText("No recent documents").assertIsDisplayed()
        composeRule.onNodeWithText("Works fully offline. No data collected.").assertIsDisplayed()
    }

    @Test
    fun `open button asks for a file`() {
        show()

        composeRule.onNodeWithText("Open a file").assertHasClickAction().performClick()

        assertThat(openClicks).isEqualTo(1)
    }

    @Test
    fun `settings action opens the settings`() {
        show()

        composeRule.onNodeWithContentDescription("Settings").performClick()

        assertThat(settingsClicks).isEqualTo(1)
    }

    @Test
    @Config(qualifiers = "fr")
    fun `empty library is translated in French`() {
        show()

        composeRule.onNodeWithText("Aucun document récent").assertIsDisplayed()
        composeRule.onNodeWithText("Ouvrir un fichier").assertIsDisplayed()
    }
}
