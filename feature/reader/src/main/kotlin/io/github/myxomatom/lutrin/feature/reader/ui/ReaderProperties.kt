// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.myxomatom.lutrin.core.domain.document.DocumentProperties
import io.github.myxomatom.lutrin.feature.reader.ReaderViewModel

/**
 * What the reader screen needs for the properties sheet (F16, V5, ADR 0027), wired by
 * [ReaderRoute] to the ViewModel. The defaults make a reader without properties.
 *
 * @property value the properties to show; `null` while the sheet is closed.
 * @property onOpen « Propriétés » in the menu of the top bar.
 * @property onDismiss the sheet was closed.
 */
@Stable
class ReaderProperties(
    val value: DocumentProperties? = null,
    val onOpen: () -> Unit = {},
    val onDismiss: () -> Unit = {},
)

/** The properties sheet of [viewModel], in the state it is in now. */
@Composable
internal fun rememberReaderProperties(viewModel: ReaderViewModel): ReaderProperties {
    val value by viewModel.properties.collectAsStateWithLifecycle()
    return ReaderProperties(
        value = value,
        onOpen = viewModel::onPropertiesRequested,
        onDismiss = viewModel::onPropertiesDismissed,
    )
}
