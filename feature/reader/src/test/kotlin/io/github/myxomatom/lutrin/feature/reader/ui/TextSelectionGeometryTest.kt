// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import android.content.ClipDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.myxomatom.lutrin.core.domain.pdf.PdfPoint
import io.github.myxomatom.lutrin.core.domain.pdf.PdfQuad
import io.github.myxomatom.lutrin.core.domain.pdf.PdfRect
import io.github.myxomatom.lutrin.core.domain.pdf.TextSelection
import io.github.myxomatom.lutrin.feature.reader.selection.SelectionHandle
import org.junit.Test
import org.junit.runner.RunWith

/** Where the handles and the action bar of a selection go (F08, ADR 0024), and the clipboard content. */
@RunWith(AndroidJUnit4::class)
class TextSelectionGeometryTest {
    private val firstLine = PdfQuad.of(PdfRect(100f, 200f, 300f, 214f))
    private val secondLine = PdfQuad.of(PdfRect(72f, 216f, 180f, 230f))

    @Test
    fun `left-to-right handles hang outside the selection, the start one to the left`() {
        val selection = TextSelection(1, listOf(firstLine), "bail", PdfPoint(100f, 214f), PdfPoint(300f, 214f))

        val handles = selectionHandles(selection)

        assertThat(handles).containsExactly(
            HandlePlacement(SelectionHandle.Start, PdfPoint(100f, 214f), HandleSide.Left),
            HandlePlacement(SelectionHandle.End, PdfPoint(300f, 214f), HandleSide.Right),
        ).inOrder()
    }

    @Test
    fun `right-to-left handles are mirrored, the start one on the right`() {
        // Arabic: the selection starts at the right edge of its first character (ADR 0020).
        val selection = TextSelection(1, listOf(firstLine), "مكتبة", PdfPoint(300f, 214f), PdfPoint(100f, 214f))

        val handles = selectionHandles(selection)

        assertThat(handles).containsExactly(
            HandlePlacement(SelectionHandle.Start, PdfPoint(300f, 214f), HandleSide.Right),
            HandlePlacement(SelectionHandle.End, PdfPoint(100f, 214f), HandleSide.Left),
        ).inOrder()
    }

    @Test
    fun `on several lines, each handle follows its own line`() {
        val selection =
            TextSelection(1, listOf(firstLine, secondLine), "bail\nde", PdfPoint(100f, 214f), PdfPoint(180f, 230f))

        val (start, end) = selectionHandles(selection)

        assertThat(start.side).isEqualTo(HandleSide.Left)
        assertThat(end.point).isEqualTo(PdfPoint(180f, 230f))
        assertThat(end.side).isEqualTo(HandleSide.Right)
    }

    @Test
    fun `right-to-left on several lines ends on the left of the last line`() {
        val selection =
            TextSelection(1, listOf(firstLine, secondLine), "مكتبة\nكتاب", PdfPoint(300f, 214f), PdfPoint(72f, 230f))

        val (start, end) = selectionHandles(selection)

        assertThat(start.side).isEqualTo(HandleSide.Right)
        assertThat(end.side).isEqualTo(HandleSide.Left)
    }

    @Test
    fun `an empty selection has no handle and no bounds`() {
        val empty = TextSelection.empty(1)

        assertThat(selectionHandles(empty)).isEmpty()
        assertThat(selectionBounds(empty)).isNull()
    }

    @Test
    fun `the bounds of a selection enclose all its lines`() {
        val selection =
            TextSelection(1, listOf(firstLine, secondLine), "bail\nde", PdfPoint(100f, 214f), PdfPoint(180f, 230f))

        assertThat(selectionBounds(selection)).isEqualTo(PdfRect(72f, 200f, 300f, 230f))
    }

    @Test
    fun `the action bar goes centred above the selection when there is room`() {
        val position = toolbarPosition(IntRect(100, 500, 300, 540), WINDOW, BAR, GAP, GAP_BELOW, TOP_LIMIT)

        assertThat(position).isEqualTo(IntOffset(200 - BAR.width / 2, 500 - GAP - BAR.height))
    }

    @Test
    fun `the action bar goes below the selection near the top of the window`() {
        val position = toolbarPosition(IntRect(100, 150, 300, 190), WINDOW, BAR, GAP, GAP_BELOW, TOP_LIMIT)

        assertThat(position).isEqualTo(IntOffset(200 - BAR.width / 2, 190 + GAP_BELOW))
    }

    @Test
    fun `the action bar stays inside the window`() {
        val nearLeft = toolbarPosition(IntRect(0, 500, 40, 540), WINDOW, BAR, GAP, GAP_BELOW, TOP_LIMIT)
        val nearRight = toolbarPosition(IntRect(1040, 500, 1080, 540), WINDOW, BAR, GAP, GAP_BELOW, TOP_LIMIT)
        val atBottom = toolbarPosition(IntRect(100, 50, 300, 1900), WINDOW, BAR, GAP, GAP_BELOW, TOP_LIMIT)

        assertThat(nearLeft.x).isEqualTo(0)
        assertThat(nearRight.x).isEqualTo(WINDOW.width - BAR.width)
        assertThat(atBottom.y).isEqualTo(WINDOW.height - BAR.height)
    }

    @Test
    fun `the text of a protected document is flagged sensitive from Android 13`() {
        val clip = selectionClip("bail", isSensitive = true)

        assertThat(clip.getItemAt(0).text.toString()).isEqualTo("bail")
        assertThat(clip.description.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE)).isTrue()
    }

    @Test
    fun `any other text is copied as is`() {
        val clip = selectionClip("bail", isSensitive = false)

        assertThat(clip.getItemAt(0).text.toString()).isEqualTo("bail")
        assertThat(clip.description.extras).isNull()
    }

    @Test
    fun `before Android 13 no flag exists, the text is copied as is`() {
        val clip = selectionClip("bail", isSensitive = true, sdkInt = 32)

        assertThat(clip.getItemAt(0).text.toString()).isEqualTo("bail")
        assertThat(clip.description.extras).isNull()
    }

    private companion object {
        val WINDOW = IntSize(1080, 1920)
        val BAR = IntSize(400, 120)
        const val GAP = 20
        const val GAP_BELOW = 80
        const val TOP_LIMIT = 200
    }
}
