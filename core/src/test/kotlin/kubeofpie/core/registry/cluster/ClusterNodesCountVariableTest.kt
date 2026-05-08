package kubeofpie.core.registry.cluster

import java.nio.file.Path
import kubeofpie.core.registry.VariableStorage
import kubeofpie.core.storage.ConfigDatabase
import kubeofpie.core.storage.OpenMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ClusterNodesCountVariableTest {

    @Test
    fun `metadata is unbounded writable non-sensitive`(@TempDir tmp: Path) {
        val variable = newVariable(tmp)

        assertEquals("cluster.nodes.count", variable.key)
        assertTrue(variable.writable)
        assertEquals(false, variable.sensitive)
        assertNull(variable.allowedValues())
    }

    @Test
    fun `write accepts a positive integer`(@TempDir tmp: Path) {
        val variable = newVariable(tmp)

        variable.write("3")

        assertEquals("3", variable.read())
    }

    @Test
    fun `write rejects zero`(@TempDir tmp: Path) {
        val variable = newVariable(tmp)

        val ex = assertThrows(IllegalArgumentException::class.java) { variable.write("0") }
        assertTrue(ex.message!!.contains("positive integer"))
        assertNull(variable.read())
    }

    @Test
    fun `write rejects negative integers`(@TempDir tmp: Path) {
        val variable = newVariable(tmp)

        assertThrows(IllegalArgumentException::class.java) { variable.write("-1") }
    }

    @Test
    fun `write rejects non-integer values`(@TempDir tmp: Path) {
        val variable = newVariable(tmp)

        assertThrows(IllegalArgumentException::class.java) { variable.write("two") }
    }

    private fun newVariable(tmp: Path): ClusterNodesCountVariable =
        ClusterNodesCountVariable(VariableStorage(openDatabase(tmp)))

    private fun openDatabase(tmp: Path): ConfigDatabase {
        val database = ConfigDatabase()
        database.open(tmp.resolve("kop.db"), OpenMode.READ_WRITE)
        return database
    }
}
