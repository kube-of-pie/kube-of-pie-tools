package kubeofpie.core.registry.nodes

import io.micronaut.context.ApplicationContext
import java.nio.file.Path
import kubeofpie.core.catalogue.AlpineCatalogue
import kubeofpie.core.registry.VariableRegistry
import kubeofpie.core.registry.VariableStorage
import kubeofpie.core.registry.VersionAlpineVariable
import kubeofpie.core.storage.ConfigDatabase
import kubeofpie.core.storage.OpenMode
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NodesKernelArgsFamilyTest {

    private lateinit var ctx: ApplicationContext
    private lateinit var catalogue: AlpineCatalogue

    @BeforeAll
    fun setUp() {
        ctx = ApplicationContext.run()
        catalogue = ctx.getBean(AlpineCatalogue::class.java)
    }

    @AfterAll
    fun tearDown() {
        ctx.close()
    }

    @Test
    fun `family is empty until at least one node is added`(@TempDir tmp: Path) {
        val storage = newStorage(tmp)
        val nodes = NodesVariable(storage)
        val alpine = VersionAlpineVariable(storage, catalogue)
        val family = NodesKernelArgsFamily(nodes, alpine, catalogue)

        assertEquals(emptyList<String>(), family.keys())
        assertNull(family.variable("nodes.0.kernel_args"))
    }

    @Test
    fun `family enumerates one key per node`(@TempDir tmp: Path) {
        val storage = newStorage(tmp)
        val nodes = NodesVariable(storage).also { it.add(null); it.add(null) }
        val alpine = VersionAlpineVariable(storage, catalogue)
        val family = NodesKernelArgsFamily(nodes, alpine, catalogue)

        assertEquals(listOf("nodes.0.kernel_args", "nodes.1.kernel_args"), family.keys())
    }

    @Test
    fun `per-node variable is read-only, unbounded, non-sensitive`(@TempDir tmp: Path) {
        val storage = newStorage(tmp)
        val nodes = NodesVariable(storage).also { it.add(null) }
        val alpine = VersionAlpineVariable(storage, catalogue)
        val family = NodesKernelArgsFamily(nodes, alpine, catalogue)

        val variable = family.variable("nodes.0.kernel_args")!!
        assertEquals(false, variable.writable)
        assertEquals(false, variable.sensitive)
        assertNull(variable.allowedValues())
    }

    @Test
    fun `read returns null when version_alpine is unset`(@TempDir tmp: Path) {
        val storage = newStorage(tmp)
        val nodes = NodesVariable(storage).also { it.add(null) }
        val alpine = VersionAlpineVariable(storage, catalogue)
        val family = NodesKernelArgsFamily(nodes, alpine, catalogue)

        val variable = family.variable("nodes.0.kernel_args")!!
        assertNull(variable.read())
    }

    @Test
    fun `read returns space-separated kernel args from the alpine catalogue when version_alpine is set`(@TempDir tmp: Path) {
        val storage = newStorage(tmp)
        val nodes = NodesVariable(storage).also { it.add(null) }
        val alpine = VersionAlpineVariable(storage, catalogue).also { it.write("3.21") }
        val family = NodesKernelArgsFamily(nodes, alpine, catalogue)

        val variable = family.variable("nodes.0.kernel_args")!!
        assertEquals("cgroup_memory=1 cgroup_enable=memory", variable.read())
    }

    @Test
    fun `family rejects out-of-range indices`(@TempDir tmp: Path) {
        val storage = newStorage(tmp)
        val nodes = NodesVariable(storage).also { it.add(null) }
        val alpine = VersionAlpineVariable(storage, catalogue)
        val family = NodesKernelArgsFamily(nodes, alpine, catalogue)

        assertNull(family.variable("nodes.1.kernel_args"))
        assertNull(family.variable("nodes.5.kernel_args"))
    }

    @Test
    fun `family ignores unrelated keys`(@TempDir tmp: Path) {
        val storage = newStorage(tmp)
        val nodes = NodesVariable(storage).also { it.add(null) }
        val alpine = VersionAlpineVariable(storage, catalogue)
        val family = NodesKernelArgsFamily(nodes, alpine, catalogue)

        assertNull(family.variable("setup.keymap"))
        assertNull(family.variable("nodes.0.model"))
        assertNull(family.variable("nodes.x.kernel_args"))
    }

    @Test
    fun `registry rejects writes to the per-node kernel args`(@TempDir tmp: Path) {
        val storage = newStorage(tmp)
        val nodes = NodesVariable(storage).also { it.add(null) }
        val alpine = VersionAlpineVariable(storage, catalogue).also { it.write("3.21") }
        val family = NodesKernelArgsFamily(nodes, alpine, catalogue)
        val registry = VariableRegistry(listOf(nodes, alpine), listOf(family))

        val ex = assertThrows(IllegalArgumentException::class.java) {
            registry.write("nodes.0.kernel_args", "anything")
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
