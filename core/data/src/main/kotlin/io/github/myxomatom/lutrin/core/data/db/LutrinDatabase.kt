// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import io.github.myxomatom.lutrin.core.data.recents.RecentDocumentDao
import io.github.myxomatom.lutrin.core.data.recents.RecentDocumentEntity

/**
 * The app's Room database (`SPEC.md` §4.1), in its private storage. Each schema version is
 * exported to `core/data/schemas/` and committed; a new version comes with its migration and a
 * migration test, never with a destructive fallback that would lose the user's recents.
 *
 * Versions:
 * - 1: `recent_documents` (ADR 0015);
 * - 2: column `is_protected`, `false` for the existing entries (ADR 0017). The automatic migration
 *   only adds the column with its default value: no data is lost.
 */
@Database(
    entities = [RecentDocumentEntity::class],
    version = LutrinDatabase.VERSION,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
internal abstract class LutrinDatabase : RoomDatabase() {
    /** Recent documents and reading positions (ADR 0015). */
    abstract fun recentDocumentDao(): RecentDocumentDao

    /** File name of the database. */
    companion object {
        /** Name of the database file, in the app's `databases/` directory. */
        const val NAME: String = "lutrin.db"

        /** Current schema version; the exported schemas and the migration test follow it. */
        const val VERSION: Int = 2
    }
}
