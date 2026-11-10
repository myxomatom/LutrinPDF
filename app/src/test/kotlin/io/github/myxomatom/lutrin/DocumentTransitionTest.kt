// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
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
 * The thumbnail → reader transition (ADR 0019) through the real activity and navigation, with a
 * fake engine: which ways into the reader grow the thumbnail (the opening page shows during the
 * transition), which only fade, and that going back, rotating or failing in the middle never
 * breaks the navigation. The animation itself cannot be seen here: the tests stop the clock a
 * few frames into it and look at what is composed.
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

    private fun openingPage() = composeRule.onNodeWithTag(OPENING_PAGE_TAG)

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
    fun `tapping the resume card grows its page into the reader, and back returns to the library`() {
        launchLibrary()
        stopClock()

        tapCard()
        advanceFrames()

        openingPage().assertExists()
        finishTransitions()
        waitForDescription(READER_PAGE_1)
        openingPage().assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitForIdle()
        assertLibraryShown()
    }

    @Test
    fun `tapping a row of the recent list grows its thumbnail too`() {
        launchLibrary()
        stopClock()

        composeRule.onNodeWithText("Facture").performClick()
        advanceFrames()

        openingPage().assertExists()
        finishTransitions()
        waitForDescription(READER_PAGE_1)
    }

    @Test
    fun `going back at once returns to the library`() {
        val controller = launchLibrary()
        stopClock()
        tapCard()
        advanceFrames()

        controller.get().onBackPressedDispatcher.onBackPressed()
        advanceFrames()
        finishTransitions()

        assertLibraryShown()
        openingPage().assertDoesNotExist()
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
        openingPage().assertDoesNotExist()
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
        finishTransitions()

        waitForText("This document cannot be opened")
        openingPage().assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitForIdle()
        assertLibraryShown()
    }

    @Test
    fun `with animations removed, the reader opens without the opening page`() {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        launchLibrary()
        stopClock()

        tapCard()
        advanceFrames()

        openingPage().assertDoesNotExist()
        finishTransitions()
        waitForDescription(READER_PAGE_1)
    }

    @Test
    fun `a document picked in the file picker opens without the opening page, even after a tap`() {
        val controller = launchLibrary()
        tapCard()
        waitForDescription(READER_PAGE_1)
        composeRule.onNodeWithContentDescription("Back").performClick()
        assertLibraryShown()

        composeRule.onNodeWithText("Open a file").performClick()
        composeRule.waitForIdle()
        val activity = shadowOf(controller.get())
        val picker = activity.nextStartedActivityForResult
        stopClock()
        activity.receiveResult(picker.intent, Activity.RESULT_OK, Intent().setData(Uri.parse(DOCUMENT)))
        advanceFrames()

        openingPage().assertDoesNotExist()
        finishTransitions()
        waitForDescription(READER_PAGE_1)
    }

    @Test
    fun `a document sent by another app opens without the opening page`() {
        record(DOCUMENT, "Contrat de location")
        stopClock()

        Robolectric.buildActivity(MainActivity::class.java, view()).setup().also { controllers += it }
        advanceFrames()

        openingPage().assertDoesNotExist()
        finishTransitions()
        waitForDescription(READER_PAGE_1)
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

        /** `OPENING_PAGE_TAG` of `feature:reader`, internal to that module. */
        const val OPENING_PAGE_TAG = "openingPage"

        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    }
}
