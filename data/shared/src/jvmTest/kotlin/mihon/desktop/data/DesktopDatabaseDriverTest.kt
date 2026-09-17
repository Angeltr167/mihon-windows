package mihon.desktop.data

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class DesktopDatabaseDriverTest {

    @Test
    fun `creates a real Mihon schema on JVM SQLite`() {
        val directory = Files.createTempDirectory("mihon-db-test")
        val databasePath = directory.resolve("tachiyomi.db")

        DesktopDatabaseDriver.open(databasePath).use { }

        assertTrue(Files.exists(databasePath))
        assertTrue(Files.size(databasePath) > 0L)
        assertTrue(Database.Schema.version > 0L)
    }
}
