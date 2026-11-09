// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.core.data.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment

/**
 * Migrations of `lutrin.db`, against the schemas exported in `core/data/schemas/` (ADR 0015,
 * 0017): every version is reached from the previous one without losing an entry.
 *
 * The helper opens the database through an [AndroidSQLiteDriver] and a file: its default,
 * name-based driver compares file names with `/` and fails on Windows paths.
 */
@RunWith(AndroidJUnit4::class)
class LutrinDatabaseMigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    /** In the sandbox of the test, removed with it. */
    private val databaseFile: File = instrumentation.targetContext.getDatabasePath("migration-test.db")

    @get:Rule
    val helper = MigrationTestHelper(instrumentation, databaseFile, AndroidSQLiteDriver(), LutrinDatabase::class)

    private fun SQLiteConnection.insertVersion1Entry(documentId: String, thumbnail: String?) {
        val thumbnailFile = thumbnail?.let { "'$it'" } ?: "NULL"
        val drawnAt = thumbnail?.let { "2000" } ?: "NULL"
        execSQL(
            "INSERT INTO recent_documents (document_id, uri, display_name, size_bytes, page_count, " +
                "last_opened_at, last_page, thumbnail_file, thumbnail_drawn_at) VALUES ('$documentId', " +
                "'content://com.example.files/document/$documentId', 'Contrat de location', 2048, 8, 1000, 3, " +
                "$thumbnailFile, $drawnAt)",
        )
    }

    /** Rows of `recent_documents` as (identifier, display name, last page, thumbnail, protected). */
    private fun SQLiteConnection.entries(): List<List<Any?>> = prepare(
        "SELECT document_id, display_name, last_page, thumbnail_file, is_protected FROM recent_documents " +
            "ORDER BY document_id",
    ).use { statement ->
        buildList {
            while (statement.step()) {
                add(
                    listOf(
                        statement.getText(0),
                        statement.getText(1),
                        statement.getInt(2),
                        if (statement.isNull(3)) null else statement.getText(3),
                        statement.getInt(4),
                    ),
                )
            }
        }
    }

    @Test
    fun `version 2 keeps every entry of version 1, none of them protected`() {
        helper.createDatabase(1).apply {
            insertVersion1Entry("a", thumbnail = "a-2000.png")
            insertVersion1Entry("b", thumbnail = null)
            close()
        }

        val migrated = helper.runMigrationsAndValidate(2)

        assertThat(migrated.entries())
            .containsExactly(
                listOf("a", "Contrat de location", 3, "a-2000.png", 0),
                listOf("b", "Contrat de location", 3, null, 0),
            ).inOrder()
        migrated.close()
    }

    @Test
    fun `Room opens a migrated database and reads its entries`() = runTest {
        helper.createDatabase(1).apply {
            insertVersion1Entry("a", thumbnail = null)
            close()
        }
        helper.runMigrationsAndValidate(2).close()

        val database =
            Room
                .databaseBuilder(RuntimeEnvironment.getApplication(), LutrinDatabase::class.java, databaseFile.path)
                .setDriver(AndroidSQLiteDriver())
                .build()
        try {
            val entry = database.recentDocumentDao().findById("a")

            assertThat(entry?.lastPage).isEqualTo(3)
            assertThat(entry?.isProtected).isFalse()
        } finally {
            database.close()
        }
    }

    @Test
    fun `every version up to the current one has its exported schema`() {
        val assets = instrumentation.context.assets
        val exported = assets.list(LutrinDatabase::class.java.name).orEmpty().toSet()

        assertThat(exported).containsExactlyElementsIn((1..LutrinDatabase.VERSION).map { "$it.json" })
    }
}
