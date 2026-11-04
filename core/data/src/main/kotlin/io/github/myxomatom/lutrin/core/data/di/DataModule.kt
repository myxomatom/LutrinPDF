// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.myxomatom.lutrin.core.data.settings.DataStoreSettingsRepository
import io.github.myxomatom.lutrin.core.domain.dispatchers.IoDispatcher
import io.github.myxomatom.lutrin.core.domain.settings.SettingsRepository
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/** Provides the repositories of `core:data` to the rest of the app. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataModule {
    /** Settings stored with DataStore. */
    @Binds
    abstract fun bindSettingsRepository(repository: DataStoreSettingsRepository): SettingsRepository

    companion object {
        /** File name of the preferences, in the app's private files. */
        private const val SETTINGS_FILE = "settings"

        /**
         * The preferences file. DataStore requires a single instance per file in the process,
         * hence the singleton; its reads and writes run on the injected I/O dispatcher.
         */
        @Provides
        @Singleton
        fun provideSettingsDataStore(
            @ApplicationContext context: Context,
            @IoDispatcher ioDispatcher: CoroutineDispatcher,
        ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(ioDispatcher + SupervisorJob()),
            produceFile = { context.preferencesDataStoreFile(SETTINGS_FILE) },
        )
    }
}
