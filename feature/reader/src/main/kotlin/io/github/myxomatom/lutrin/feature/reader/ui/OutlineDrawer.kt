// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.myxomatom.lutrin.core.ui.theme.LutrinDimens
import io.github.myxomatom.lutrin.feature.reader.OutlineEntry
import io.github.myxomatom.lutrin.feature.reader.OutlineTarget
import io.github.myxomatom.lutrin.feature.reader.R
import io.github.myxomatom.lutrin.feature.reader.ReaderOutline
import io.github.myxomatom.lutrin.feature.reader.visibleEntries

/** Test tag of the list of entries of the table of contents. */
internal const val OUTLINE_LIST_TAG = "outlineList"

/** Width of the drawer (mockup V2: 320 dp). */
private val DrawerWidth = 320.dp

/** Margin between the drawer and its content (mockup V2: 12 dp). */
private val SheetPadding = 12.dp

/** Margin before the title (mockup V2: 16 dp, counted from the content of the drawer). */
private val TitleStart = 16.dp

/** Indentation added by each level of the tree. */
private val LevelIndent = 12.dp

/** Deepest level that is indented more than the previous one, so that a deep tree keeps room for its titles. */
private const val MAX_INDENTED_LEVEL = 6

/** Corners of the highlight of an entry (mockup V2: a 48 dp pill). */
private val EntryCorner = 24.dp

/** Space at the end of an entry, after the page number. */
private val EntryEnd = 16.dp

/** Gap between the title of an entry and its page number. */
private val EntryGap = 8.dp

/** Size of the icon marking an external entry. */
private val ExternalIconSize = 18.dp

/** Lines the title of an entry may take at a large font size. */
private const val MAX_TITLE_LINES = 3

/** Space below the last entry. */
private val ListBottom = 16.dp

/** Entries kept above the current section when the list opens on it, so that its context shows. */
private const val CONTEXT_ENTRIES = 1

/**
 * Table of contents (V2) as a side drawer over [content], opened and closed through
 * [drawerState]; it follows the layout direction, so it slides from the right in a right-to-left
 * language. It opens on the current section, which is highlighted, and each entry is a line of at
 * least 48 dp: a tap on its title leads to its page, a tap on its chevron shows or hides its
 * sub-sections.
 *
 * The drawer is not opened by a swipe from the edge, which belongs to the pages and to the
 * system back gesture; it opens from the button of the top bar and closes by a swipe, a tap
 * outside, the close button or back. The entries are composed only while the drawer is open or
 * moving, so a closed drawer costs nothing.
 *
 * @param drawerState open or closed.
 * @param outline the table of contents.
 * @param documentTitle title of the document, shown under the title of the drawer.
 * @param pageCount number of pages of the document.
 * @param onToggle called with the id of an entry whose sub-sections the user shows or hides.
 * @param onEntryClick called with the entry the user chose; the caller closes the drawer.
 * @param onClose called by the close button.
 * @param modifier modifier applied to the drawer and its content.
 * @param content what the drawer covers: the reader.
 */
@Suppress("LongParameterList") // Plain wiring of the state hoisted in ReaderContent.
@Composable
internal fun OutlineDrawer(
    drawerState: DrawerState,
    outline: ReaderOutline,
    documentTitle: String,
    pageCount: Int,
    onToggle: (Int) -> Unit,
    onEntryClick: (OutlineEntry) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    ModalNavigationDrawer(
        drawerContent = {
            ModalDrawerSheet(drawerState = drawerState, modifier = Modifier.width(DrawerWidth)) {
                if (drawerState.currentValue != DrawerValue.Closed || drawerState.targetValue != DrawerValue.Closed) {
                    OutlinePanel(outline, documentTitle, pageCount, onToggle, onEntryClick, onClose)
                }
            }
        },
        modifier = modifier,
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        content = content,
    )
}

/** Title with its close button, the document, then the entries. */
@Suppress("LongParameterList") // Plain wiring of the state of OutlineDrawer.
@Composable
private fun OutlinePanel(
    outline: ReaderOutline,
    documentTitle: String,
    pageCount: Int,
    onToggle: (Int) -> Unit,
    onEntryClick: (OutlineEntry) -> Unit,
    onClose: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = SheetPadding)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.reader_outline_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f).padding(start = TitleStart).semantics { heading() },
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.reader_outline_close))
            }
        }
        Text(
            stringResource(
                R.string.reader_outline_subtitle,
                documentTitle,
                pluralStringResource(R.plurals.reader_outline_pages, pageCount, pageCount),
            ),
            style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = TitleStart, end = TitleStart, bottom = SheetPadding),
        )
        OutlineList(outline, onToggle, onEntryClick)
    }
}

/**
 * The entries not hidden by a collapsed parent, lazily: a long document has thousands. It starts
 * on the current section, so a table of hundreds of entries does not open on its first line.
 */
@Composable
private fun OutlineList(outline: ReaderOutline, onToggle: (Int) -> Unit, onEntryClick: (OutlineEntry) -> Unit) {
    val visible = remember(outline.entries, outline.expanded) { outline.entries.visibleEntries(outline.expanded) }
    val current = visible.indexOfFirst { it.id == outline.currentSection }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (current - CONTEXT_ENTRIES).coerceAtLeast(0))
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(OUTLINE_LIST_TAG),
        state = listState,
        contentPadding = PaddingValues(bottom = ListBottom),
    ) {
        items(visible, key = { it.id }) { entry ->
            OutlineRow(
                entry = entry,
                isCurrent = entry.id == outline.currentSection,
                isExpanded = entry.id in outline.expanded,
                onToggle = { onToggle(entry.id) },
                onClick = { onEntryClick(entry) },
            )
        }
    }
}

/**
 * One entry: its chevron, its title, and the page it leads to or the mark of an external link.
 * An entry that leads nowhere does nothing, except showing its sub-sections when it has some.
 */
@Composable
private fun OutlineRow(
    entry: OutlineEntry,
    isCurrent: Boolean,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit,
) {
    val title = entry.title.ifBlank { stringResource(R.string.reader_outline_untitled) }
    val highlight = if (isCurrent) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    val onLabelClick = if (entry.target != null) onClick else onToggle.takeIf { entry.hasChildren }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = LutrinDimens.TouchTargetMin)
            .background(highlight, RoundedCornerShape(EntryCorner)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(LevelIndent * entry.level.coerceAtMost(MAX_INDENTED_LEVEL)))
        if (entry.hasChildren) {
            ExpandButton(title, isExpanded, onToggle)
        } else {
            Spacer(Modifier.width(LutrinDimens.TouchTargetMin))
        }
        EntryLabel(entry, title, isCurrent, onLabelClick)
    }
}

/** The chevron showing or hiding the sub-sections: a 48 dp target announcing its state. */
@Composable
private fun ExpandButton(title: String, isExpanded: Boolean, onToggle: () -> Unit) {
    val state = stringResource(if (isExpanded) R.string.reader_outline_expanded else R.string.reader_outline_collapsed)
    val label = stringResource(if (isExpanded) R.string.reader_outline_collapse else R.string.reader_outline_expand)
    val description = stringResource(R.string.reader_outline_toggle, title)
    Box(
        Modifier
            .size(LutrinDimens.TouchTargetMin)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onToggle)
            .semantics {
                contentDescription = description
                stateDescription = state
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (isExpanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(LutrinDimens.ButtonIcon),
        )
    }
}

/**
 * Title and destination of an entry, one target of at least 48 dp. TalkBack reads the title,
 * where the entry leads, its level and whether it is the current section.
 *
 * @param onClick what a tap does; `null` for an entry that leads nowhere and has nothing to show.
 */
@Composable
private fun RowScope.EntryLabel(entry: OutlineEntry, title: String, isCurrent: Boolean, onClick: (() -> Unit)?) {
    val description =
        when (val target = entry.target) {
            is OutlineTarget.Page -> stringResource(R.string.reader_outline_entry_page, title, target.pageIndex + 1)
            is OutlineTarget.External -> stringResource(R.string.reader_outline_entry_external, title)
            null -> title
        }
    val level = stringResource(R.string.reader_outline_level, entry.level + 1)
    val textColor =
        if (isCurrent) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .weight(1f)
            .defaultMinSize(minHeight = LutrinDimens.TouchTargetMin)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                stateDescription = level
                selected = isCurrent
            }
            .padding(end = EntryEnd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EntryGap),
    ) {
        Text(
            title,
            // The title is the document's own text: its direction follows its content, not the app's.
            style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
            fontWeight = if (isCurrent) FontWeight.Medium else FontWeight.Normal,
            color = textColor,
            maxLines = MAX_TITLE_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        EntryTrailing(entry.target)
    }
}

/** « p. 3 » for an entry of the document, the mark of an external link for an address. */
@Composable
private fun EntryTrailing(target: OutlineTarget?) {
    when (target) {
        is OutlineTarget.Page ->
            Text(
                stringResource(R.string.reader_outline_page_label, target.pageIndex + 1),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

        is OutlineTarget.External ->
            Icon(
                OpenInNewIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(ExternalIconSize),
            )

        null -> Unit
    }
}
