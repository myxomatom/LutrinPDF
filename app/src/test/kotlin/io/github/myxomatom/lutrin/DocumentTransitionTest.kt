// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import io.github.myxomatom.lutrin.core.ui.transition.SharedDocumentPage
import java.io.File
import javax.inject.Inject
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.w3c.dom.Element

/**
 * The thumbnail ↔ reader transition (amended ADR 0019) through the real activity and navigation,
 * with a fake engine: going to the reader is always a plain fade, with no shared element; once
 * the reader of a tapped document is settled, its page is linked and shrinks back into the
 * thumbnail; the other ways into the reader stay unlinked; going back, rotating or failing in the
 * middle never breaks the navigation. The animation itself cannot be seen here: the tests stop
 * the clock a few frames into it and look at which elements carry the shared element.
 */
@HiltAndroidTest
@Config(application = HiltTestApplication::class)
@RunWith(AndroidJUnit4::class)
class DocumentTransitionTest {
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
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        controllers.forEach { controller -> controller.run { if (!get().isFinishing) pause().stop().destroy() } }
    }

    private fun launch(intent: Intent): ActivityController<MainActivity> =
        Robolectric.buildActivity(MainActivity::class.java, intent.setClass(context, MainActivity::class.java))
            .setup()
            .also {
                controllers += it
                composeRule.waitForIdle()
            }

    private fun launcher() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

    private fun record(uri: String, name: String) = runBlocking {
        recents.recordOpening(DocumentOpening(uri, PAGE_COUNT, 0, fallbackName = name))
    }

    /** The library with the « Contrat de location » card and the « Facture » row. */
    private fun launchLibrary(): ActivityController<MainActivity> {
        record(OTHER_DOCUMENT, "Facture")
        record(DOCUMENT, "Contrat de location")
        return launch(launcher()).also { waitForText("Facture") }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(TIMEOUT_MS) { composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitForDescription(description: String) {
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Stops the clock and plays the first frames of what comes next. */
    private fun stopClock() {
        composeRule.mainClock.autoAdvance = false
    }

    private fun advanceFrames() {
        Snapshot.sendApplyNotifications()
        repeat(FRAMES) { composeRule.mainClock.advanceTimeByFrame() }
    }

    private fun finishTransitions() {
        composeRule.mainClock.advanceTimeBy(SETTLE_MS)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
    }

    /** The elements carrying the shared element: thumbnails and reader pages. */
    private fun sharedElements() =
        composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SharedDocumentPage), useUnmergedTree = true)
            .fetchSemanticsNodes()

    private fun assertNoSharedElement() {
        assertThat(sharedElements()).isEmpty()
    }

    /** Opens the card's document and waits until its reader is settled. */
    private fun openFromCard() {
        tapCard()
        waitForDescription(READER_PAGE_1)
        composeRule.waitForIdle()
    }

    private fun tapCard() =
        composeRule.onNodeWithContentDescription("Resume reading Contrat de location, page 1 of 6").performClick()

    private fun assertLibraryShown() {
        waitForText("Facture")
        composeRule.onNodeWithText("Lutrin").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription(READER_PAGE_1).fetchSemanticsNodes().let {
            assertThat(it).isEmpty()
        }
    }

    @Test
    fun `tapping the resume card fades into the reader, with no shared element on the way`() {
        launchLibrary()
        stopClock()

        tapCard()
        advanceFrames()

        assertNoSharedElement()
        finishTransitions()
        waitForDescription(READER_PAGE_1)
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitForIdle()
        assertLibraryShown()
    }

    @Test
    fun `tapping a row of the recent list only fades too`() {
        launchLibrary()
        stopClock()

        composeRule.onNodeWithText("Facture").performClick()
        advanceFrames()

        assertNoSharedElement()
        finishTransitions()
        waitForDescription(READER_PAGE_1)
    }

    @Test
    fun `once the reader is settled, its page shrinks back into the card on return`() {
        launchLibrary()
        openFromCard()
        composeRule.waitUntil(TIMEOUT_MS) { sharedElements().size == 1 }
        stopClock()

        composeRule.onNodeWithContentDescription("Back").performClick()
        advanceFrames()

        // The page of the reader and the thumbnail of the card, paired while the screens change.
        assertThat(sharedElements()).hasSize(2)
        finishTransitions()
        assertLibraryShown()
    }

    @Test
    fun `going back at once returns to the library, with nothing linked`() {
        val controller = launchLibrary()
        stopClock()
        tapCard()
        advanceFrames()

        controller.get().onBackPressedDispatcher.onBackPressed()
        advanceFrames()
        finishTransitions()

        assertLibraryShown()
        assertNoSharedElement()
    }

    @Test
    fun `rotating during the transition shows the reader, and back still returns to the library`() {
        val controller = launchLibrary()
        stopClock()
        tapCard()
        advanceFrames()

        composeRule.mainClock.autoAdvance = true
        controller.recreate()
        composeRule.waitForIdle()

        waitForDescription(READER_PAGE_1)
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitForIdle()
        assertLibraryShown()
    }

    @Test
    fun `a document that cannot be opened ends on the error screen, and back returns`() {
        launchLibrary()
        engine.result = Result.failure(PdfError.Unreadable())
        stopClock()

        tapCard()
        advanceFrames()
        assertNoSharedElement()
        finishTransitions()

        waitForText("This document cannot be opened")
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitForIdle()
        assertLibraryShown()
    }

    @Test
    fun `with animations removed, nothing is linked`() {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        launchLibrary()

        openFromCard()

        assertNoSharedElement()
    }

    @Test
    fun `a document picked in the file picker is not linked, even after a tap`() {
        val controller = launchLibrary()
        openFromCard()
        composeRule.onNodeWithContentDescription("Back").performClick()
        assertLibraryShown()

        composeRule.onNodeWithText("Open a file").performClick()
        composeRule.waitForIdle()
        val activity = shadowOf(controller.get())
        val picker = activity.nextStartedActivityForResult
        activity.receiveResult(picker.intent, Activity.RESULT_OK, Intent().setData(Uri.parse(DOCUMENT)))
        waitForDescription(READER_PAGE_1)
        composeRule.waitForIdle()

        assertNoSharedElement()
    }

    @Test
    fun `a document sent by another app is not linked`() {
        record(DOCUMENT, "Contrat de location")

        Robolectric.buildActivity(MainActivity::class.java, view()).setup().also { controllers += it }
        waitForDescription(READER_PAGE_1)
        composeRule.waitForIdle()

        assertNoSharedElement()
    }

    @Test
    fun `predictive back is turned on, so that the gesture can drive the transition`() {
        // Robolectric does not parse this flag: read the manifest of the app (the module directory
        // is the working directory of its unit tests).
        val application = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(File("src/main/AndroidManifest.xml"))
            .getElementsByTagName("application")
            .item(0) as Element

        assertThat(application.getAttributeNS(ANDROID_NAMESPACE, "enableOnBackInvokedCallback")).isEqualTo("true")
    }

    private fun view(): Intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(Uri.parse(DOCUMENT), "application/pdf")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        .setClass(context, MainActivity::class.java)

    private companion object {
        const val PAGE_COUNT = 6
        const val TIMEOUT_MS = 5_000L
        const val FRAMES = 3
        const val SETTLE_MS = 2_000L
        const val READER_PAGE_1 = "Document, page 1 of $PAGE_COUNT"
        const val DOCUMENT = "content://com.example.files/document/1"
        const val OTHER_DOCUMENT = "content://com.example.files/document/2"

        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    }
}
