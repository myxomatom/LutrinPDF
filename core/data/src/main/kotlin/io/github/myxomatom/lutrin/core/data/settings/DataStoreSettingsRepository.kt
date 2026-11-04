// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import io.github.myxomatom.lutrin.core.domain.settings.AppSettings
import io.github.myxomatom.lutrin.core.domain.settings.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * [SettingsRepository] stored in a Preferences DataStore, in the app's private files: never
 * backed up nor sent anywhere (ADR 0004). A missing value reads as its default.
 *
 * @param dataStore the preferences file.
 */
internal class DataStoreSettingsRepository @Inject constructor(private val dataStore: DataStore<Preferences>) :
    SettingsRepository {
    override val settings: Flow<AppSettings> =
        dataStore.data
            .map { preferences ->
                AppSettings(dynamicColor = preferences[DYNAMIC_COLOR] ?: AppSettings.DEFAULT_DYNAMIC_COLOR)
            }.distinctUntilChanged()

    override suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[DYNAMIC_COLOR] = enabled }
    }

    private companion object {
        /** Key names are stored on the device: never rename them without a migration. */
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    }
}
