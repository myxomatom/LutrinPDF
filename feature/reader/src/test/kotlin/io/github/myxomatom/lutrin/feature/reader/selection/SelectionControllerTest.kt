// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.selection

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.pdf.PdfPoint
import io.github.myxomatom.lutrin.core.domain.pdf.PdfQuad
import io.github.myxomatom.lutrin.core.domain.pdf.PdfRect
import io.github.myxomatom.lutrin.core.domain.pdf.SelectionMode
import io.github.myxomatom.lutrin.core.domain.pdf.TextSelection
import io.github.myxomatom.lutrin.core.testing.A4
import io.github.myxomatom.lutrin.core.testing.FakePdfDocument
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** The text selection of the reader (F08, ADR 0024), on a fake document. */
@OptIn(ExperimentalCoroutinesApi::class)
class SelectionControllerTest {
    private val dispatcher = StandardTestDispatcher()
    private val document = FakePdfDocument(List(PAGE_COUNT) { A4 })
    private var canCopy = true
    private var isProtected = false

    private val word = selection("bail", left = 100f, right = 130f)
    private val longer = selection("bail de location", left = 100f, right = 220f)
    private val wholePage = selection("Contrat\nbail de location", left = 72f, right = 400f)

    private fun selection(text: String, left: Float, right: Float, page: Int = 1) = TextSelection(
        pageIndex = page,
        quads = listOf(PdfQuad.of(PdfRect(left, 200f, right, 214f))),
        text = text,
        start = PdfPoint(left, 214f),
        end = PdfPoint(right, 214f),
    )

    private fun TestScope.controller() = SelectionController(this, dispatcher, document = {
        document
    }, canCopy = { canCopy }, isProtected = { isProtected })

    /** Page 1 has text around [word]; the other pages have none (scanned). */
    private fun givenText(selection: TextSelection = word) {
        document.textAreas = mapOf(1 to listOf(PdfRect(72f, 190f, 500f, 220f)))
        document.selections = mapOf(1 to selection)
    }

    @Test
    fun `a long press on a word selects it`() = runTest(dispatcher) {
        givenText()
        val controller = controller()

        val selected = controller.onLongPress(1, PdfPoint(110f, 205f))

        assertThat(selected).isTrue()
        assertThat(controller.selection.value).isEqualTo(word)
        val request = document.selectionRequests.single()
        assertThat(request.mode).isEqualTo(SelectionMode.Words)
        assertThat(request.start).isEqualTo(PdfPoint(110f, 205f))
        assertThat(request.end).isEqualTo(PdfPoint(110f, 205f))
    }

    @Test
    fun `a long press outside the text selects nothing`() = runTest(dispatcher) {
        givenText()
        val controller = controller()

        val selected = controller.onLongPress(1, PdfPoint(20f, 20f))

        assertThat(selected).isFalse()
        assertThat(controller.selection.value).isNull()
        assertThat(document.selectionRequests).isEmpty()
    }

    @Test
    fun `a long press on a scanned page selects nothing`() = runTest(dispatcher) {
        document.textAreas = mapOf(0 to listOf(PdfRect(0f, 0f, A4.width, A4.height)))
        val controller = controller()

        assertThat(controller.onLongPress(0, PdfPoint(110f, 205f))).isFalse()
        assertThat(controller.selection.value).isNull()
    }

    @Test
    fun `a long press on a closed document selects nothing`() = runTest(dispatcher) {
        givenText()
        val controller = controller()
        document.close()

        assertThat(controller.onLongPress(1, PdfPoint(110f, 205f))).isFalse()
    }

    @Test
    fun `a long press elsewhere outside the text keeps the selection`() = runTest(dispatcher) {
        givenText()
        val controller = controller()
        controller.onLongPress(1, PdfPoint(110f, 205f))

        controller.onLongPress(1, PdfPoint(20f, 20f))

        assertThat(controller.selection.value).isEqualTo(word)
    }

    @Test
    fun `dragging the end handle selects from the start, character by character`() = runTest(dispatcher) {
        givenText()
        val controller = controller()
        controller.onLongPress(1, PdfPoint(110f, 205f))
        document.selections = mapOf(1 to longer)

        controller.selection.test {
            assertThat(awaitItem()).isEqualTo(word)
            controller.onHandleDragStart(SelectionHandle.End)
            controller.onHandleDrag(PdfPoint(220f, 214f))
            assertThat(awaitItem()).isEqualTo(longer)
            controller.onHandleDragEnd()
        }
        val request = document.selectionRequests.last()
        assertThat(request.mode).isEqualTo(SelectionMode.Characters)
        assertThat(request.start).isEqualTo(word.start)
        assertThat(request.end).isEqualTo(PdfPoint(220f, 214f))
    }

    @Test
    fun `dragging the start handle keeps the end where it is`() = runTest(dispatcher) {
        givenText()
        val controller = controller()
        controller.onLongPress(1, PdfPoint(110f, 205f))

        controller.onHandleDragStart(SelectionHandle.Start)
        controller.onHandleDrag(PdfPoint(80f, 214f))
        advanceUntilIdle()

        val request = document.selectionRequests.last()
        assertThat(request.start).isEqualTo(word.end)
        assertThat(request.end).isEqualTo(PdfPoint(80f, 214f))
    }

    @Test
    fun `positions arriving while the text is read are merged into the latest`() = runTest(dispatcher) {
        givenText()
        val controller = controller()
        controller.onLongPress(1, PdfPoint(110f, 205f))
        val before = document.selectionRequests.size

        controller.onHandleDragStart(SelectionHandle.End)
        controller.onHandleDrag(PdfPoint(150f, 214f))
        controller.onHandleDrag(PdfPoint(180f, 214f))
        controller.onHandleDrag(PdfPoint(220f, 214f))
        advanceUntilIdle()

        val requests = document.selectionRequests.drop(before)
        assertThat(requests.map { it.end }).containsExactly(PdfPoint(220f, 214f))
    }

    @Test
    fun `a drag that selects nothing keeps the last selection`() = runTest(dispatcher) {
        givenText()
        val controller = controller()
        controller.onLongPress(1, PdfPoint(110f, 205f))
        document.selections = emptyMap()

        controller.onHandleDragStart(SelectionHandle.End)
        controller.onHandleDrag(PdfPoint(100f, 214f))
        advanceUntilIdle()

        assertThat(controller.selection.value).isEqualTo(word)
    }

    @Test
    fun `a drag without a selection is ignored`() = runTest(dispatcher) {
        val controller = controller()

        controller.onHandleDragStart(SelectionHandle.End)
        controller.onHandleDrag(PdfPoint(100f, 214f))
        advanceUntilIdle()

        assertThat(document.selectionRequests).isEmpty()
    }

    @Test
    fun `select all takes every line of the page`() = runTest(dispatcher) {
        givenText()
        val controller = controller()
        controller.onLongPress(1, PdfPoint(110f, 205f))
        document.selections = mapOf(1 to wholePage)

        controller.onSelectAll()
        advanceUntilIdle()

        assertThat(controller.selection.value).isEqualTo(wholePage)
        val request = document.selectionRequests.last()
        assertThat(request.mode).isEqualTo(SelectionMode.Lines)
        assertThat(request.start).isEqualTo(PdfPoint(0f, 0f))
        assertThat(request.end).isEqualTo(PdfPoint(A4.width, A4.height))
    }

    @Test
    fun `copy puts the text on the clipboard and closes the selection`() = runTest(dispatcher) {
        givenText()
        val controller = controller()
        controller.onLongPress(1, PdfPoint(110f, 205f))

        controller.effects.test {
            controller.onCopy()
            assertThat(awaitItem()).isEqualTo(SelectionEffect.Copy("bail", isSensitive = false))
        }
        assertThat(controller.selection.value).isNull()
    }

    @Test
    fun `the text of a protected document is copied as sensitive`() = runTest(dispatcher) {
        givenText()
        isProtected = true
        val controller = controller()
        controller.onLongPress(1, PdfPoint(110f, 205f))

        controller.effects.test {
            controller.onCopy()
            assertThat(awaitItem()).isEqualTo(SelectionEffect.Copy("bail", isSensitive = true))
        }
    }

    @Test
    fun `a forbidden copy is refused and the selection stays`() = runTest(dispatcher) {
        givenText()
        canCopy = false
        val controller = controller()
        controller.onLongPress(1, PdfPoint(110f, 205f))

        controller.effects.test {
            controller.onCopy()
            assertThat(awaitItem()).isEqualTo(SelectionEffect.CopyRefused)
        }
        assertThat(controller.selection.value).isEqualTo(word)
    }

    @Test
    fun `the copy permission is read at each copy`() = runTest(dispatcher) {
        givenText()
        canCopy = false
        val controller = controller()
        controller.onLongPress(1, PdfPoint(110f, 205f))

        controller.effects.test {
            controller.onCopy()
            assertThat(awaitItem()).isEqualTo(SelectionEffect.CopyRefused)
            canCopy = true
            controller.onCopy()
            assertThat(awaitItem()).isEqualTo(SelectionEffect.Copy("bail", isSensitive = false))
        }
    }

    @Test
    fun `copying the page copies its whole text without showing a selection`() = runTest(dispatcher) {
        givenText(wholePage)
        val controller = controller()

        controller.effects.test {
            controller.onCopyPage(1)
            assertThat(awaitItem()).isEqualTo(SelectionEffect.Copy("Contrat\nbail de location", isSensitive = false))
        }
        assertThat(controller.selection.value).isNull()
        assertThat(document.selectionRequests.single().mode).isEqualTo(SelectionMode.Lines)
    }

    @Test
    fun `copying a page without text says so`() = runTest(dispatcher) {
        val controller = controller()

        controller.effects.test {
            controller.onCopyPage(0)
            assertThat(awaitItem()).isEqualTo(SelectionEffect.NothingToCopy)
        }
    }

    @Test
    fun `copying the page of a document that forbids it is refused without reading it`() = runTest(dispatcher) {
        givenText(wholePage)
        canCopy = false
        val controller = controller()

        controller.effects.test {
            controller.onCopyPage(1)
            assertThat(awaitItem()).isEqualTo(SelectionEffect.CopyRefused)
        }
        assertThat(document.selectionRequests).isEmpty()
    }

    @Test
    fun `clearing closes the selection and stops a drag`() = runTest(dispatcher) {
        givenText()
        val controller = controller()
        controller.onLongPress(1, PdfPoint(110f, 205f))
        controller.onHandleDragStart(SelectionHandle.End)

        controller.onClear()
        controller.onHandleDrag(PdfPoint(220f, 214f))
        advanceUntilIdle()

        assertThat(controller.selection.value).isNull()
        assertThat(document.selectionRequests.map { it.mode }).containsExactly(SelectionMode.Words)
    }

    @Test
    fun `copy, select all and drags are ignored without a selection`() = runTest(dispatcher) {
        val controller = controller()

        controller.effects.test {
            controller.onCopy()
            controller.onSelectAll()
            advanceUntilIdle()
            expectNoEvents()
        }
        assertThat(document.selectionRequests).isEmpty()
    }

    private companion object {
        const val PAGE_COUNT = 3
    }
}
