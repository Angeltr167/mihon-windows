package mihon.desktop.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

object DesktopDatabaseDriver {
    fun open(path: Path): SqlDriver {
        path.parent?.let(Files::createDirectories)
        return JdbcSqliteDriver(
            url = "jdbc:sqlite:${path.toAbsolutePath()}",
            properties = Properties(),
            schema = Database.Schema,
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
