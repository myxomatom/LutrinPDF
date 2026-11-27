// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.library

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.myxomatom.lutrin.core.testing.assertControlsAreAccessible
import io.github.myxomatom.lutrin.core.ui.theme.LutrinTheme
import java.time.LocalTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * E1 against the accessibility rules of `AGENTS.md` §11 (F18): in every state, every control has a
 * name for TalkBack and a touch target of 48 dp, also at 200 % and with the menu of a row open.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w390dp-h2500dp-xhdpi")
class LibraryAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val resume = item("1", "Contrat de location", lastPage = 2, pageCount = 8)
    private val others = listOf(
        item("2", "Facture octobre 2026", lastPage = 0, pageCount = 2),
        item("3", "Relevé de compte", lastPage = 3, pageCount = 4, protected = true),
    )

    private fun item(id: String, name: String, lastPage: Int, pageCount: Int, protected: Boolean = false) =
        RecentDocumentItem(
            documentId = id,
            uri = "content://com.example.files/document/$id",
            name = name,
            pageCount = pageCount,
            lastPage = lastPage,
            openedOn = OpenedOn.Today(LocalTime.of(20, 41)),
            thumbnailPath = if (protected) null else "/thumbnails/$id.png",
            isProtected = protected,
            isReadingInProgress = lastPage < pageCount - 1,
        )

    private fun show(state: LibraryUiState) {
        composeRule.setContent {
            LutrinTheme(dynamicColor = false) {
                LibraryScreen(state = state, onOpenFile = {}, onOpenSettings = {}, onOpenDocument = {})
            }
        }
    }

    @Test
    fun `the library with recent documents has named controls of 48 dp`() {
        show(LibraryUiState.Content(resume, others))

        composeRule.assertControlsAreAccessible()
    }

    @Test
    @Config(fontScale = 2f)
    fun `the library with recent documents stays accessible at 200 percent`() {
        show(LibraryUiState.Content(resume, others))

        composeRule.assertControlsAreAccessible()
    }

    @Test
    fun `the menu of a document is accessible`() {
        show(LibraryUiState.Content(resume, others))
        composeRule.onAllNodesWithContentDescription("More actions", substring = true).onFirst().performClick()
        composeRule.waitForIdle()

        composeRule.assertControlsAreAccessible()
    }

    @Test
    fun `the empty library is accessible`() {
        show(LibraryUiState.Empty)

        composeRule.assertControlsAreAccessible()
    }

    @Test
    fun `the library that cannot be read is accessible`() {
        show(LibraryUiState.Error)

        composeRule.assertControlsAreAccessible()
    }
}
