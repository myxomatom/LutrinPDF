// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.myxomatom.lutrin.core.domain.document.DocumentProperties
import io.github.myxomatom.lutrin.feature.reader.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Horizontal margin of the sheet content (mockup V5: 24 dp). */
private val SheetGutter = 24.dp

/** Space below the buttons, above the navigation bar. */
private val SheetBottom = 16.dp

/** Gap between the title, the rows and the buttons. */
private val SectionSpacing = 16.dp

/** Vertical padding of a row of the list (mockup V5: rows 32 dp high). */
private val RowPadding = 4.dp

/** Gap between a label and its value, and between the two buttons. */
private val ItemSpacing = 8.dp

/** Gap between the buttons, and between the rows of buttons when they stack. */
private val ButtonSpacing = 12.dp

/** Space between the icon of a button and its label. */
private val IconSpacing = 8.dp

/** Size of the icon of a button. */
private val ButtonIconSize = 18.dp

/** Share of the width taken by the labels; the values take the rest (mockup V5). */
private const val LABEL_WEIGHT = 0.33f

/** Font scale above which a value goes under its label instead of beside it. */
private const val STACKED_FONT_SCALE = 1.3f

/** Opacity of the print button when the author of the document forbids printing (ADR 0026). */
private const val FORBIDDEN_ALPHA = 0.38f

/**
 * The properties (V5) over the reader, while [properties] holds them: « Partager » and « Imprimer »
 * close the sheet first, so that the message they may give, or the system dialog, shows over the
 * document.
 *
 * @param properties the properties to show and what closes the sheet.
 * @param canPrint whether the document may be printed.
 * @param onShare « Partager ».
 * @param onPrint « Imprimer ».
 */
@Composable
internal fun PropertiesSheet(
    properties: ReaderProperties,
    canPrint: Boolean,
    onShare: () -> Unit,
    onPrint: () -> Unit,
) {
    val value = properties.value ?: return
    ReaderPropertiesSheet(
        properties = value,
        canPrint = canPrint,
        onShare = {
            properties.onDismiss()
            onShare()
        },
        onPrint = {
            properties.onDismiss()
            onPrint()
        },
        onDismiss = properties.onDismiss,
    )
}

/**
 * The properties of the document (V5) as a modal sheet over the reader, with « Partager » and
 * « Imprimer ».
 *
 * @param properties what to show.
 * @param canPrint whether the document may be printed; when it may not, « Imprimer » is dimmed and
 *   a tap explains why (the reader shows the message).
 * @param onShare « Partager »: the reader closes the sheet and opens the chooser of the system.
 * @param onPrint « Imprimer »: the reader closes the sheet and opens the print dialog.
 * @param onDismiss called when the user closes the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderPropertiesSheet(
    properties: DocumentProperties,
    canPrint: Boolean,
    onShare: () -> Unit,
    onPrint: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        ReaderPropertiesContent(properties, canPrint, onShare, onPrint)
    }
}

/**
 * Content of the [ReaderPropertiesSheet], without the sheet: the title, one row per property and
 * the two buttons. The title and the rows scroll when the text is large; the buttons stay in view.
 *
 * @param properties what to show; a value that is `null` reads « Non renseigné ».
 * @param canPrint whether the document may be printed.
 * @param onShare called by « Partager ».
 * @param onPrint called by « Imprimer », whether printing is allowed or not.
 * @param modifier modifier applied to the content.
 * @param locale language of the dates and of the size; the one of the device by default.
 * @param zone time zone of the dates; the one of the device by default.
 */
@Composable
internal fun ReaderPropertiesContent(
    properties: DocumentProperties,
    canPrint: Boolean,
    onShare: () -> Unit,
    onPrint: () -> Unit,
    modifier: Modifier = Modifier,
    locale: Locale = LocalConfiguration.current.locales[0],
    zone: ZoneId = ZoneId.systemDefault(),
) {
    Column(modifier.fillMaxWidth()) {
        // The buttons stay in reach: only the title and the rows scroll, when the text is large.
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(SectionSpacing),
        ) {
            Text(
                stringResource(R.string.reader_properties_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = SheetGutter).semantics { heading() },
            )
            PropertyRows(properties, locale, zone, Modifier.padding(horizontal = SheetGutter))
        }
        HorizontalDivider(Modifier.padding(top = SectionSpacing))
        PropertyActions(canPrint, onShare, onPrint, Modifier.padding(horizontal = SheetGutter))
    }
}

/** The rows of the list, in the order of the mockup V5. */
@Composable
private fun PropertyRows(properties: DocumentProperties, locale: Locale, zone: ZoneId, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(modifier) {
        PropertyRow(stringResource(R.string.reader_properties_file), properties.fileName)
        PropertyRow(stringResource(R.string.reader_properties_document_title), properties.title)
        PropertyRow(stringResource(R.string.reader_properties_author), properties.author)
        PropertyRow(stringResource(R.string.reader_properties_pages), properties.pageCount.toString())
        PropertyRow(
            stringResource(R.string.reader_properties_size),
            properties.sizeBytes?.let { Formatter.formatFileSize(context, it) },
        )
        PropertyRow(stringResource(R.string.reader_properties_version), properties.pdfVersion)
        PropertyRow(
            stringResource(R.string.reader_properties_created),
            properties.createdAt?.let { formatPropertyDate(it, locale, zone) },
        )
        PropertyRow(
            stringResource(R.string.reader_properties_modified),
            properties.modifiedAt?.let { formatPropertyDate(it, locale, zone) },
        )
    }
}

/**
 * A label and its value, read as one item by TalkBack; a missing [value] reads « Non renseigné ».
 * Side by side, or the value under its label when the text is large: a narrow column would break
 * the words (a file name, a date) in the middle.
 */
@Composable
private fun PropertyRow(label: String, value: String?) {
    val colors = MaterialTheme.colorScheme
    val labelText: @Composable (Modifier) -> Unit = {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant, modifier = it)
    }
    val valueText: @Composable (Modifier) -> Unit = {
        Text(
            value ?: stringResource(R.string.reader_properties_unknown),
            style = MaterialTheme.typography.bodyLarge,
            color = if (value == null) colors.onSurfaceVariant else colors.onSurface,
            modifier = it,
        )
    }
    val rowModifier = Modifier.fillMaxWidth().padding(vertical = RowPadding).semantics(mergeDescendants = true) {}
    if (LocalDensity.current.fontScale > STACKED_FONT_SCALE) {
        Column(rowModifier) {
            labelText(Modifier)
            valueText(Modifier)
        }
    } else {
        Row(rowModifier, horizontalArrangement = Arrangement.spacedBy(ItemSpacing)) {
            labelText(Modifier.weight(LABEL_WEIGHT))
            valueText(Modifier.weight(1f - LABEL_WEIGHT))
        }
    }
}

/**
 * « Partager » and « Imprimer », side by side, or one under the other when the text is too large
 * for both. « Imprimer » stays reachable when printing is forbidden, but dimmed (ADR 0026).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PropertyActions(
    canPrint: Boolean,
    onShare: () -> Unit,
    onPrint: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val forbidden = stringResource(R.string.reader_print_forbidden_state)
    FlowRow(
        modifier.fillMaxWidth().padding(top = SectionSpacing, bottom = SheetBottom),
        horizontalArrangement = Arrangement.spacedBy(ButtonSpacing),
        verticalArrangement = Arrangement.spacedBy(ButtonSpacing),
    ) {
        ActionButton(R.drawable.ic_share, R.string.reader_share, onShare, Modifier.weight(1f))
        ActionButton(
            R.drawable.ic_print,
            R.string.reader_print,
            onPrint,
            Modifier.weight(1f).then(
                if (canPrint) Modifier else Modifier.alpha(FORBIDDEN_ALPHA).semantics { stateDescription = forbidden },
            ),
        )
    }
}

/** A tonal button with its icon, at least 48 dp high. */
@Composable
private fun ActionButton(icon: Int, label: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = ButtonDefaults.MinHeight),
    ) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(ButtonIconSize))
        Text(stringResource(label), modifier = Modifier.padding(start = IconSpacing))
    }
}

/**
 * A date of the properties in the language of the user: day, month and year, then hours and
 * minutes (mockup V5: « 12 sept. 2026, 10:14 »). A PDF date without a time zone is read as UTC
 * (`parsePdfDate`) and shown here in [zone].
 *
 * @param date the instant to show.
 * @param locale the language and region of the user.
 * @param zone the time zone to show it in.
 */
internal fun formatPropertyDate(date: Instant, locale: Locale, zone: ZoneId): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(locale)
        .withZone(zone)
        .format(date)
