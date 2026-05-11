package kubeofpie.core.registry.setup

import io.micronaut.context.ApplicationContext
import java.nio.file.Path
import kubeofpie.core.catalogue.AlpineCatalogue
import kubeofpie.core.registry.VariableStorage
import kubeofpie.core.registry.VersionAlpineVariable
import kubeofpie.core.storage.ConfigDatabase
import kubeofpie.core.storage.OpenMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class SetupBootAdditionalKernelArgsVariableTest {

    @Test
    fun `metadata declares the variable as read-only, unbounded, non-sensitive`(@TempDir tmp: Path) {
        withVariable(tmp, alpineVersion = null) { variable ->
            assertEquals("setup.boot.additional_kernel_args", variable.key)
            assertEquals(false, variable.writable)
            assertEquals(false, variable.sensitive)
            assertNull(variable.allowedValues())
        }
    }

    @Test
    fun `read returns null when version_alpine is unset`(@TempDir tmp: Path) {
        withVariable(tmp, alpineVersion = null) { variable ->
            assertNull(variable.read())
        }
    }

    @Test
    fun `read returns space-separated kernel args from the alpine catalogue when version_alpine is set`(@TempDir tmp: Path) {
        withVariable(tmp, alpineVersion = "3.21") { variable ->
            assertEquals("cgroup_memory=1 cgroup_enable=memory", variable.read())
        }
    }

    @Test
    fun `write throws UnsupportedOperationException`(@TempDir tmp: Path) {
        withVariable(tmp, alpineVersion = "3.21") { variable ->
            assertThrows(UnsupportedOperationException::class.java) {
                variable.write("anything")
            }
        }
    }

    private fun withVariable(
        tmp: Path,
        alpineVersion: String?,
        block: (SetupBootAdditionalKernelArgsVariable) -> Unit,
    ) {
        ApplicationContext.run().use { ctx ->
            val catalogue = ctx.getBean(AlpineCatalogue::class.java)
            val database = ConfigDatabase()
            database.open(tmp.resolve("kop.db"), OpenMode.READ_WRITE)
            val storage = VariableStorage(database)
            val alpine = VersionAlpineVariable(storage, catalogue)
            if (alpineVersion != null) alpine.write(alpineVersion)
            block(SetupBootAdditionalKernelArgsVariable(alpine, catalogue))
        }
    }
}
