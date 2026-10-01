package mihon.desktop.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import java.util.UUID

object DesktopDatabaseDriver {
    fun open(path: Path): SqlDriver {
        return open(path, Database.Schema)
    }

    internal fun open(path: Path, schema: SqlSchema<QueryResult.Value<Unit>>): SqlDriver {
        path.parent?.let(Files::createDirectories)
        val url = "jdbc:sqlite:${path.toAbsolutePath()}"
        if (Files.exists(path) && Files.size(path) > 0) {
            JdbcSqliteDriver(url).use { existing ->
                val version = existing.executeQuery(null, "PRAGMA user_version", { cursor ->
                    QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L)
                }, 0).value
                require(version <= schema.version) {
                    "This library requires a newer Ronin version. Its database has not been changed."
                }
                if (version == 0L) {
                    val tables = existing.executeQuery(
                        null,
                        "SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'",
                        { cursor ->
                            QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L)
                        },
                        0,
                    ).value
                    require(tables == 0L) {
                        "The existing library has no recognized schema version. It has not been reset."
                    }
                } else if (version < schema.version) {
                    val backups = path.toAbsolutePath().parent.resolve("upgrade-backups")
                    Files.createDirectories(backups)
                    val backup = backups.resolve(
                        "${path.fileName}.schema-$version-to-${schema.version}-${UUID.randomUUID()}.db",
                    )
                    // SQLite takes a consistent snapshot including committed WAL data.
                    // Failure to create this backup stops the upgrade before any migration.
                    existing.execute(null, "VACUUM INTO ?", 1) {
                        bindString(0, backup.toString())
                    }.value
                }
            }
        }
        return JdbcSqliteDriver(
            url = url,
            properties = Properties(),
            schema = schema,
        )
    }

    fun inMemory(): SqlDriver {
        return JdbcSqliteDriver(
            url = JdbcSqliteDriver.IN_MEMORY,
            properties = Properties(),
            schema = Database.Schema,
        )
    }
}
