// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.about

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.myxomatom.lutrin.core.ui.theme.LutrinDimens
import io.github.myxomatom.lutrin.core.ui.theme.LutrinTheme

/** Horizontal margin of the content (E4 mockup: 24 dp). */
internal val AboutGutter = 24.dp

/** Horizontal padding a Material 3 ListItem already applies on each side. */
private val ListItemInset = 16.dp

/** Size of the square logo (E4 mockup: 72 dp) and its corner radius (20 dp). */
private val LogoSize = 72.dp
private val LogoCorner = 20.dp

/** Space between the elements of the header. */
private val HeaderSpacing = 6.dp

/** Margin around the « no data collected » card (E4 mockup: 16 dp). */
private val CardMargin = 16.dp

/** Space between the icon and the text of the card, and inside the card. */
private val CardPadding = 16.dp
private val CardGap = 12.dp

/**
 * Stateful entry point of E4: connects [AboutScreen] to its ViewModel, hands the source code
 * address to another app once confirmed, and tells when no app can open it.
 *
 * @param onBack called when the user leaves E4.
 * @param modifier modifier applied to the screen.
 * @param viewModel state holder, provided by Hilt for the navigation entry.
 */
@Composable
fun AboutRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: AboutViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val noAppMessage = stringResource(R.string.about_source_no_app)
    LaunchedEffect(viewModel, context) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is AboutEffect.OpenSourceCode ->
                    if (!openSourceCode(context, effect.url)) snackbarHostState.showSnackbar(noAppMessage)
            }
        }
    }
    AboutScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        callbacks =
            AboutCallbacks(
                onBackFromPage = viewModel::onBackFromPage,
                onOpenPrivacy = viewModel::onOpenPrivacy,
                onOpenComponent = viewModel::onOpenComponent,
                onSourceCodeClick = viewModel::onSourceCodeClick,
                onSourceCodeConfirm = viewModel::onSourceCodeConfirm,
                onSourceCodeDismiss = viewModel::onSourceCodeDismiss,
            ),
        modifier = modifier,
    )
}

/**
 * What E4 can do, grouped so that the screen keeps a short signature.
 *
 * @property onBackFromPage goes back from a sub-page to the main page; returns `false` when the
 *   main page was already shown.
 * @property onOpenPrivacy shows the privacy policy.
 * @property onOpenComponent shows the license text of a component, given its identifier.
 * @property onSourceCodeClick asks to open the source code.
 * @property onSourceCodeConfirm the user confirmed opening the source code.
 * @property onSourceCodeDismiss the user cancelled the confirmation.
 */
data class AboutCallbacks(
    val onBackFromPage: () -> Boolean = { false },
    val onOpenPrivacy: () -> Unit = {},
    val onOpenComponent: (String) -> Unit = {},
    val onSourceCodeClick: () -> Unit = {},
    val onSourceCodeConfirm: () -> Unit = {},
    val onSourceCodeDismiss: () -> Unit = {},
)

/**
 * E4 « À propos et licences » (`docs/design/png/E4-APropos.png`): the main page, the privacy
 * policy and the license text of a component, one at a time. The back gesture goes from a
 * sub-page to the main page, then leaves E4.
 *
 * @param state what to show.
 * @param onBack called when the user leaves E4 from its main page.
 * @param modifier modifier applied to the screen.
 * @param snackbarHostState host of the messages (no app can open the link).
 * @param callbacks what the user can do.
 */
@Composable
fun AboutScreen(
    state: AboutUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    callbacks: AboutCallbacks = AboutCallbacks(),
) {
    val goBack = { if (!callbacks.onBackFromPage()) onBack() }
    BackHandler(enabled = state.page != AboutPage.Main) { callbacks.onBackFromPage() }
    when (val page = state.page) {
        AboutPage.Main -> MainPage(state, snackbarHostState, goBack, callbacks, modifier)

        AboutPage.Privacy -> PrivacyPage(goBack, modifier)

        is AboutPage.Component ->
            ComponentPage(
                component = (state.licenses as? LicensesState.Loaded)?.components?.firstOrNull {
                    it.id ==
                        page.componentId
                },
                onBack = goBack,
                modifier = modifier,
            )
    }
    if (state.confirmingSourceLink) {
        SourceCodeDialog(
            url = SOURCE_CODE_URL,
            onOpen = callbacks.onSourceCodeConfirm,
            onDismiss = callbacks.onSourceCodeDismiss,
        )
    }
}

/** Top bar of the three pages: a title and the back arrow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AboutTopBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    TopAppBar(
        title = { Text(title, modifier = Modifier.semantics { heading() }) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.about_back))
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun MainPage(
    state: AboutUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    callbacks: AboutCallbacks,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { AboutTopBar(stringResource(R.string.about_title), onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = innerPadding) {
            item { Header(state.versionName) }
            item { NoDataCard() }
            item { LicenceRow() }
            item {
                LinkRow(
                    title = stringResource(R.string.about_source_title),
                    summary = stringResource(R.string.about_source_url),
                    onClick = callbacks.onSourceCodeClick,
                )
            }
            item { LinkRow(title = stringResource(R.string.about_privacy_title), onClick = callbacks.onOpenPrivacy) }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { SectionTitle(stringResource(R.string.about_licenses_title)) }
            licenseItems(state.licenses, callbacks.onOpenComponent)
        }
    }
}

/** The logo, the name, the version and the tagline of the app. */
@Composable
private fun Header(versionName: String?, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(start = AboutGutter, end = AboutGutter, top = 8.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HeaderSpacing),
    ) {
        Surface(
            modifier = Modifier.size(LogoSize),
            shape = RoundedCornerShape(LogoCorner),
            color = MaterialTheme.colorScheme.primary,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(R.drawable.ic_about_logo),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
        Text(
            text = stringResource(R.string.about_app_name),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 8.dp).semantics { heading() },
        )
        if (versionName != null) {
            Text(
                text = stringResource(R.string.about_version, versionName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(R.string.about_tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** « Aucune donnée collectée »: the card of the mockup, read as one element. */
@Composable
private fun NoDataCard(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(start = CardMargin, end = CardMargin, bottom = CardMargin),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(Modifier.padding(CardPadding), horizontalArrangement = Arrangement.spacedBy(CardGap)) {
            Icon(Icons.Outlined.CheckCircle, contentDescription = null)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.about_no_data_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.about_no_data_message),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/**
 * « Licence »: GPL v3 or later, distributed under the AGPL v3 because of MuPDF (ADR 0030). The
 * explanation is always visible: it is the notice the AGPL asks the app to give.
 */
@Composable
private fun LicenceRow(modifier: Modifier = Modifier) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.about_license_title)) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.about_license_summary), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.about_license_explanation), style = MaterialTheme.typography.bodySmall)
            }
        },
        modifier = modifier.padding(horizontal = AboutGutter - ListItemInset),
    )
}

/** A row that opens something: a title, an optional second line, and the button role. */
@Composable
private fun LinkRow(title: String, onClick: () -> Unit, modifier: Modifier = Modifier, summary: String? = null) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        modifier =
            modifier
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = AboutGutter - ListItemInset),
    )
}

/** A section title in the primary colour, a heading for TalkBack (as in E3). */
@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = AboutGutter, end = AboutGutter, top = 8.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

@Preview
@Composable
private fun AboutScreenPreview() {
    LutrinTheme(dynamicColor = false) {
        AboutScreen(
            state =
                AboutUiState(
                    versionName = "1.0.0",
                    licenses =
                        LicensesState.Loaded(
                            listOf(
                                LicensedComponent(
                                    id = "mupdf",
                                    name = "MuPDF",
                                    version = "1.28.6",
                                    description = null,
                                    website = null,
                                    authors = emptyList(),
                                    licenses = listOf(LicenseText("AGPL-3.0-or-later", null)),
                                ),
                            ),
                        ),
                ),
            onBack = {},
        )
    }
}
