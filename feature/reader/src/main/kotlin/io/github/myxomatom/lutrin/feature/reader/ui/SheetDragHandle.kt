// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.myxomatom.lutrin.core.ui.theme.LutrinDimens

/**
 * The handle of the bottom sheets: the Material 3 handle, centred in a 48 dp square.
 *
 * The Material handle is 32 dp wide, and the sheet makes it the target of its expand and collapse
 * actions for TalkBack. The Accessibility Test Framework then asks for a touch target of 48 dp
 * (F18, `AGENTS.md` §11). The handle keeps its name and its look (32 × 4 dp); only the area it
 * occupies grows, to the 48 dp square.
 */
@OptIn(ExperimentalMaterial3Api::class) // BottomSheetDefaults.DragHandle
@Composable
internal fun SheetDragHandle() {
    Box(
        Modifier.size(LutrinDimens.TouchTargetMin),
        contentAlignment = Alignment.Center,
    ) {
        BottomSheetDefaults.DragHandle()
    }
}
