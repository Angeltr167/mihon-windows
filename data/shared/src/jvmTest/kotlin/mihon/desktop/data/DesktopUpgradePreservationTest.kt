package mihon.desktop.data

import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class DesktopUpgradePreservationTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `real migration preserves history progress favorites and takes a WAL aware backup`() {
        val path = seedLibrary()
        JdbcSqliteDriver("jdbc:sqlite:$path").use { old ->
            old.execute(null, "PRAGMA journal_mode = WAL", 0)
            old.execute(null, "PRAGMA user_version = 14", 0)
            old.execute(null, "UPDATE history SET time_read = 480001", 0)
            DesktopDatabaseDriver.open(path).use { upgraded ->
                assertEquals(Database.Schema.version, upgraded.number("PRAGMA user_version"))
                assertLibrary(upgraded, 480001)
                assertEquals(14L, upgraded.number("SELECT score FROM manga_sync"))
            }
        }
        val backup = Files.list(directory.resolve("upgrade-backups")).use { it.toList().single() }
        JdbcSqliteDriver("jdbc:sqlite:$backup").use {
            assertEquals(14L, it.number("PRAGMA user_version"))
            assertLibrary(it, 480001)
            assertEquals(7L, it.number("SELECT score FROM manga_sync"))
        }
        DesktopDatabaseDriver.open(path).use { assertLibrary(it, 480001) }
        assertEquals(1L, Files.list(directory.resolve("upgrade-backups")).use { it.count() })
    }

    @Test
    fun `reopening the same release keeps history without unnecessary backups`() {
        val path = seedLibrary()
        repeat(3) { DesktopDatabaseDriver.open(path).use { assertLibrary(it, 480000) } }
        assertFalse(Files.exists(directory.resolve("upgrade-backups")))
    }

    @Test
    fun `an older app cannot change a newer library`() {
        val path = seedLibrary()
        JdbcSqliteDriver("jdbc:sqlite:$path").use {
            it.execute(null, "PRAGMA user_version = ${Database.Schema.version + 1}", 0)
        }
        assertThrows(IllegalArgumentException::class.java) { DesktopDatabaseDriver.open(path) }
        JdbcSqliteDriver("jdbc:sqlite:$path").use {
            assertLibrary(it, 480000)
            assertEquals(Database.Schema.version + 1, it.number("PRAGMA user_version"))
        }
    }

    @Test
    fun `a failed migration rolls back changes and keeps its backup`() {
        val path = seedLibrary()
        val failingSchema = object : SqlSchema<QueryResult.Value<Unit>> {
            override val version = Database.Schema.version + 1
            override fun create(driver: SqlDriver) = Database.Schema.create(driver)
            override fun migrate(
                driver: SqlDriver,
                oldVersion: Long,
                newVersion: Long,
                vararg callbacks: AfterVersion,
            ): QueryResult.Value<Unit> {
                driver.execute(null, "DELETE FROM history", 0)
                error("Migration failure fixture")
            }
        }
        assertThrows(IllegalStateException::class.java) { DesktopDatabaseDriver.open(path, failingSchema) }
        JdbcSqliteDriver("jdbc:sqlite:$path").use {
            assertLibrary(it, 480000)
            assertEquals(Database.Schema.version, it.number("PRAGMA user_version"))
        }
        assertEquals(1L, Files.list(directory.resolve("upgrade-backups")).use { it.count() })
    }

    @Test
    fun `an existing unversioned database is never recreated`() {
        val path = seedLibrary()
        JdbcSqliteDriver("jdbc:sqlite:$path").use { it.execute(null, "PRAGMA user_version = 0", 0) }
        assertThrows(IllegalArgumentException::class.java) { DesktopDatabaseDriver.open(path) }
        JdbcSqliteDriver("jdbc:sqlite:$path").use { assertLibrary(it, 480000) }
    }

    private fun seedLibrary(): Path {
        val path = directory.resolve("tachiyomi.db")
        DesktopDatabaseDriver.open(path).use { driver ->
            driver.execute(
                null,
                """
                INSERT INTO mangas (_id, source, url, title, status, favorite, initialized, viewer,
                  chapter_flags, cover_last_modified, date_added)
                VALUES (1, 0, 'preserved-manga', 'My library', 1, 1, 1, 0, 0, 0, 12345)
                """.trimIndent(),
                0,
            )
            driver.execute(
                null,
                """
                INSERT INTO chapters (_id, manga_id, url, name, read, bookmark, last_page_read,
                  chapter_number, source_order, date_fetch, date_upload)
                VALUES (2, 1, 'chapter-12', 'Chapter 12', 0, 1, 7, 12, 0, 12345, 12345)
                """.trimIndent(),
                0,
            )
            driver.execute(
                null,
                "INSERT INTO history (_id, chapter_id, last_read, time_read) VALUES (3, 2, 12345, 480000)",
                0,
            )
            driver.execute(
                null,
                """
                INSERT INTO manga_sync (_id, manga_id, sync_id, remote_id, title, last_chapter_read,
                  total_chapters, status, score, remote_url, start_date, finish_date)
                VALUES (4, 1, 3, 42, 'My library', 12, 24, 1, 7, 'https://example.com/42', 0, 0)
                """.trimIndent(),
                0,
            )
        }
        return path
    }

    private fun assertLibrary(driver: SqlDriver, readingTime: Long) {
        assertEquals(1L, driver.number("SELECT favorite FROM mangas WHERE _id = 1"))
        assertEquals(7L, driver.number("SELECT last_page_read FROM chapters WHERE _id = 2"))
        assertEquals(1L, driver.number("SELECT bookmark FROM chapters WHERE _id = 2"))
        assertEquals(12345L, driver.number("SELECT last_read FROM history WHERE chapter_id = 2"))
        assertEquals(readingTime, driver.number("SELECT time_read FROM history WHERE chapter_id = 2"))
    }

    private fun SqlDriver.number(sql: String): Long = executeQuery(null, sql, { cursor ->
        check(cursor.next().value)
        QueryResult.Value(requireNotNull(cursor.getLong(0)))
    }, 0).value
}
