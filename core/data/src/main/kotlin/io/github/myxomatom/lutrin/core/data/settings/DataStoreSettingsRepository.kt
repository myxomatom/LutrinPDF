// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.myxomatom.lutrin.core.domain.settings.AppSettings
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
                    ignoreCopyRestriction =
                        preferences[IGNORE_COPY_RESTRICTION] ?: AppSettings.DEFAULT_IGNORE_COPY_RESTRICTION,
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

    override suspend fun setIgnoreCopyRestriction(enabled: Boolean) {
        dataStore.edit { it[IGNORE_COPY_RESTRICTION] = enabled }
    }

    private companion object {
        /** Key names are stored on the device: never rename them without a migration. */
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")

        /** Holds [storedValue] of the chosen [ScrollMode]. */
        val SCROLL_MODE = stringPreferencesKey("scroll_mode")

        /** Whether the reader's slider previews the page (ADR 0018). */
        val PAGE_PREVIEW = booleanPreferencesKey("page_preview")

        /** Whether the copy restriction of documents is ignored (ADR 0024); absent before it existed. */
        val IGNORE_COPY_RESTRICTION = booleanPreferencesKey("ignore_copy_restriction")
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
