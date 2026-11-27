// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin

import android.app.LocaleManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.LocaleList
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.testing.TestFiles
import io.github.myxomatom.lutrin.feature.reader.R as ReaderR
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The reading options end to end (ADR 0029), on the real activity: the orientation lock of the
 * display options (F17) asks the activity to keep its orientation and gives it back, and the
 * per-app language of Android 13 and later (F19) reaches the resources of the activity. The
 * choices are stored on the device: each test puts them back as it found them.
 */
@RunWith(AndroidJUnit4::class)
class ReadingOptionsInstrumentedTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val targetContext: Context = instrumentation.targetContext

    private fun launch(): ActivityScenario<MainActivity> = ActivityScenario.launch(
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(TestDocumentProvider.uriOf(TestFiles.Pdf.SIMPLE), PDF)
            .putExtra(EXTRA_PAGE, 1)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .setClass(targetContext, MainActivity::class.java),
    )

    private fun text(id: Int) = targetContext.getString(id)

    private fun openDisplayOptions() {
        val more = text(ReaderR.string.reader_more_options)
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.onAllNodesWithContentDescription(more).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription(more).performClick()
        composeRule.onNodeWithText(text(ReaderR.string.reader_options_title)).performClick()
    }

    private fun toggleOrientationLock() {
        composeRule
            .onNodeWithText(text(ReaderR.string.reader_options_lock_orientation))
            .performScrollTo()
            .performClick()
    }

    private fun requestedOrientation(scenario: ActivityScenario<MainActivity>): Int {
        var orientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        scenario.onActivity { orientation = it.requestedOrientation }
        return orientation
    }

    @After
    fun forgetTheLanguage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            targetContext.getSystemService(LocaleManager::class.java).applicationLocales =
                LocaleList.getEmptyLocaleList()
        }
    }

    @Test
    fun theOrientationLockKeepsTheOrientationAndTurnsOffAgain() {
        launch().use { scenario ->
            openDisplayOptions()
            assertThat(requestedOrientation(scenario)).isEqualTo(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)

            toggleOrientationLock()
            composeRule.waitUntil(TIMEOUT_MS) {
                requestedOrientation(scenario) == ActivityInfo.SCREEN_ORIENTATION_LOCKED
            }

            // Put the setting back, as the next tests expect it.
            toggleOrientationLock()
            composeRule.waitUntil(TIMEOUT_MS) {
                requestedOrientation(scenario) == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    fun theLanguageChosenForTheAppReachesItsResources() {
        val localeManager = targetContext.getSystemService(LocaleManager::class.java)
        localeManager.applicationLocales = LocaleList.forLanguageTags("fr")

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var title: String? = null
            scenario.onActivity { title = it.getString(ReaderR.string.reader_options_title) }
            assertThat(title).isEqualTo("Affichage")
        }

        localeManager.applicationLocales = LocaleList.forLanguageTags("en")

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var title: String? = null
            scenario.onActivity { title = it.getString(ReaderR.string.reader_options_title) }
            assertThat(title).isEqualTo("Display")
        }
    }

    private companion object {
        const val PDF = "application/pdf"
        const val TIMEOUT_MS = 15_000L
    }
}
