package kubeofpie.core.storage

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ConfigDatabaseTest {

    @Test
    fun `read-write open on missing file creates and migrates`(@TempDir tmp: Path) {
        val dbPath = tmp.resolve("kop.db")
        val database = ConfigDatabase()

        val result = database.open(dbPath, OpenMode.READ_WRITE)

        assertTrue(result.createdNew)
        assertEquals(dbPath, result.path)
        assertTrue(Files.exists(dbPath))
        assertTrue(flywayHistoryTableExists(database), "flyway_schema_history should exist after migrate()")
    }

    @Test
    fun `read-write open on existing file is not flagged as new`(@TempDir tmp: Path) {
        val dbPath = tmp.resolve("kop.db")
        ConfigDatabase().open(dbPath, OpenMode.READ_WRITE)

        val result = ConfigDatabase().open(dbPath, OpenMode.READ_WRITE)

        assertFalse(result.createdNew)
    }

    @Test
    fun `read-only open on missing file throws NoDatabaseException`(@TempDir tmp: Path) {
        val dbPath = tmp.resolve("missing.db")
        val database = ConfigDatabase()

        val ex = assertThrows(NoDatabaseException::class.java) {
            database.open(dbPath, OpenMode.READ_ONLY)
        }
        assertEquals(dbPath, ex.path)
        assertTrue(ex.message!!.contains(dbPath.toString()))
        assertFalse(database.isOpen())
    }

    @Test
    fun `read-only open on existing file does not run migrations`(@TempDir tmp: Path) {
        val dbPath = tmp.resolve("kop.db")
        ConfigDatabase().open(dbPath, OpenMode.READ_WRITE)

        val readOnly = ConfigDatabase()
        readOnly.open(dbPath, OpenMode.READ_ONLY)

        readOnly.connection().use { conn ->
            // Read-only mode rejects writes — proves Flyway never tried to migrate
            // (which would have written to flyway_schema_history).
            assertThrows(java.sql.SQLException::class.java) {
                conn.createStatement().use { it.execute("CREATE TABLE _probe (x INTEGER)") }
            }
        }
    }

    @Test
    fun `connection before open throws`() {
        val database = ConfigDatabase()
        assertThrows(IllegalStateException::class.java) { database.connection() }
        assertFalse(database.isOpen())
    }

    @Test
    fun `reopening with mismatched path throws`(@TempDir tmp: Path) {
        val first = tmp.resolve("a.db")
        val second = tmp.resolve("b.db")
        val database = ConfigDatabase()
        database.open(first, OpenMode.READ_WRITE)

        assertThrows(IllegalArgumentException::class.java) {
            database.open(second, OpenMode.READ_WRITE)
        }
    }

    @Test
    fun `reopening with same path and mode is a no-op`(@TempDir tmp: Path) {
        val dbPath = tmp.resolve("kop.db")
        val database = ConfigDatabase()
        val first = database.open(dbPath, OpenMode.READ_WRITE)
        val second = database.open(dbPath, OpenMode.READ_WRITE)

        assertEquals(first.path, second.path)
        assertEquals(first.createdNew, second.createdNew)
        assertTrue(first.dataSource === second.dataSource)
        assertNotNull(database.dataSource())
    }

    private fun flywayHistoryTableExists(db: ConfigDatabase): Boolean {
        db.connection().use { conn ->
            conn.metaData.getTables(null, null, "flyway_schema_history", null).use { rs ->
                return rs.next()
            }
        }
    }
}
