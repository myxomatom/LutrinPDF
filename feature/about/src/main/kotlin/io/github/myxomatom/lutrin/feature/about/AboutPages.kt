// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

/** Space between two paragraphs of a page. */
private val ParagraphSpacing = 12.dp

/** Space between the bullet and the text of an item. */
private val BulletGap = 8.dp

/** The bullet drawn before an item of a list. */
private const val BULLET = "•"

/**
 * The privacy policy as local text, the same as `PRIVACY.md` (ADR 0030). Nothing here is loaded
 * from the network.
 *
 * @param onBack called when the user goes back to the main page.
 * @param modifier modifier applied to the page.
 */
@Composable
internal fun PrivacyPage(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { AboutTopBar(stringResource(R.string.about_privacy_title), onBack) },
    ) { innerPadding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = innerPadding,
            verticalArrangement = Arrangement.spacedBy(ParagraphSpacing),
        ) {
            item {
                Text(
                    text = stringResource(R.string.about_privacy_updated),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.pageText(),
                )
            }
            item {
                Text(
                    text = stringResource(R.string.about_privacy_summary),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.pageText(),
                )
            }
            bullets(PRIVACY_BULLETS)
            item { SectionTitle(stringResource(R.string.about_privacy_assistants_title)) }
            item { Text(stringResource(R.string.about_privacy_assistants_intro), Modifier.pageText()) }
            bullets(ASSISTANT_BULLETS)
            item { SectionTitle(stringResource(R.string.about_privacy_contact_title)) }
            item { Text(stringResource(R.string.about_privacy_contact), Modifier.pageText()) }
        }
    }
}

/** What Lutrin does with data, one item each, in the order of `PRIVACY.md`. */
private val PRIVACY_BULLETS: List<Int> =
    listOf(
        R.string.about_privacy_network,
        R.string.about_privacy_no_tracking,
        R.string.about_privacy_local_data,
        R.string.about_privacy_passwords,
        R.string.about_privacy_files,
        R.string.about_privacy_links,
    )

/** The guarantees given about assistants (version 1.1). */
private val ASSISTANT_BULLETS: List<Int> =
    listOf(
        R.string.about_privacy_assistants_content,
        R.string.about_privacy_assistants_off,
        R.string.about_privacy_assistants_password,
    )

private fun LazyListScope.bullets(texts: List<Int>) {
    items(texts) { text ->
        Row(Modifier.pageText()) {
            // Decorative: a screen reader reads the sentence, not "bullet".
            Text(BULLET, Modifier.clearAndSetSemantics {}.padding(end = BulletGap))
            Text(stringResource(text))
        }
    }
}

/**
 * The detail of one component: who made it, which version, and the full text of its license(s)
 * with their copyright notice, as the licenses require (OFL, MIT, BSD, IJG, AGPL…).
 *
 * @param component the component to show, or `null` when it is not in the list any more.
 * @param onBack called when the user goes back to the main page.
 * @param modifier modifier applied to the page.
 */
@Composable
internal fun ComponentPage(component: LicensedComponent?, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { AboutTopBar(component?.name ?: stringResource(R.string.about_licenses_title), onBack) },
    ) { innerPadding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = innerPadding,
            verticalArrangement = Arrangement.spacedBy(ParagraphSpacing),
        ) {
            if (component == null) {
                item { Text(stringResource(R.string.about_component_not_found), Modifier.pageText()) }
            } else {
                componentDetails(component)
            }
        }
    }
}

private fun LazyListScope.componentDetails(component: LicensedComponent) {
    item { ComponentFacts(component) }
    component.licenses.forEach { license ->
        item { SectionTitle(license.name) }
        item {
            Text(
                text = license.text ?: stringResource(R.string.about_license_text_missing),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.pageText(),
            )
        }
    }
}

/** Version, authors, website (text only) and description of a component. */
@Composable
private fun ComponentFacts(component: LicensedComponent) {
    Column(Modifier.pageText(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        component.version?.let { Text(stringResource(R.string.about_component_version, it)) }
        if (component.authors.isNotEmpty()) {
            Text(stringResource(R.string.about_component_authors, component.authors.joinToString(", ")))
        }
        component.description?.let { Text(it) }
        component.website?.let { Text(stringResource(R.string.about_component_website, it)) }
    }
}

/** The side margins of the text of a page. */
private fun Modifier.pageText(): Modifier = padding(horizontal = AboutGutter)
