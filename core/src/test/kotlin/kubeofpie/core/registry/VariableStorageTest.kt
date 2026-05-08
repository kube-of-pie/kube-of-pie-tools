package kubeofpie.core.registry

import java.nio.file.Path
import kubeofpie.core.storage.ConfigDatabase
import kubeofpie.core.storage.OpenMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class VariableStorageTest {

    @Test
    fun `read returns null for a missing key`(@TempDir tmp: Path) {
        val storage = newStorage(tmp)

        assertNull(storage.read("setup.keymap"))
    }

    @Test
    fun `write then read returns the persisted value`(@TempDir tmp: Path) {
        val storage = newStorage(tmp)

        storage.write("setup.keymap", "fr fr")

        assertEquals("fr fr", storage.read("setup.keymap"))
    }

    @Test
    fun `write twice for the same key updates rather than duplicating`(@TempDir tmp: Path) {
        val database = openDatabase(tmp)
        val storage = VariableStorage(database)

        storage.write("setup.keymap", "fr fr")
        storage.write("setup.keymap", "us us")

        assertEquals("us us", storage.read("setup.keymap"))
        database.connection().use { conn ->
            conn.prepareStatement("SELECT COUNT(*) FROM variables WHERE key = ?").use { stmt ->
                stmt.setString(1, "setup.keymap")
                stmt.executeQuery().use { rs ->
                    rs.next()
                    assertEquals(1, rs.getInt(1))
                }
            }
        }
    }

    private fun newStorage(tmp: Path): VariableStorage = VariableStorage(openDatabase(tmp))

    private fun openDatabase(tmp: Path): ConfigDatabase {
        val database = ConfigDatabase()
        database.open(tmp.resolve("kop.db"), OpenMode.READ_WRITE)
        return database
    }
}
