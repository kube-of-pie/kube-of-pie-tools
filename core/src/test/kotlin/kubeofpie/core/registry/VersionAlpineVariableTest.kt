package kubeofpie.core.registry

import java.nio.file.Path
import kubeofpie.core.catalogue.AlpineCatalogue
import kubeofpie.core.storage.ConfigDatabase
import kubeofpie.core.storage.OpenMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class VersionAlpineVariableTest {

    @Test
    fun `read returns null before any write`(@TempDir tmp: Path) {
        val variable = newVariable(tmp)

        assertNull(variable.read())
    }

    @Test
    fun `write then read returns the persisted value`(@TempDir tmp: Path) {
        val variable = newVariable(tmp)

        variable.write("3.21")

        assertEquals("3.21", variable.read())
    }

    @Test
    fun `write twice for the same key updates rather than duplicating`(@TempDir tmp: Path) {
        val database = openDatabase(tmp)
        val variable = VersionAlpineVariable(database, AlpineCatalogue())

        variable.write("3.21")
        variable.write("3.21") // upsert path

        database.connection().use { conn ->
            conn.prepareStatement("SELECT COUNT(*) FROM variables WHERE key = ?").use { stmt ->
                stmt.setString(1, "version.alpine")
                stmt.executeQuery().use { rs ->
                    rs.next()
                    assertEquals(1, rs.getInt(1))
                }
            }
        }
    }

    @Test
    fun `registry rejects values outside allowedValues`(@TempDir tmp: Path) {
        val variable = newVariable(tmp)
        val registry = VariableRegistry(listOf(variable))

        val ex = assertThrows(IllegalArgumentException::class.java) {
            registry.write("version.alpine", "3.99")
        }
        assertTrue(ex.message!!.contains("not allowed"))
        assertNull(variable.read())
    }

    @Test
    fun `registry round-trip persists through write`(@TempDir tmp: Path) {
        val variable = newVariable(tmp)
        val registry = VariableRegistry(listOf(variable))

        registry.write("version.alpine", "3.21")

        assertEquals("3.21", registry.read("version.alpine")?.read())
    }

    @Test
    fun `metadata matches the registry contract`(@TempDir tmp: Path) {
        val variable = newVariable(tmp)

        assertEquals("version.alpine", variable.key)
        assertTrue(variable.writable)
        assertEquals(false, variable.sensitive)
        assertEquals(listOf("3.21"), variable.allowedValues())
    }

    private fun newVariable(tmp: Path): VersionAlpineVariable =
        VersionAlpineVariable(openDatabase(tmp), AlpineCatalogue())

    private fun openDatabase(tmp: Path): ConfigDatabase {
        val database = ConfigDatabase()
        database.open(tmp.resolve("kop.db"), OpenMode.READ_WRITE)
        return database
    }
}
