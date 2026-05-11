package kubeofpie.core.registry.nodes

import io.micronaut.context.ApplicationContext
import java.nio.file.Path
import kubeofpie.core.business.nodes.NodeManager
import kubeofpie.core.registry.VariableRegistry
import kubeofpie.core.registry.VersionAlpineVariable
import kubeofpie.core.storage.ConfigDatabase
import kubeofpie.core.storage.OpenMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class NodesKernelArgsFamilyTest {

    @Test
    fun `family is empty until at least one node is added`(@TempDir tmp: Path) = withFamily(tmp) { family, _, _ ->
        assertEquals(emptyList<String>(), family.keys())
        assertNull(family.variable("nodes.master.kernel_args"))
    }

    @Test
    fun `family enumerates one key per node`(@TempDir tmp: Path) = withFamily(tmp) { family, manager, _ ->
        manager.add("master")
        manager.add("worker-1")

        assertEquals(
            listOf("nodes.master.kernel_args", "nodes.worker-1.kernel_args"),
            family.keys(),
        )
    }

    @Test
    fun `per-node variable is read-only, unbounded, non-sensitive`(@TempDir tmp: Path) =
        withFamily(tmp) { family, manager, _ ->
            manager.add("master")

            val variable = family.variable("nodes.master.kernel_args")!!
            assertEquals(false, variable.writable)
            assertEquals(false, variable.sensitive)
            assertNull(variable.allowedValues())
        }

    @Test
    fun `read returns null when version_alpine is unset`(@TempDir tmp: Path) =
        withFamily(tmp) { family, manager, _ ->
            manager.add("master")

            val variable = family.variable("nodes.master.kernel_args")!!
            assertNull(variable.read())
        }

    @Test
    fun `read returns space-separated kernel args from the alpine catalogue when version_alpine is set`(
        @TempDir tmp: Path,
    ) = withFamily(tmp) { family, manager, alpine ->
        manager.add("master")
        alpine.write("3.21")

        val variable = family.variable("nodes.master.kernel_args")!!
        assertEquals("cgroup_memory=1 cgroup_enable=memory", variable.read())
    }

    @Test
    fun `family rejects unknown node ids`(@TempDir tmp: Path) = withFamily(tmp) { family, manager, _ ->
        manager.add("master")

        assertNull(family.variable("nodes.worker-1.kernel_args"))
        assertNull(family.variable("nodes.ghost.kernel_args"))
    }

    @Test
    fun `family ignores unrelated keys`(@TempDir tmp: Path) = withFamily(tmp) { family, manager, _ ->
        manager.add("master")

        assertNull(family.variable("setup.keymap"))
        assertNull(family.variable("nodes.master.model"))
        assertNull(family.variable("nodes.Master.kernel_args"))
    }

    @Test
    fun `registry rejects writes to the per-node kernel args`(@TempDir tmp: Path) =
        withFamily(tmp) { family, manager, alpine ->
            val nodesVariable = NodesVariable(manager)
            manager.add("master")
            alpine.write("3.21")
            val registry = VariableRegistry(listOf(nodesVariable, alpine), listOf(family))

            val ex = assertThrows(IllegalArgumentException::class.java) {
                registry.write("nodes.master.kernel_args", "anything")
            }
            assertTrue(ex.message!!.contains("not user-writable"), ex.message)
        }

    private fun withFamily(
        tmp: Path,
        block: (NodesKernelArgsFamily, NodeManager, VersionAlpineVariable) -> Unit,
    ) {
        ApplicationContext.run().use { ctx ->
            ctx.getBean(ConfigDatabase::class.java)
                .open(tmp.resolve("kop.db"), OpenMode.READ_WRITE)
            val manager = ctx.getBean(NodeManager::class.java)
            val alpine = ctx.getBean(VersionAlpineVariable::class.java)
            block(NodesKernelArgsFamily(manager), manager, alpine)
        }
    }
}
