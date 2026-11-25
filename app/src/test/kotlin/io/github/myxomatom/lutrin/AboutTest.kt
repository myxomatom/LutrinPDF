// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.github.myxomatom.lutrin.di.AboutModule
import io.github.myxomatom.lutrin.feature.about.AboutViewModel
import io.github.myxomatom.lutrin.feature.about.LicensedComponent
import io.github.myxomatom.lutrin.feature.about.LicensesState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * « À propos et licences » (E4, ADR 0030) with the real data of the app: the list of licenses that
 * AboutLibraries generated at build time and the hand-made entries for what Gradle cannot see
 * (MuPDF and its native libraries, fonts), and the way in from the settings (E3).
 */
@HiltAndroidTest
@Config(application = HiltTestApplication::class, qualifiers = "w360dp-h900dp-xhdpi")
@RunWith(AndroidJUnit4::class)
class AboutTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var controller: ActivityController<MainActivity>? = null

    @After
    fun tearDown() {
        controller?.run { if (!get().isFinishing) pause().stop().destroy() }
    }

    private fun realComponents(): List<LicensedComponent> {
        val viewModel =
            AboutViewModel(
                AboutModule.provideAppVersion(context),
                AboutModule.provideLicensesSource(context),
                Dispatchers.Unconfined,
            )
        val state = runBlocking { viewModel.uiState.first { it.licenses !is LicensesState.Loading } }
        return (state.licenses as LicensesState.Loaded).components
    }

    private fun openAboutFromSettings() {
        controller =
            Robolectric.buildActivity(
                MainActivity::class.java,
                Intent(
                    Intent.ACTION_MAIN,
                ).addCategory(Intent.CATEGORY_LAUNCHER).setClass(context, MainActivity::class.java),
            ).setup()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNodeWithText("About and licenses").performScrollTo().performClick()
    }

    @Test
    fun `the generated list holds the libraries Gradle found and what it cannot see`() {
        val names = realComponents().map { it.name }

        assertThat(names).containsAtLeast("MuPDF", "Fraunces", "FreeType", "HarfBuzz", "OpenJPEG", "Brotli", "MuJS")
        assertThat(names).contains("URW++ Base 35 fonts (the 14 standard PDF fonts)")
        assertThat(names.any { it.contains("Compose", ignoreCase = true) }).isTrue()
        assertThat(names.size).isGreaterThan(MIN_COMPONENTS)
    }

    @Test
    fun `MuPDF is listed under the AGPL, the fonts under the OFL`() {
        val byName = realComponents().associateBy { it.name }

        assertThat(byName.getValue("MuPDF").licenses.map { it.name }).containsExactly("AGPL-3.0-or-later")
        assertThat(byName.getValue("MuPDF").version).isEqualTo("1.28.6")
        assertThat(byName.getValue("Fraunces").licenses.map { it.name }).containsExactly("OFL-1.1")
        val urw = byName.getValue("URW++ Base 35 fonts (the 14 standard PDF fonts)")
        assertThat(urw.licenses.single().text).contains("(URW)++ Design & Development")
        assertThat(byName.getValue("Fraunces").licenses.single().text).contains("Fraunces Project Authors")
    }

    @Test
    fun `every component names a license and carries its full text`() {
        realComponents().forEach { component ->
            assertThat(component.licenses).isNotEmpty()
            component.licenses.forEach { license ->
                assertThat(license.text).isNotNull()
                assertThat(license.text).isNotEmpty()
            }
        }
    }

    @Test
    fun `the libjpeg entry carries the sentence the IJG license asks for`() {
        val libjpeg = realComponents().single { it.name == "libjpeg (IJG)" }

        assertThat(libjpeg.description).contains("based in part on the work of the Independent JPEG Group")
    }

    @Test
    fun `the settings lead to the about screen and back`() {
        openAboutFromSettings()

        composeRule.onNodeWithText("No data collected").assertIsDisplayed()
        composeRule.onNodeWithText("Version", substring = true).assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("MuPDF"))
        composeRule.onNodeWithText("MuPDF").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("Appearance").assertIsDisplayed()
        assertThat(composeRule.onAllNodesWithText("No data collected").fetchSemanticsNodes()).isEmpty()
    }

    private companion object {
        /** Maven libraries of the release classpath, plus the hand-made entries: well over this. */
        const val MIN_COMPONENTS = 50
    }
}
