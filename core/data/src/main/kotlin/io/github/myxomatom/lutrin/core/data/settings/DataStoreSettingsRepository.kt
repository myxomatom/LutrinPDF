// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.myxomatom.lutrin.core.domain.settings.AppSettings
import io.github.myxomatom.lutrin.core.domain.settings.AppTheme
import io.github.myxomatom.lutrin.core.domain.settings.DocumentColorMode
import io.github.myxomatom.lutrin.core.domain.settings.DocumentColors
import io.github.myxomatom.lutrin.core.domain.settings.ScrollMode
import io.github.myxomatom.lutrin.core.domain.settings.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * [SettingsRepository] stored in a Preferences DataStore, in the app's private files: never
 * backed up nor sent anywhere (ADR 0004). A missing or unknown value reads as its default.
 *
 * @param dataStore the preferences file.
 */
internal class DataStoreSettingsRepository @Inject constructor(private val dataStore: DataStore<Preferences>) :
    SettingsRepository {
    override val settings: Flow<AppSettings> =
        dataStore.data
            .map { preferences ->
                AppSettings(
                    dynamicColor = preferences[DYNAMIC_COLOR] ?: AppSettings.DEFAULT_DYNAMIC_COLOR,
                    scrollMode = scrollModeOf(preferences[SCROLL_MODE]),
                    pagePreview = preferences[PAGE_PREVIEW] ?: AppSettings.DEFAULT_PAGE_PREVIEW,
                    ignoreDocumentRestrictions =
                        preferences[IGNORE_DOCUMENT_RESTRICTIONS] ?: AppSettings.DEFAULT_IGNORE_DOCUMENT_RESTRICTIONS,
                    appTheme = appThemeOf(preferences[APP_THEME]),
                    documentColors =
                        DocumentColors(
                            chosen = documentColorModeOf(preferences[DOCUMENT_COLORS]),
                            followDarkTheme = preferences[FOLLOW_DARK_THEME] ?: false,
                        ),
                )
            }.distinctUntilChanged()

    override suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[DYNAMIC_COLOR] = enabled }
    }

    override suspend fun setScrollMode(mode: ScrollMode) {
        dataStore.edit { it[SCROLL_MODE] = mode.storedValue }
    }

    override suspend fun setPagePreview(enabled: Boolean) {
        dataStore.edit { it[PAGE_PREVIEW] = enabled }
    }

    override suspend fun setIgnoreDocumentRestrictions(enabled: Boolean) {
        dataStore.edit { it[IGNORE_DOCUMENT_RESTRICTIONS] = enabled }
    }

    override suspend fun setAppTheme(theme: AppTheme) {
        dataStore.edit { it[APP_THEME] = theme.storedValue }
    }

    override suspend fun setDocumentColorMode(mode: DocumentColorMode?) {
        dataStore.edit { preferences ->
            if (mode == null) preferences.remove(DOCUMENT_COLORS) else preferences[DOCUMENT_COLORS] = mode.storedValue
        }
    }

    override suspend fun setFollowDarkTheme(enabled: Boolean) {
        dataStore.edit { it[FOLLOW_DARK_THEME] = enabled }
    }

    private companion object {
        /** Key names are stored on the device: never rename them without a migration. */
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")

        /** Holds [storedValue] of the chosen [ScrollMode]. */
        val SCROLL_MODE = stringPreferencesKey("scroll_mode")

        /** Whether the reader's slider previews the page (ADR 0018). */
        val PAGE_PREVIEW = booleanPreferencesKey("page_preview")

        /**
         * Whether the copy and print restrictions of documents are ignored (ADR 0024, ADR 0026);
         * absent before it existed. The key keeps its first name, from when it covered the copy
         * only: a user who turned it on keeps it on, without any migration.
         */
        val IGNORE_DOCUMENT_RESTRICTIONS = booleanPreferencesKey("ignore_copy_restriction")

        /** Holds [storedValue] of the chosen [AppTheme]; absent for the system theme. */
        val APP_THEME = stringPreferencesKey("app_theme")

        /**
         * Holds [storedValue] of the [DocumentColorMode] picked in the reader; absent until the
         * user picks one, which is not the same as « Normal » picked explicitly (ADR 0028).
         */
        val DOCUMENT_COLORS = stringPreferencesKey("document_colors")

        /** Whether documents without chosen colours turn to night in the dark theme (ADR 0028). */
        val FOLLOW_DARK_THEME = booleanPreferencesKey("follow_dark_theme")
    }
}

/**
 * Value written on the device for a scroll mode. Spelled out instead of the enum name, so that
 * renaming a constant in code cannot silently reset the users' choice.
 */
internal val ScrollMode.storedValue: String
    get() = when (this) {
        ScrollMode.CONTINUOUS -> "continuous"
        ScrollMode.PAGED -> "paged"
    }

/** Scroll mode of a stored value; the default when nothing, or an unknown value, is stored. */
internal fun scrollModeOf(stored: String?): ScrollMode =
    ScrollMode.entries.firstOrNull { it.storedValue == stored } ?: AppSettings.DEFAULT_SCROLL_MODE

/** Value written on the device for an app theme; spelled out for the same reason as [ScrollMode.storedValue]. */
internal val AppTheme.storedValue: String
    get() = when (this) {
        AppTheme.LIGHT -> "light"
        AppTheme.DARK -> "dark"
        AppTheme.SYSTEM -> "system"
    }

/** App theme of a stored value; the default when nothing, or an unknown value, is stored. */
internal fun appThemeOf(stored: String?): AppTheme =
    AppTheme.entries.firstOrNull { it.storedValue == stored } ?: AppSettings.DEFAULT_APP_THEME

/** Value written on the device for document colours; spelled out like [ScrollMode.storedValue]. */
internal val DocumentColorMode.storedValue: String
    get() = when (this) {
        DocumentColorMode.NORMAL -> "normal"
        DocumentColorMode.NIGHT -> "night"
        DocumentColorMode.SEPIA -> "sepia"
    }

/** Document colours of a stored value; `null` (no choice) when nothing, or an unknown value, is stored. */
internal fun documentColorModeOf(stored: String?): DocumentColorMode? =
    DocumentColorMode.entries.firstOrNull { it.storedValue == stored }
