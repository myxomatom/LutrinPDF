// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.data.di

import android.content.ContentResolver
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.myxomatom.lutrin.core.data.db.LutrinDatabase
import io.github.myxomatom.lutrin.core.data.document.ContentResolverDocumentTypeResolver
import io.github.myxomatom.lutrin.core.data.recents.ContentResolverDocumentAccess
import io.github.myxomatom.lutrin.core.data.recents.DocumentAccess
import io.github.myxomatom.lutrin.core.data.recents.PdfThumbnailRenderer
import io.github.myxomatom.lutrin.core.data.recents.RecentDocumentDao
import io.github.myxomatom.lutrin.core.data.recents.RoomRecentDocumentsRepository
import io.github.myxomatom.lutrin.core.data.recents.ThumbnailRenderer
import io.github.myxomatom.lutrin.core.data.recents.ThumbnailStore
import io.github.myxomatom.lutrin.core.data.settings.DataStoreSettingsRepository
import io.github.myxomatom.lutrin.core.domain.dispatchers.IoDispatcher
import io.github.myxomatom.lutrin.core.domain.document.DocumentTypeResolver
import io.github.myxomatom.lutrin.core.domain.recents.RecentDocumentsRepository
import io.github.myxomatom.lutrin.core.domain.settings.SettingsRepository
import java.io.File
import java.time.Clock
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

    /** MIME types of incoming documents, asked to their providers (ADR 0014). */
    @Binds
    abstract fun bindDocumentTypeResolver(resolver: ContentResolverDocumentTypeResolver): DocumentTypeResolver

    /** Recent documents and reading positions, stored with Room (ADR 0015). */
    @Binds
    abstract fun bindRecentDocumentsRepository(repository: RoomRecentDocumentsRepository): RecentDocumentsRepository

    /** Names, sizes and persisted permissions of documents, asked to their providers. */
    @Binds
    abstract fun bindDocumentAccess(access: ContentResolverDocumentAccess): DocumentAccess

    /** Thumbnails drawn through the PDF engine. */
    @Binds
    abstract fun bindThumbnailRenderer(renderer: PdfThumbnailRenderer): ThumbnailRenderer

    companion object {
        /** File name of the preferences, in the app's private files. */
        private const val SETTINGS_FILE = "settings"

        /** Directory of the thumbnails, in the app's files that are never backed up. */
        private const val THUMBNAILS_DIRECTORY = "thumbnails"

        /** The time of the device, in its time zone. */
        @Provides
        fun provideClock(): Clock = Clock.systemDefaultZone()

        /**
         * The app's database. Room requires a single instance per file in the process, hence the
         * singleton; its queries run on the injected I/O dispatcher.
         */
        @Provides
        @Singleton
        fun provideDatabase(
            @ApplicationContext context: Context,
            @IoDispatcher ioDispatcher: CoroutineDispatcher,
        ): LutrinDatabase = Room
            .databaseBuilder(context, LutrinDatabase::class.java, LutrinDatabase.NAME)
            .setQueryCoroutineContext(ioDispatcher)
            .build()

        /** The table of recent documents. */
        @Provides
        fun provideRecentDocumentDao(database: LutrinDatabase): RecentDocumentDao = database.recentDocumentDao()

        /** Thumbnails under `noBackupFilesDir`: private, kept, never backed up (ADR 0015). */
        @Provides
        fun provideThumbnailStore(@ApplicationContext context: Context): ThumbnailStore =
            ThumbnailStore(File(context.noBackupFilesDir, THUMBNAILS_DIRECTORY))

        /** The app's content resolver, to query the providers of documents sent by other apps. */
        @Provides
        fun provideContentResolver(@ApplicationContext context: Context): ContentResolver = context.contentResolver

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
