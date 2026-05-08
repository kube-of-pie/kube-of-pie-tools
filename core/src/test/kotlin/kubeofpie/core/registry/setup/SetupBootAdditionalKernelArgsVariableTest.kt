package kubeofpie.core.registry.setup

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

class SetupBootAdditionalKernelArgsVariableTest {

    @Test
    fun `metadata declares the variable as writable, unbounded, JSON-typed`(@TempDir tmp: Path) {
        val variable = newVariable(tmp)

        assertEquals("setup.boot.additional_kernel_args", variable.key)
        assertTrue(variable.writable)
        assertEquals(false, variable.sensitive)
        assertNull(variable.allowedValues())
    }

    @Test
    fun `write persists a JSON array, read returns it back`(@TempDir tmp: Path) {
        val variable = newVariable(tmp)

        variable.write("""["cgroup_memory=1","cgroup_enable=memory"]""")

        assertEquals("""["cgroup_memory=1","cgroup_enable=memory"]""", variable.read())
    }

    @Test
    fun `write accepts an empty JSON array`(@TempDir tmp: Path) {
        val variable = newVariable(tmp)

        variable.write("[]")

        assertEquals("[]", variable.read())
    }

    @Test
    fun `write rejects malformed JSON`(@TempDir tmp: Path) {
        val variable = newVariable(tmp)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            variable.write("[cgroup_memory=1]")
        }
        assertTrue(ex.message!!.contains("JSON array of strings"))
        assertNull(variable.read())
    }

    @Test
    fun `write rejects a non-array JSON value`(@TempDir tmp: Path) {
        val variable = newVariable(tmp)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            variable.write("\"cgroup_memory=1\"")
        }
        assertTrue(ex.message!!.contains("JSON array of strings"))
    }

    @Test
    fun `write rejects an array containing non-strings`(@TempDir tmp: Path) {
        val variable = newVariable(tmp)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            variable.write("[1, 2, 3]")
        }
        assertTrue(ex.message!!.contains("JSON array of strings"))
    }

    private fun newVariable(tmp: Path): SetupBootAdditionalKernelArgsVariable =
        SetupBootAdditionalKernelArgsVariable(VariableStorage(openDatabase(tmp)))

    private fun openDatabase(tmp: Path): ConfigDatabase {
        val database = ConfigDatabase()
        database.open(tmp.resolve("kop.db"), OpenMode.READ_WRITE)
        return database
    }
}
