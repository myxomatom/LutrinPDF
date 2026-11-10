// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.github.myxomatom.lutrin.core.domain.recents.RecentDocumentsRepository
import io.github.myxomatom.lutrin.core.testing.A4
import io.github.myxomatom.lutrin.core.testing.FakePdfDocument
import io.github.myxomatom.lutrin.core.testing.FakePdfEngine
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * Password-protected documents (F10, V4, ADR 0017) through the real activity, navigation and Room
 * database, with a fake engine encrypted with a known test password: asked, refused, opened,
 * listed with its lock, asked again from the library, resumed at its page.
 */
@HiltAndroidTest
@Config(application = HiltTestApplication::class)
@RunWith(AndroidJUnit4::class)
class ProtectedDocumentsTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    @Inject
    lateinit var engine: FakePdfEngine

    @Inject
    lateinit var recents: RecentDocumentsRepository

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val controllers = mutableListOf<ActivityController<MainActivity>>()

    @Before
    fun setUp() {
        hiltRule.inject()
        engine.result = Result.success(FakePdfDocument(List(PAGE_COUNT) { A4 }, title = "Relevé de compte"))
        engine.password = PASSWORD
    }

    @After
    fun tearDown() {
        controllers.forEach { controller -> controller.run { if (!get().isFinishing) pause().stop().destroy() } }
    }

    private fun launch(intent: Intent): ActivityController<MainActivity> =
        Robolectric.buildActivity(MainActivity::class.java, intent.setClass(context, MainActivity::class.java))
            .setup()
            .also {
                controllers += it
                composeRule.waitForIdle()
            }

    private fun ActivityController<MainActivity>.finishActivity() {
        pause().stop().destroy()
        controllers -= this
        composeRule.waitForIdle()
    }

    private fun view(): Intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(Uri.parse(DOCUMENT), "application/pdf")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    private fun launcher() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

    private fun waitForText(text: String) {
        composeRule.waitUntil(TIMEOUT_MS) { composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun assertReaderShows(page: Int) {
        val description = "Document, page $page of $PAGE_COUNT"
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun typePassword(password: String) {
        val field = composeRule.onNode(hasSetTextAction())
        field.performTextClearance()
        field.performTextInput(password)
    }

    private fun waitForStoredPage(page: Int) {
        composeRule.waitUntil(TIMEOUT_MS) { runBlocking { recents.findByUri(DOCUMENT) }?.lastPage == page }
    }

    @Test
    fun `asked, refused, opened, listed with a lock, asked again and resumed`() {
        // Opened from another app: asked, a wrong password, then the right one.
        val reader = launch(view())
        waitForText("Protected document")
        typePassword("wrong")
        composeRule.onNode(hasSetTextAction()).performImeAction()
        waitForText("Incorrect password. Try again.")
        typePassword(PASSWORD)
        composeRule.onNodeWithText("Open").performClick()
        assertReaderShows(page = 1)
        // Reads up to page 4, then leaves: the page is saved when the reader closes.
        composeRule.onNodeWithContentDescription("Go to page").performSemanticsAction(SemanticsActions.SetProgress) {
            it(3f)
        }
        assertReaderShows(page = 4)
        reader.finishActivity()
        waitForStoredPage(3)
        assertThat(runBlocking { recents.findByUri(DOCUMENT) }?.isProtected).isTrue()

        // From the library: the lock, the question again, and the saved page.
        launch(launcher())
        waitForText("Relevé de compte")
        composeRule.onNodeWithContentDescription("Protected document", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Relevé de compte").performClick()
        waitForText("“Relevé de compte” is encrypted. Enter its password to open it.")
        typePassword(PASSWORD)
        composeRule.onNode(hasSetTextAction()).performImeAction()

        assertReaderShows(page = 4)
        assertThat(engine.givenPasswords).containsExactly(null, "wrong", PASSWORD, null, PASSWORD).inOrder()
    }

    @Test
    fun `Cancel goes back to the library and records nothing new`() {
        launch(view()).also { reader ->
            waitForText("Protected document")
            typePassword(PASSWORD)
            composeRule.onNodeWithText("Open").performClick()
            assertReaderShows(page = 1)
            reader.finishActivity()
        }
        launch(launcher())
        waitForText("Relevé de compte")
        composeRule.onNodeWithText("Relevé de compte").performClick()
        waitForText("Protected document")

        composeRule.onNodeWithText("Cancel").performClick()

        // The only recent document is in the resume card (ADR 0018), without « Recent » header.
        waitForText("RESUME READING")
        composeRule.onNodeWithText("Relevé de compte").assertIsDisplayed()
        composeRule.onNodeWithText("Protected document").assertDoesNotExist()
    }

    @Test
    fun `a protected document never gets a thumbnail`() {
        launch(view()).also { reader ->
            waitForText("Protected document")
            typePassword(PASSWORD)
            composeRule.onNodeWithText("Open").performClick()
            assertReaderShows(page = 1)
            reader.finishActivity()
        }
        val openings = engine.openedSources.size

        launch(launcher())
        waitForText("Relevé de compte")
        composeRule.waitForIdle()

        // The library shows the row without opening the document to draw it.
        assertThat(engine.openedSources).hasSize(openings)
        assertThat(runBlocking { recents.findByUri(DOCUMENT) }?.thumbnailPath).isNull()
    }

    private companion object {
        const val PAGE_COUNT = 6
        const val TIMEOUT_MS = 5_000L
        const val DOCUMENT = "content://com.example.files/document/statement"

        /** Password of the fake encrypted document: test data, not a secret. */
        const val PASSWORD = "lutrin"
    }
}
