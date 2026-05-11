package kubeofpie.core.registry

import io.micronaut.context.ApplicationContext
import java.nio.file.Path
import kubeofpie.core.business.versions.AlpineVersionManager
import kubeofpie.core.storage.ConfigDatabase
import kubeofpie.core.storage.OpenMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Storage and validation live in [AlpineVersionManager] — that class owns the
 * persistence assertions. This test stays at the adapter layer: state is set up via
 * the manager and observed through the variable (for reads), or set via the variable
 * and observed through the manager (for writes). The registry tests cover the
 * dotted-key contract that the variable participates in.
 */
class VersionAlpineVariableTest {

    @Test
    fun `read returns null before the manager has been set`(@TempDir tmp: Path) =
        withContext(tmp) { _, variable ->
            assertNull(variable.read())
        }

    @Test
    fun `read returns whatever the manager has pinned`(@TempDir tmp: Path) =
        withContext(tmp) { manager, variable ->
            manager.set("3.21")

            assertEquals("3.21", variable.read())
        }

    @Test
    fun `write delegates to the manager`(@TempDir tmp: Path) =
        withContext(tmp) { manager, variable ->
            variable.write("3.21")

            assertEquals("3.21", manager.get())
        }

    @Test
    fun `registry rejects values outside allowedValues`(@TempDir tmp: Path) =
        withContext(tmp) { manager, variable ->
            val registry = VariableRegistry(listOf(variable), emptyList())

            val ex = assertThrows(IllegalArgumentException::class.java) {
                registry.write("version.alpine", "3.99")
            }
            assertTrue(ex.message!!.contains("not allowed"))
            assertNull(manager.get())
        }

    @Test
    fun `registry round-trip persists through write`(@TempDir tmp: Path) =
        withContext(tmp) { manager, variable ->
            val registry = VariableRegistry(listOf(variable), emptyList())

            registry.write("version.alpine", "3.21")

            assertEquals("3.21", manager.get())
            assertEquals("3.21", registry.read("version.alpine")?.read())
        }

    @Test
    fun `metadata matches the registry contract`(@TempDir tmp: Path) =
        withContext(tmp) { _, variable ->
            assertEquals("version.alpine", variable.key)
            assertTrue(variable.writable)
            assertEquals(false, variable.sensitive)
            assertEquals(listOf("3.21"), variable.allowedValues())
        }

    private fun withContext(
        tmp: Path,
        block: (AlpineVersionManager, VersionAlpineVariable) -> Unit,
    ) {
        ApplicationContext.run().use { ctx ->
            ctx.getBean(ConfigDatabase::class.java)
                .open(tmp.resolve("kop.db"), OpenMode.READ_WRITE)
            block(
                ctx.getBean(AlpineVersionManager::class.java),
                ctx.getBean(VersionAlpineVariable::class.java),
            )
        }
    }
}
