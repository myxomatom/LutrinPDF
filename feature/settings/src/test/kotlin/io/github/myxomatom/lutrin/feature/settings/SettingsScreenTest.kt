// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.settings

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.ui.theme.LutrinTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val changes = mutableListOf<Boolean>()
    private val previewChanges = mutableListOf<Boolean>()
    private var backClicks = 0
    private var clearRequests = 0

    private fun show(state: SettingsUiState, supported: Boolean = true) {
        composeRule.setContent {
            LutrinTheme(dynamicColor = false) {
                SettingsScreen(
                    state = state,
                    dynamicColorSupported = supported,
                    onDynamicColorChange = { changes += it },
                    onBack = { backClicks++ },
                    onClearRecents = { clearRequests++ },
                    onPagePreviewChange = { previewChanges += it },
                )
            }
        }
    }

    @Test
    fun `shows the dynamic colour switch on by default`() {
        show(SettingsUiState(loading = false))

        composeRule.onNodeWithText("Appearance").assertIsDisplayed()
        composeRule.onNodeWithText("Dynamic colours").assertIsDisplayed()
        composeRule.onNodeWithText("Use the colours of your wallpaper").assertIsDisplayed()
        composeRule.onNodeWithText("Dynamic colours").assertIsOn()
    }

    @Test
    fun `tapping the row turns dynamic colours off`() {
        show(SettingsUiState(loading = false, dynamicColor = true))

        composeRule.onNodeWithText("Dynamic colours").performClick()

        assertThat(changes).containsExactly(false)
    }

    @Test
    fun `tapping the row turns dynamic colours back on`() {
        show(SettingsUiState(loading = false, dynamicColor = false))

        composeRule.onNodeWithText("Dynamic colours").assertIsOff().performClick()

        assertThat(changes).containsExactly(true)
    }

    @Test
    fun `before Android 12 the switch is disabled and explains why`() {
        show(SettingsUiState(loading = false, dynamicColor = true), supported = false)

        composeRule.onNodeWithText("Available from Android 12").assertIsDisplayed()
        composeRule.onNodeWithText("Dynamic colours").assertIsNotEnabled().assertIsOff().performClick()
        assertThat(changes).isEmpty()
    }

    @Test
    fun `shows nothing to change while loading`() {
        show(SettingsUiState(loading = true))

        composeRule.onNodeWithText("Dynamic colours").assertDoesNotExist()
        composeRule.onNodeWithText("Page preview").assertDoesNotExist()
    }

    @Test
    fun `offers the page preview of the reader in the reading section, on by default`() {
        show(SettingsUiState(loading = false))

        composeRule.onNodeWithText("Reading").assertIsDisplayed()
        val summary =
            "Shows the page while you drag the slider of the reader; the document moves there when you let go."
        composeRule.onNodeWithText(summary).assertIsDisplayed()
        composeRule
            .onNode(isToggleable() and hasText("Page preview"))
            .assertIsOn()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
    }

    @Test
    fun `tapping the row turns the page preview off, without touching the colours`() {
        show(SettingsUiState(loading = false, pagePreview = true))

        composeRule.onNode(isToggleable() and hasText("Page preview")).performClick()

        assertThat(previewChanges).containsExactly(false)
        assertThat(changes).isEmpty()
    }

    @Test
    fun `tapping the row turns the page preview back on`() {
        show(SettingsUiState(loading = false, pagePreview = false))

        composeRule.onNode(isToggleable() and hasText("Page preview")).assertIsOff().performClick()

        assertThat(previewChanges).containsExactly(true)
    }

    @Test
    fun `the page preview stays available before Android 12`() {
        show(SettingsUiState(loading = false), supported = false)

        composeRule.onNode(isToggleable() and hasText("Page preview")).assertIsEnabled().performClick()

        assertThat(previewChanges).containsExactly(false)
    }

    @Test
    fun `back leaves the settings`() {
        show(SettingsUiState(loading = false))

        composeRule.onNodeWithContentDescription("Back").performClick()

        assertThat(backClicks).isEqualTo(1)
    }

    @Test
    @Config(qualifiers = "fr")
    fun `is translated in French`() {
        show(SettingsUiState(loading = false))

        composeRule.onNodeWithText("Paramètres").assertIsDisplayed()
        composeRule.onNodeWithText("Couleurs dynamiques").assertIsDisplayed()
        composeRule.onNodeWithText("Lecture").assertIsDisplayed()
        composeRule.onNodeWithText("Aperçu de la page").assertIsDisplayed()
        val summary =
            "Montre la page pendant que vous faites glisser le curseur du lecteur\u00A0; " +
                "le document s’y rend quand vous relâchez."
        composeRule.onNodeWithText(summary).assertIsDisplayed()
    }

    @Test
    fun `clear recents is disabled while the list is empty`() {
        show(SettingsUiState(loading = false, recentCount = 0))

        composeRule.onNodeWithText("Data and privacy").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("No recent documents").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Clear recent documents").performScrollTo().assertIsNotEnabled().performClick()

        composeRule.onNode(isDialog()).assertDoesNotExist()
        assertThat(clearRequests).isEqualTo(0)
    }

    @Test
    fun `clear recents asks for confirmation before clearing`() {
        show(SettingsUiState(loading = false, recentCount = 3))

        composeRule.onNodeWithText("Clear recent documents").performScrollTo()
            .assertIsEnabled()
            .assertHasClickAction()
            .performClick()

        composeRule.onNodeWithText("Clear recent documents?").assertIsDisplayed()
        composeRule.onNodeWithText(
            "3 documents will be removed from the list, with their thumbnails and the pages where you stopped. " +
                "The files themselves are not deleted.",
        ).assertIsDisplayed()
        assertThat(clearRequests).isEqualTo(0)

        composeRule.onNodeWithText("Clear").performClick()

        assertThat(clearRequests).isEqualTo(1)
        composeRule.onNode(isDialog()).assertDoesNotExist()
    }

    @Test
    fun `cancelling the confirmation clears nothing`() {
        show(SettingsUiState(loading = false, recentCount = 1))

        composeRule.onNodeWithText("Clear recent documents").performScrollTo().performClick()
        composeRule.onNodeWithText(
            "1 document will be removed from the list, with its thumbnail and the page where you stopped. " +
                "The file itself is not deleted.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.onNode(isDialog()).assertDoesNotExist()
        assertThat(clearRequests).isEqualTo(0)
    }

    @Test
    @Config(qualifiers = "fr")
    fun `clear recents is translated into French`() {
        show(SettingsUiState(loading = false, recentCount = 2))

        composeRule.onNodeWithText("Données et confidentialité").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Vider les récents").performScrollTo().performClick()

        composeRule.onNodeWithText("Vider les récents\u00A0?").assertIsDisplayed()
        composeRule.onNodeWithText("Vider").assertIsDisplayed()
        composeRule.onNodeWithText("Annuler").assertIsDisplayed()
    }
}
