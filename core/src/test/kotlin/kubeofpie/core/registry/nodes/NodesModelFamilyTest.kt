package kubeofpie.core.registry.nodes

import io.micronaut.context.ApplicationContext
import java.nio.file.Path
import kubeofpie.core.catalogue.RaspberryPiCatalogue
import kubeofpie.core.registry.VariableRegistry
import kubeofpie.core.registry.VariableStorage
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
class NodesModelFamilyTest {

    private lateinit var ctx: ApplicationContext
    private lateinit var catalogue: RaspberryPiCatalogue

    @BeforeAll
    fun setUp() {
        ctx = ApplicationContext.run()
        catalogue = ctx.getBean(RaspberryPiCatalogue::class.java)
    }

    @AfterAll
    fun tearDown() {
        ctx.close()
    }

    @Test
    fun `family is empty until at least one node is added`(@TempDir tmp: Path) {
        val storage = newStorage(tmp)
        val nodes = NodesVariable(storage)
        val family = NodesModelFamily(storage, nodes, catalogue)

        assertEquals(emptyList<String>(), family.keys())
        assertNull(family.variable("nodes.0.model"))
    }

    @Test
    fun `family enumerates one key per node, one entry per index up to count`(@TempDir tmp: Path) {
        val storage = newStorage(tmp)
        val nodes = NodesVariable(storage).also { it.add(null); it.add(null) }
        val family = NodesModelFamily(storage, nodes, catalogue)

        assertEquals(listOf("nodes.0.model", "nodes.1.model"), family.keys())
    }

    @Test
    fun `family resolves the per-node model variable with catalogue-backed allowed values`(@TempDir tmp: Path) {
        val storage = newStorage(tmp)
        val nodes = NodesVariable(storage).also { it.add(null) }
        val family = NodesModelFamily(storage, nodes, catalogue)

        val model = family.variable("nodes.0.model")!!
        assertTrue(model.writable)
        assertEquals(false, model.sensitive)
        assertEquals(listOf("pi4", "pi5"), model.allowedValues())
    }

    @Test
    fun `family rejects out-of-range indices`(@TempDir tmp: Path) {
        val storage = newStorage(tmp)
        val nodes = NodesVariable(storage).also { it.add(null) }
        val family = NodesModelFamily(storage, nodes, catalogue)

        assertNull(family.variable("nodes.1.model"))
        assertNull(family.variable("nodes.5.model"))
    }

    @Test
    fun `family ignores unrelated keys`(@TempDir tmp: Path) {
        val storage = newStorage(tmp)
        val nodes = NodesVariable(storage).also { it.add(null) }
        val family = NodesModelFamily(storage, nodes, catalogue)

        assertNull(family.variable("setup.keymap"))
        assertNull(family.variable("nodes.0.network.hostname"))
        assertNull(family.variable("nodes.x.model"))
    }

    @Test
    fun `registry round-trips per-node model isolated by index`(@TempDir tmp: Path) {
        val storage = newStorage(tmp)
        val nodes = NodesVariable(storage).also { it.add(null); it.add(null) }
        val family = NodesModelFamily(storage, nodes, catalogue)
        val registry = VariableRegistry(listOf(nodes), listOf(family))

        registry.write("nodes.0.model", "pi4")
        registry.write("nodes.1.model", "pi5")

        assertEquals("pi4", registry.read("nodes.0.model")?.read())
        assertEquals("pi5", registry.read("nodes.1.model")?.read())
    }

    @Test
    fun `registry rejects an unsupported model`(@TempDir tmp: Path) {
        val storage = newStorage(tmp)
        val nodes = NodesVariable(storage).also { it.add(null) }
        val family = NodesModelFamily(storage, nodes, catalogue)
        val registry = VariableRegistry(listOf(nodes), listOf(family))

        val ex = assertThrows(IllegalArgumentException::class.java) {
            registry.write("nodes.0.model", "pi-not-a-thing")
        }
        assertTrue(ex.message!!.contains("not allowed"))
    }

    private fun newStorage(tmp: Path): VariableStorage = VariableStorage(openDatabase(tmp))

    private fun openDatabase(tmp: Path): ConfigDatabase {
        val database = ConfigDatabase()
        database.open(tmp.resolve("kop.db"), OpenMode.READ_WRITE)
        return database
    }
}
