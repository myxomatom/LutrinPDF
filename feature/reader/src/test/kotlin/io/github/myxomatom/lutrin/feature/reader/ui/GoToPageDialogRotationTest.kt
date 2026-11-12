// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.StateRestorationTester
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.myxomatom.lutrin.core.testing.A4
import io.github.myxomatom.lutrin.core.testing.FakePdfDocument
import io.github.myxomatom.lutrin.core.ui.theme.LutrinTheme
import io.github.myxomatom.lutrin.feature.reader.ReaderUiState
import io.github.myxomatom.lutrin.feature.reader.render.PageRenderer
import kotlinx.coroutines.Dispatchers
import org.junit.Test
import org.junit.runner.RunWith

/** « Aller à la page » across the recreation of the activity, as a rotation does it. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class GoToPageDialogRotationTest {
    @Test
    fun `the dialog and what was typed survive a rotation`() = runComposeUiTest {
        val sizes = List(PAGES) { A4 }
        val state =
            ReaderUiState.Ready(
                title = "Contrat de location",
                pageSizes = sizes,
                renderer = PageRenderer(FakePdfDocument(pageSizes = sizes), sizes, Dispatchers.Unconfined),
            )
        val restoration = StateRestorationTester(this)
        restoration.setContent { LutrinTheme(dynamicColor = false) { ReaderScreen(state, onBack = {}) } }
        onNodeWithContentDescription("Page 1 of $PAGES").performClick()
        onNode(hasSetTextAction()).performTextReplacement("6")

        restoration.emulateSaveAndRestore()
        waitForIdle()

        onNode(isDialog()).assertIsDisplayed()
        onNode(hasSetTextAction()).assertTypedText("6")
        onNodeWithText("Go").assertIsEnabled()
    }

    private companion object {
        const val PAGES = 8
    }
}
