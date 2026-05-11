package kubeofpie.core.registry.nodes

import io.micronaut.context.ApplicationContext
import java.nio.file.Path
import kubeofpie.core.business.nodes.NodeManager
import kubeofpie.core.registry.VariableRegistry
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
    fun `metadata is non-writable listable`(@TempDir tmp: Path) = withNodes(tmp) { nodes, _ ->
        assertEquals("nodes", nodes.key)
        assertFalse(nodes.writable)
        assertEquals(false, nodes.sensitive)
        assertNull(nodes.allowedValues())
        assertNull(nodes.read())
        assertEquals(emptyList<String>(), nodes.identifiers())
    }

    @Test
    fun `add delegates to the manager and identifiers reflects state`(@TempDir tmp: Path) =
        withNodes(tmp) { nodes, _ ->
            assertEquals("master", nodes.add("master"))
            assertEquals("worker-1", nodes.add("worker-1"))
            assertEquals(listOf("master", "worker-1"), nodes.identifiers())
        }

    @Test
    fun `add rejects null id`(@TempDir tmp: Path) = withNodes(tmp) { nodes, _ ->
        val ex = assertThrows(IllegalArgumentException::class.java) { nodes.add(null) }
        assertTrue(ex.message!!.contains("requires a node id"), ex.message)
    }

    @Test
    fun `add rejects ids that violate the DNS label shape`(@TempDir tmp: Path) =
        withNodes(tmp) { nodes, _ ->
            val ex = assertThrows(IllegalArgumentException::class.java) { nodes.add("Master") }
            assertTrue(ex.message!!.contains("invalid node id"), ex.message)
        }

    @Test
    fun `add rejects duplicates`(@TempDir tmp: Path) = withNodes(tmp) { nodes, _ ->
        nodes.add("master")
        val ex = assertThrows(IllegalArgumentException::class.java) { nodes.add("master") }
        assertTrue(ex.message!!.contains("already exists"), ex.message)
    }

    @Test
    fun `remove drops a known id and rejects unknown ones`(@TempDir tmp: Path) =
        withNodes(tmp) { nodes, _ ->
            nodes.add("master")
            nodes.add("worker-1")

            nodes.remove("master")
            assertEquals(listOf("worker-1"), nodes.identifiers())

            val ex = assertThrows(IllegalArgumentException::class.java) { nodes.remove("ghost") }
            assertTrue(ex.message!!.contains("not registered"), ex.message)
        }

    @Test
    fun `registry rejects writes to the listable nodes head`(@TempDir tmp: Path) =
        withNodes(tmp) { nodes, _ ->
            val registry = VariableRegistry(listOf(nodes), emptyList())

            val ex = assertThrows(IllegalArgumentException::class.java) {
                registry.write("nodes", "[\"master\"]")
            }
            assertTrue(ex.message!!.contains("not user-writable"), ex.message)
        }

    private fun withNodes(tmp: Path, block: (NodesVariable, NodeManager) -> Unit) {
        ApplicationContext.run().use { ctx ->
            ctx.getBean(ConfigDatabase::class.java)
                .open(tmp.resolve("kop.db"), OpenMode.READ_WRITE)
            val manager = ctx.getBean(NodeManager::class.java)
            block(NodesVariable(manager), manager)
        }
    }
}
