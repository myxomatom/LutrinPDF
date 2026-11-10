// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.github.myxomatom.lutrin.core.domain.pdf.PdfError
import io.github.myxomatom.lutrin.core.domain.recents.DocumentOpening
import io.github.myxomatom.lutrin.core.domain.recents.RecentDocumentsRepository
import io.github.myxomatom.lutrin.core.testing.A4
import io.github.myxomatom.lutrin.core.testing.FakePdfDocument
import io.github.myxomatom.lutrin.core.testing.FakePdfEngine
import javax.inject.Inject
import kotlinx.coroutines.flow.first
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
 * Recent documents (F11) and resuming reading (F12, ADR 0015) through the real activity,
 * navigation and Room database, with a fake engine; removing one from the library or from the
 * error screen, and clearing them from the settings (ADR 0016).
 */
@HiltAndroidTest
@Config(application = HiltTestApplication::class)
@RunWith(AndroidJUnit4::class)
class RecentDocumentsTest {
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
        engine.result = Result.success(FakePdfDocument(List(PAGE_COUNT) { A4 }, title = "Contrat de location"))
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

    /** Closes an activity, so that the next one is the only Compose hierarchy on screen. */
    private fun ActivityController<MainActivity>.finishActivity() {
        pause().stop().destroy()
        controllers -= this
        composeRule.waitForIdle()
    }

    private fun launcher() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

    private fun view(): Intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(Uri.parse(DOCUMENT), "application/pdf")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    private fun record(page: Int, uri: String = DOCUMENT, name: String = "Contrat de location") = runBlocking {
        recents.recordOpening(DocumentOpening(uri, PAGE_COUNT, page, fallbackName = name))
    }

    private fun storedUris(): List<String> = runBlocking { recents.recentDocuments.first().map { it.uri } }

    /** Waits for the removals, which run on the real threads of the application scope and Room. */
    private fun waitForStoredUris(vararg uris: String) {
        composeRule.waitUntil(TIMEOUT_MS) { storedUris() == uris.toList() }
    }

    private fun removeFromRowMenu(name: String) {
        composeRule.onNodeWithContentDescription("More actions for $name").performClick()
        composeRule.onNodeWithText("Remove from recents").performClick()
    }

    /** Room and DataStore work on real background threads, which `waitForIdle` does not wait for. */
    private fun waitForText(text: String) {
        composeRule.waitUntil(TIMEOUT_MS) { composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun assertReaderShows(page: Int) {
        val description = "Document, page $page of $PAGE_COUNT"
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription(description).assertExists()
    }

    @Test
    fun `a document opened from another app is listed in the library`() {
        val reader = launch(view())
        assertReaderShows(page = 1)
        composeRule.waitUntil(TIMEOUT_MS) { runBlocking { recents.findByUri(DOCUMENT) } != null }
        reader.finishActivity()

        launch(launcher())

        waitForText("Contrat de location")
        // The only document takes the resume card (ADR 0018).
        composeRule.onNodeWithContentDescription("Resume reading Contrat de location, page 1 of 6").assertIsDisplayed()
        composeRule.onNodeWithText("No recent documents").assertDoesNotExist()
    }

    @Test
    fun `tapping a recent document resumes at its saved page`() {
        record(page = 3)
        launch(launcher())
        waitForText("Contrat de location")

        composeRule.onNodeWithText("Contrat de location").performClick()

        assertReaderShows(page = 4)
        assertThat(engine.openedSources).isNotEmpty()
    }

    @Test
    fun `opening a known document again without a page resumes at its saved page`() {
        record(page = 2)

        launch(view())

        assertReaderShows(page = 3)
    }

    @Test
    fun `the page extra of another app wins over the saved page`() {
        record(page = 2)

        launch(view().putExtra(EXTRA_PAGE, 5))

        assertReaderShows(page = 5)
    }

    @Test
    fun `opening the same document twice keeps one entry`() {
        launch(view()).also { assertReaderShows(page = 1) }.finishActivity()
        launch(view())
        assertReaderShows(page = 1)

        val list = runBlocking { recents.recentDocuments.first() }
        assertThat(list.map { it.uri }).containsExactly(DOCUMENT)
    }

    @Test
    fun `a recent document that cannot be read any more shows the error screen`() {
        record(page = 0)
        engine.result = Result.failure(PdfError.NotAccessible())
        launch(launcher())
        waitForText("Contrat de location")

        composeRule.onNodeWithText("Contrat de location").performClick()

        waitForText("The file was moved or deleted, or access to it was revoked.")
        composeRule.onNodeWithText("This document cannot be opened").assertIsDisplayed()
    }

    @Test
    fun `open, go back and remove the document, and the list no longer shows it`() {
        val recorded = record(page = 0)
        launch(launcher())
        waitForText("Contrat de location")
        composeRule.onNodeWithText("Contrat de location").performClick()
        assertReaderShows(page = 1)
        // The reader records its opening: the removal below is asked for this opening.
        composeRule.waitUntil(TIMEOUT_MS) {
            runBlocking { recents.findByUri(DOCUMENT) }?.lastOpenedAt != recorded?.lastOpenedAt
        }
        composeRule.onNodeWithContentDescription("Back").performClick()
        waitForText("Contrat de location")

        removeFromRowMenu("Contrat de location")

        waitForText("No recent documents")
        composeRule.onNodeWithText("“Contrat de location” removed from recents").assertIsDisplayed()
        assertThat(storedUris()).containsExactly(DOCUMENT)
        composeRule.mainClock.advanceTimeBy(UNDO_TIMEOUT_MS)
        waitForStoredUris()
        composeRule.onNodeWithText("Contrat de location").assertDoesNotExist()
    }

    @Test
    fun `undoing a removal brings the document back and keeps it`() {
        record(page = 2)
        launch(launcher())
        waitForText("Contrat de location")

        removeFromRowMenu("Contrat de location")
        waitForText("No recent documents")
        composeRule.onNodeWithText("Undo").performClick()

        waitForText("Contrat de location")
        composeRule.mainClock.advanceTimeBy(UNDO_TIMEOUT_MS)
        composeRule.waitForIdle()
        assertThat(storedUris()).containsExactly(DOCUMENT)
        assertThat(runBlocking { recents.findByUri(DOCUMENT) }?.lastPage).isEqualTo(2)
    }

    @Test
    fun `a recent document that cannot be read any more can be removed from the error screen`() {
        record(page = 0)
        record(page = 0, uri = OTHER_DOCUMENT, name = "Facture")
        engine.result = Result.failure(PdfError.NotAccessible())
        launch(launcher())
        waitForText("Contrat de location")

        composeRule.onNodeWithText("Contrat de location").performClick()
        waitForText("Remove from recents")
        composeRule.onNodeWithText("Remove from recents").performClick()

        waitForText("Facture")
        waitForStoredUris(OTHER_DOCUMENT)
        composeRule.onNodeWithText("Contrat de location").assertDoesNotExist()
    }

    @Test
    fun `clearing the recents from the settings empties the library`() {
        record(page = 0)
        record(page = 0, uri = OTHER_DOCUMENT, name = "Facture")
        launch(launcher())
        waitForText("1 other document")

        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNodeWithText("Clear recent documents").performScrollTo().performClick()
        composeRule.onNodeWithText("Clear").performClick()

        waitForStoredUris()
        waitForText("No recent documents")
        composeRule.onNodeWithContentDescription("Back").performClick()
        waitForText("Open a file")
        composeRule.onNodeWithText("No recent documents").assertIsDisplayed()
    }

    private companion object {
        const val PAGE_COUNT = 6
        const val TIMEOUT_MS = 5_000L
        const val DOCUMENT = "content://com.example.files/document/1"
        const val OTHER_DOCUMENT = "content://com.example.files/document/2"

        /** Longer than the message offering to undo a removal (10 s). */
        const val UNDO_TIMEOUT_MS = 11_000L
    }
}
