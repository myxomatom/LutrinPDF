// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.myxomatom.lutrin.core.ui.theme.LutrinDimens

/** Vertical space around a row of the list of third-party licenses. */
private val ComponentRowPadding = 10.dp

/** Space around the progress indicator shown while the list is read. */
private val LoadingPadding = 16.dp

/** Space between the name of a component and its license when they share a line. */
private val NameGap = 12.dp

/** The rows of the list of third-party licenses, or what replaces them while loading or on failure. */
internal fun LazyListScope.licenseItems(licenses: LicensesState, onOpenComponent: (String) -> Unit) {
    when (licenses) {
        LicensesState.Loading -> item(key = "loading") { LoadingRow() }

        LicensesState.Unavailable ->
            item(key = "unavailable") {
                Text(
                    text = stringResource(R.string.about_licenses_unavailable),
                    modifier = Modifier.padding(horizontal = AboutGutter, vertical = ComponentRowPadding),
                )
            }

        is LicensesState.Loaded ->
            items(licenses.components, key = { it.id }) { component ->
                ComponentRow(component, onClick = { onOpenComponent(component.id) })
            }
    }
}

@Composable
private fun LoadingRow(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.about_licenses_loading)
    Box(modifier.fillMaxWidth().padding(vertical = LoadingPadding), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.semantics { contentDescription = description })
    }
}

/**
 * A row of the list (E4 mockup): the name on the start side, the license on the end side; with a
 * large font the license goes under the name. Read as « name, license » by TalkBack.
 */
@Composable
private fun ComponentRow(component: LicensedComponent, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FlowRow(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = LutrinDimens.TouchTargetMin)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = AboutGutter, vertical = ComponentRowPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.Center,
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = component.name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(end = NameGap),
        )
        Text(
            text = component.licenseSummary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
