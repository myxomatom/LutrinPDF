// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.github.myxomatom.lutrin.core.pdf.DocumentSource
import io.github.myxomatom.lutrin.core.testing.A4
import io.github.myxomatom.lutrin.core.testing.FakePdfDocument
import io.github.myxomatom.lutrin.core.testing.FakePdfEngine
import javax.inject.Inject
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * Documents sent by other apps (F01, ADR 0014): `ACTION_VIEW` and `ACTION_SEND` reaching the
 * real activity and navigation, with a fake engine.
 *
 * The activity is driven by Robolectric's synchronous [ActivityController]: `ActivityScenario`
 * waits for lifecycle changes on the main thread, where the Compose test rule runs the test,
 * and never returns.
 */
@HiltAndroidTest
@Config(application = HiltTestApplication::class)
@RunWith(AndroidJUnit4::class)
class MainActivityIncomingIntentTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    @Inject
    lateinit var engine: FakePdfEngine

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var controller: ActivityController<MainActivity>? = null

    @Before
    fun setUp() {
        hiltRule.inject()
        engine.result = Result.success(FakePdfDocument(List(PAGE_COUNT) { A4 }))
    }

    @After
    fun tearDown() {
        controller?.run { if (!get().isFinishing) pause().stop().destroy() }
    }

    private fun launch(intent: Intent): ActivityController<MainActivity> =
        Robolectric.buildActivity(MainActivity::class.java, intent.setClass(context, MainActivity::class.java))
            .setup()
            .also {
                controller = it
                composeRule.waitForIdle()
            }

    private fun view(uri: String = DOCUMENT, type: String? = PDF): Intent =
        Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(uri), type).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    private fun send(stream: Uri? = Uri.parse(DOCUMENT)): Intent =
        Intent(Intent.ACTION_SEND).setType(PDF).apply { stream?.let { putExtra(Intent.EXTRA_STREAM, it) } }

    private fun ActivityController<MainActivity>.deliver(intent: Intent) {
        newIntent(intent)
        composeRule.waitForIdle()
    }

    private fun ActivityController<MainActivity>.activity(): Activity = get()

    private fun openedUris(): List<String> = engine.openedSources.map { (it as DocumentSource.FromUri).uri.toString() }

    /**
     * Waits for the reader: its state also needs the settings, read by DataStore on a real I/O
     * thread that `waitForIdle` does not wait for.
     */
    private fun assertReaderShows(page: Int) {
        val description = "Document, page $page of $PAGE_COUNT"
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription(description).assertExists()
    }

    private fun assertLibraryShows(message: String) {
        composeRule.onNodeWithText("No recent documents").assertIsDisplayed()
        composeRule.onNodeWithText(message).assertIsDisplayed()
    }

    @Test
    fun `open with shows the document in the reader`() {
        launch(view())

        assertThat(openedUris()).containsExactly(DOCUMENT)
        assertReaderShows(page = 1)
    }

    @Test
    fun `open with starts at the page of the page extra`() {
        launch(view().putExtra(EXTRA_PAGE, 3))

        assertReaderShows(page = 3)
    }

    @Test
    fun `a page beyond the document opens the last page`() {
        launch(view().putExtra(EXTRA_PAGE, 99))

        assertReaderShows(page = PAGE_COUNT)
    }

    @Test
    fun `an invalid page extra opens the first page`() {
        launch(view().putExtra(EXTRA_PAGE, "3"))

        assertReaderShows(page = 1)
    }

    @Test
    fun `sharing a PDF shows it in the reader`() {
        launch(send())

        assertThat(openedUris()).containsExactly(DOCUMENT)
        assertReaderShows(page = 1)
    }

    @Test
    fun `back from a document of another app leaves the activity`() {
        val activity = launch(view()).activity()
        assertReaderShows(page = 1)

        composeRule.onNodeWithContentDescription("Back").performClick()

        assertThat(activity.isFinishing).isTrue()
    }

    @Test
    fun `rotation does not open the document again`() {
        val controller = launch(view())

        // Recreation as after a rotation: saved state, new activity, same ViewModels.
        this.controller = controller.recreate()
        composeRule.waitForIdle()

        assertThat(openedUris()).containsExactly(DOCUMENT)
        assertReaderShows(page = 1)
    }

    @Test
    fun `a new intent while reading replaces the document`() {
        val first = FakePdfDocument(List(PAGE_COUNT) { A4 })
        engine.result = Result.success(first)
        val controller = launch(view())
        engine.result = Result.success(FakePdfDocument(List(PAGE_COUNT) { A4 }))

        controller.deliver(view(OTHER_DOCUMENT).putExtra(EXTRA_PAGE, 2))

        assertReaderShows(page = 2)
        assertThat(openedUris()).containsExactly(DOCUMENT, OTHER_DOCUMENT).inOrder()
        assertThat(first.isClosed).isTrue()
    }

    @Test
    fun `back from a document received by a new intent still leaves the activity`() {
        val controller = launch(view())
        controller.deliver(view(OTHER_DOCUMENT))
        val activity = controller.activity()
        assertReaderShows(page = 1)

        composeRule.onNodeWithContentDescription("Back").performClick()

        assertThat(activity.isFinishing).isTrue()
    }

    @Test
    fun `a file URI is refused with a message on the library`() {
        launch(view("file:///sdcard/Download/a.pdf"))

        assertThat(engine.openedSources).isEmpty()
        assertLibraryShows("This link cannot be opened. Open the document from your files, or with “Open a file”.")
    }

    @Test
    fun `another type is refused with a message on the library`() {
        launch(view(type = "image/png"))

        assertThat(engine.openedSources).isEmpty()
        assertLibraryShows("Lutrin only opens PDF documents.")
    }

    @Test
    @Config(qualifiers = "fr")
    fun `the refusal is explained in French`() {
        launch(view(type = "image/png"))

        composeRule.onNodeWithText("Lutrin n’ouvre que les documents PDF.").assertIsDisplayed()
    }

    @Test
    fun `an invalid new intent while reading returns to the library`() {
        val controller = launch(view())

        controller.deliver(send(stream = null))

        assertLibraryShows("No document was received from the other app.")
    }

    @Test
    fun `a launch from recents does not open the document again`() {
        launch(view().addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY))

        assertThat(engine.openedSources).isEmpty()
        composeRule.onNodeWithText("No recent documents").assertIsDisplayed()
    }

    @Test
    fun `the launcher still opens the library`() {
        launch(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER))

        assertThat(engine.openedSources).isEmpty()
        composeRule.onNodeWithText("No recent documents").assertIsDisplayed()
    }

    private companion object {
        const val PDF = "application/pdf"
        const val PAGE_COUNT = 5
        const val TIMEOUT_MS = 5_000L
        const val DOCUMENT = "content://com.example.files/document/1"
        const val OTHER_DOCUMENT = "content://com.example.files/document/2"
    }
}
