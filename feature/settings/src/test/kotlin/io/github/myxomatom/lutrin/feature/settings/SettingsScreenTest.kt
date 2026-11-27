// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.settings

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.settings.AppLanguage
import io.github.myxomatom.lutrin.core.domain.settings.AppTheme
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
    private val copyChanges = mutableListOf<Boolean>()
    private var backClicks = 0
    private var clearRequests = 0
    private var aboutRequests = 0
    private val themeChanges = mutableListOf<AppTheme>()
    private val followChanges = mutableListOf<Boolean>()
    private val keepOnChanges = mutableListOf<Boolean>()
    private val languageChanges = mutableListOf<AppLanguage>()

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
                    onIgnoreDocumentRestrictionsChange = { copyChanges += it },
                    onOpenAbout = { aboutRequests++ },
                    onAppThemeChange = { themeChanges += it },
                    onFollowDarkThemeChange = { followChanges += it },
                    onKeepScreenOnChange = { keepOnChanges += it },
                    onAppLanguageChange = { languageChanges += it },
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

        composeRule.onNodeWithText("Reading").performScrollTo().assertIsDisplayed()
        val summary =
            "Shows the page while you drag the slider of the reader; the document moves there when you let go."
        composeRule.onNodeWithText(summary).performScrollTo().assertIsDisplayed()
        composeRule
            .onNode(isToggleable() and hasText("Page preview"))
            .assertIsOn()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
    }

    @Test
    fun `tapping the row turns the page preview off, without touching the colours`() {
        show(SettingsUiState(loading = false, pagePreview = true))

        composeRule.onNode(isToggleable() and hasText("Page preview")).performScrollTo().performClick()

        assertThat(previewChanges).containsExactly(false)
        assertThat(changes).isEmpty()
    }

    @Test
    fun `tapping the row turns the page preview back on`() {
        show(SettingsUiState(loading = false, pagePreview = false))

        composeRule.onNode(isToggleable() and hasText("Page preview")).performScrollTo().assertIsOff().performClick()

        assertThat(previewChanges).containsExactly(true)
    }

    @Test
    fun `the page preview stays available before Android 12`() {
        show(SettingsUiState(loading = false), supported = false)

        composeRule
            .onNode(isToggleable() and hasText("Page preview"))
            .performScrollTo()
            .assertIsEnabled()
            .performClick()

        assertThat(previewChanges).containsExactly(false)
    }

    @Test
    fun `offers to ignore the document restrictions in the reading section, off by default`() {
        show(SettingsUiState(loading = false))

        val summary =
            "Lets you copy the text of documents and print them when their author forbids it. " +
                "Respect the rights of the author."
        composeRule.onNodeWithText(summary).performScrollTo().assertIsDisplayed()
        composeRule
            .onNode(isToggleable() and hasText("Ignore document restrictions"))
            .assertIsOff()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
    }

    @Test
    fun `tapping the row ignores the document restrictions, without touching the other settings`() {
        show(SettingsUiState(loading = false))

        composeRule.onNode(isToggleable() and hasText("Ignore document restrictions")).performScrollTo().performClick()

        assertThat(copyChanges).containsExactly(true)
        assertThat(previewChanges).isEmpty()
        assertThat(changes).isEmpty()
    }

    @Test
    fun `tapping the row again respects the document restrictions`() {
        show(SettingsUiState(loading = false, ignoreDocumentRestrictions = true))

        composeRule
            .onNode(isToggleable() and hasText("Ignore document restrictions"))
            .performScrollTo()
            .assertIsOn()
            .performClick()

        assertThat(copyChanges).containsExactly(false)
    }

    @Test
    @Config(qualifiers = "fr")
    fun `the document restrictions switch is translated in French`() {
        show(SettingsUiState(loading = false))

        composeRule
            .onNode(isToggleable() and hasText("Ignorer les restrictions du document"))
            .performScrollTo()
            .assertIsOff()
        val summary =
            "Permet de copier le texte et d’imprimer les documents dont l’auteur l’interdit. " +
                "Respectez les droits de l’auteur."
        composeRule.onNodeWithText(summary).assertIsDisplayed()
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
        composeRule.onNodeWithText("Lecture").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Aperçu de la page").performScrollTo().assertIsDisplayed()
        val summary =
            "Montre la page pendant que vous faites glisser le curseur du lecteur\u00A0; " +
                "le document s’y rend quand vous relâchez."
        composeRule.onNodeWithText(summary).performScrollTo().assertIsDisplayed()
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

    @Test
    fun `the last row opens the about screen`() {
        show(SettingsUiState(loading = false))

        composeRule.onNodeWithText("About and licenses").performScrollTo().assertHasClickAction().performClick()

        assertThat(aboutRequests).isEqualTo(1)
    }

    @Test
    fun `the about row is there even while the recent list is empty`() {
        show(SettingsUiState(loading = false, recentCount = 0))

        composeRule.onNodeWithText("About and licenses").performScrollTo().assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun `the app theme is a choice between light, dark and system, system by default`() {
        show(SettingsUiState(loading = false))

        composeRule.onNodeWithText("App theme").assertIsDisplayed()
        composeRule.onNode(hasText("System") and isSelectable()).assertIsSelected()
        composeRule.onNode(hasText("Light") and isSelectable()).assertIsNotSelected()
        composeRule.onNode(hasText("Dark") and isSelectable()).assertIsNotSelected().performClick()

        assertThat(themeChanges).containsExactly(AppTheme.DARK)
        assertThat(changes).isEmpty()
    }

    @Test
    fun `the stored app theme is shown selected`() {
        show(SettingsUiState(loading = false, appTheme = AppTheme.LIGHT))

        composeRule.onNode(hasText("Light") and isSelectable()).assertIsSelected()
        composeRule.onNode(hasText("System") and isSelectable()).assertIsNotSelected()
    }

    @Test
    fun `documents do not follow the dark theme by default, and the row turns it on`() {
        show(SettingsUiState(loading = false))

        composeRule
            .onNode(isToggleable() and hasText("Follow the dark theme"))
            .performScrollTo()
            .assertIsOff()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .performClick()

        assertThat(followChanges).containsExactly(true)
        assertThat(changes).isEmpty()
        assertThat(themeChanges).isEmpty()
    }

    @Test
    fun `the dark theme follow can be turned off again`() {
        show(SettingsUiState(loading = false, followDarkTheme = true))

        composeRule
            .onNode(isToggleable() and hasText("Follow the dark theme"))
            .performScrollTo()
            .assertIsOn()
            .performClick()

        assertThat(followChanges).containsExactly(false)
    }

    @Test
    @Config(qualifiers = "fr")
    fun `the theme settings are translated in French`() {
        show(SettingsUiState(loading = false))

        composeRule.onNodeWithText("Thème de l’application").assertIsDisplayed()
        composeRule.onNode(hasText("Clair") and isSelectable()).assertIsNotSelected()
        composeRule.onNode(hasText("Sombre") and isSelectable()).assertIsNotSelected()
        composeRule.onNode(hasText("Système") and isSelectable()).assertIsSelected()
        composeRule.onNode(isToggleable() and hasText("Suivre le thème sombre")).performScrollTo().assertIsOff()
    }

    @Test
    @Config(fontScale = 2f)
    fun `with a large font the app theme is a list of radio buttons`() {
        show(SettingsUiState(loading = false))

        composeRule
            .onNode(hasText("System") and isSelectable())
            .assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        composeRule.onNode(hasText("Light") and isSelectable()).assertIsNotSelected().performClick()

        assertThat(themeChanges).containsExactly(AppTheme.LIGHT)
    }

    @Test
    fun `keeping the screen on is in the reading section, off by default`() {
        show(SettingsUiState(loading = false))

        composeRule
            .onNode(isToggleable() and hasText("Keep the screen on"))
            .performScrollTo()
            .assertIsOff()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        composeRule.onNodeWithText("While you read a document").assertExists()
    }

    @Test
    fun `tapping the row keeps the screen on, without touching the other settings`() {
        show(SettingsUiState(loading = false))

        composeRule.onNode(isToggleable() and hasText("Keep the screen on")).performScrollTo().performClick()

        assertThat(keepOnChanges).containsExactly(true)
        assertThat(changes).isEmpty()
        assertThat(previewChanges).isEmpty()
        assertThat(copyChanges).isEmpty()
    }

    @Test
    fun `the stored keep screen on choice is shown and can be turned off`() {
        show(SettingsUiState(loading = false, keepScreenOn = true))

        composeRule
            .onNode(isToggleable() and hasText("Keep the screen on"))
            .performScrollTo()
            .assertIsOn()
            .performClick()

        assertThat(keepOnChanges).containsExactly(false)
    }

    @Test
    @Config(qualifiers = "fr")
    fun `keeping the screen on is translated in French`() {
        show(SettingsUiState(loading = false))

        composeRule.onNode(isToggleable() and hasText("Garder l’écran allumé")).performScrollTo().assertIsOff()
        composeRule.onNodeWithText("Pendant la lecture d’un document").assertExists()
    }

    @Test
    fun `there is no language row where the system does not offer the choice`() {
        show(SettingsUiState(loading = false, appLanguage = null))

        composeRule.onNodeWithText("App language").assertDoesNotExist()
    }

    @Test
    fun `the app language is a choice between the system, French and English`() {
        show(SettingsUiState(loading = false, appLanguage = AppLanguage.SYSTEM))

        composeRule.onNodeWithText("App language").assertIsDisplayed()
        composeRule.onAllNodes(hasText("System") and isSelectable()).assertCountEquals(2)
        composeRule.onNode(hasText("Français") and isSelectable()).assertIsNotSelected()
        composeRule.onNode(hasText("English") and isSelectable()).assertIsNotSelected().performClick()

        assertThat(languageChanges).containsExactly(AppLanguage.ENGLISH)
        assertThat(themeChanges).isEmpty()
    }

    @Test
    fun `the stored app language is shown selected`() {
        show(SettingsUiState(loading = false, appLanguage = AppLanguage.FRENCH))

        composeRule.onNode(hasText("Français") and isSelectable()).assertIsSelected()
        composeRule.onNode(hasText("English") and isSelectable()).assertIsNotSelected()
    }

    @Test
    @Config(qualifiers = "fr")
    fun `the app language is translated in French and the languages keep their own names`() {
        show(SettingsUiState(loading = false, appLanguage = AppLanguage.ENGLISH))

        composeRule.onNodeWithText("Langue de l’application").assertIsDisplayed()
        composeRule.onNode(hasText("Français") and isSelectable()).assertIsNotSelected()
        composeRule.onNode(hasText("English") and isSelectable()).assertIsSelected()
    }

    @Test
    @Config(fontScale = 2f)
    fun `with a large font the app language is a list of radio buttons`() {
        show(SettingsUiState(loading = false, appLanguage = AppLanguage.SYSTEM))

        composeRule
            .onNode(hasText("English") and isSelectable())
            .performScrollTo()
            .assertIsNotSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .performClick()

        assertThat(languageChanges).containsExactly(AppLanguage.ENGLISH)
    }
}
