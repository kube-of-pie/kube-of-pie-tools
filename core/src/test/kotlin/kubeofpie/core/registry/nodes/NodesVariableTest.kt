package kubeofpie.core.registry.nodes

import java.nio.file.Path
import kubeofpie.core.registry.VariableRegistry
import kubeofpie.core.registry.VariableStorage
import kubeofpie.core.storage.ConfigDatabase
import kubeofpie.core.storage.OpenMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class NodesVariableTest {

    @Test
    fun `metadata is non-writable listable`(@TempDir tmp: Path) {
        val nodes = NodesVariable(newStorage(tmp))

        assertEquals("nodes", nodes.key)
        assertFalse(nodes.writable)
        assertEquals(false, nodes.sensitive)
        assertNull(nodes.allowedValues())
        assertEquals(emptyList<String>(), nodes.identifiers())
    }

    @Test
    fun `add appends sequential indices starting at 0`(@TempDir tmp: Path) {
        val nodes = NodesVariable(newStorage(tmp))

        assertEquals("0", nodes.add(null))
        assertEquals("1", nodes.add(null))
        assertEquals("2", nodes.add(null))

        assertEquals(listOf("0", "1", "2"), nodes.identifiers())
    }

    @Test
    fun `add accepts an explicit id only when it equals the next index`(@TempDir tmp: Path) {
        val nodes = NodesVariable(newStorage(tmp))

        assertEquals("0", nodes.add("0"))
        val ex = assertThrows(IllegalArgumentException::class.java) { nodes.add("5") }
        assertTrue(ex.message!!.contains("next index"))
    }

    @Test
    fun `remove only accepts the highest current index`(@TempDir tmp: Path) {
        val nodes = NodesVariable(newStorage(tmp)).also {
            it.add(null); it.add(null); it.add(null)
        }

        val ex = assertThrows(IllegalArgumentException::class.java) { nodes.remove("0") }
        assertTrue(ex.message!!.contains("highest index"))

        nodes.remove("2")
        assertEquals(listOf("0", "1"), nodes.identifiers())
    }

    @Test
    fun `remove on an empty family is rejected`(@TempDir tmp: Path) {
        val nodes = NodesVariable(newStorage(tmp))

        val ex = assertThrows(IllegalArgumentException::class.java) { nodes.remove("0") }
        assertTrue(ex.message!!.contains("no nodes to remove"))
    }

    @Test
    fun `registry rejects writes to the listable nodes head`(@TempDir tmp: Path) {
        val nodes = NodesVariable(newStorage(tmp))
        val registry = VariableRegistry(listOf(nodes), emptyList())

        val ex = assertThrows(IllegalArgumentException::class.java) {
            registry.write("nodes", "3")
        }
        assertTrue(ex.message!!.contains("not user-writable"))
    }

    private fun newStorage(tmp: Path): VariableStorage = VariableStorage(openDatabase(tmp))

    private fun openDatabase(tmp: Path): ConfigDatabase {
        val database = ConfigDatabase()
        database.open(tmp.resolve("kop.db"), OpenMode.READ_WRITE)
        return database
    }
}
