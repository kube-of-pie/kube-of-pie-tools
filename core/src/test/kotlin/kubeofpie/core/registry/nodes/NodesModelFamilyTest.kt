package kubeofpie.core.registry.nodes

import io.micronaut.context.ApplicationContext
import java.nio.file.Path
import kubeofpie.core.business.nodes.NodeManager
import kubeofpie.core.registry.VariableRegistry
import kubeofpie.core.storage.ConfigDatabase
import kubeofpie.core.storage.OpenMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class NodesModelFamilyTest {

    @Test
    fun `family is empty until at least one node is added`(@TempDir tmp: Path) = withFamily(tmp) { family, _ ->
        assertEquals(emptyList<String>(), family.keys())
        assertNull(family.variable("nodes.master.model"))
    }

    @Test
    fun `family enumerates one key per node, in registration order`(@TempDir tmp: Path) =
        withFamily(tmp) { family, manager ->
            manager.add("master")
            manager.add("worker-1")

            assertEquals(listOf("nodes.master.model", "nodes.worker-1.model"), family.keys())
        }

    @Test
    fun `family resolves the per-node model variable with catalogue-backed allowed values`(@TempDir tmp: Path) =
        withFamily(tmp) { family, manager ->
            manager.add("master")

            val model = family.variable("nodes.master.model")!!
            assertTrue(model.writable)
            assertEquals(false, model.sensitive)
            assertEquals(listOf("pi4", "pi5"), model.allowedValues())
        }

    @Test
    fun `family rejects unknown node ids`(@TempDir tmp: Path) = withFamily(tmp) { family, manager ->
        manager.add("master")

        assertNull(family.variable("nodes.worker-1.model"))
        assertNull(family.variable("nodes.ghost.model"))
    }

    @Test
    fun `family ignores unrelated keys`(@TempDir tmp: Path) = withFamily(tmp) { family, manager ->
        manager.add("master")

        assertNull(family.variable("setup.keymap"))
        assertNull(family.variable("nodes.master.network.hostname"))
        assertNull(family.variable("nodes.Master.model"))
    }

    @Test
    fun `registry round-trips per-node model isolated by id`(@TempDir tmp: Path) =
        withFamily(tmp) { family, manager ->
            val nodesVariable = NodesVariable(manager)
            manager.add("master")
            manager.add("worker-1")
            val registry = VariableRegistry(listOf(nodesVariable), listOf(family))

            registry.write("nodes.master.model", "pi4")
            registry.write("nodes.worker-1.model", "pi5")

            assertEquals("pi4", registry.read("nodes.master.model")?.read())
            assertEquals("pi5", registry.read("nodes.worker-1.model")?.read())
        }

    @Test
    fun `registry rejects an unsupported model`(@TempDir tmp: Path) =
        withFamily(tmp) { family, manager ->
            val nodesVariable = NodesVariable(manager)
            manager.add("master")
            val registry = VariableRegistry(listOf(nodesVariable), listOf(family))

            val ex = assertThrows(IllegalArgumentException::class.java) {
                registry.write("nodes.master.model", "pi-not-a-thing")
            }
            assertTrue(ex.message!!.contains("not allowed"), ex.message)
        }

    private fun withFamily(tmp: Path, block: (NodesModelFamily, NodeManager) -> Unit) {
        ApplicationContext.run().use { ctx ->
            ctx.getBean(ConfigDatabase::class.java)
                .open(tmp.resolve("kop.db"), OpenMode.READ_WRITE)
            val manager = ctx.getBean(NodeManager::class.java)
            block(NodesModelFamily(manager), manager)
        }
    }
}
