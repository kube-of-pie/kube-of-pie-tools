package kubeofpie.core.registry.setup

import java.nio.file.Path
import kubeofpie.core.registry.VariableRegistry
import kubeofpie.core.registry.VariableStorage
import kubeofpie.core.storage.ConfigDatabase
import kubeofpie.core.storage.OpenMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Metadata + registry-validation coverage for the simple cluster-wide setup
 * variables. Storage round-trips are covered by [VariableStorageTest].
 */
class SetupVariablesTest {

    @Test
    fun `keymap metadata is unbounded free text`(@TempDir tmp: Path) {
        val variable = SetupKeymapVariable(newStorage(tmp))

        assertEquals("setup.keymap", variable.key)
        assertTrue(variable.writable)
        assertEquals(false, variable.sensitive)
        assertNull(variable.allowedValues())
    }

    @Test
    fun `sshd_enabled metadata enforces a boolean string`(@TempDir tmp: Path) {
        val variable = SetupSshdEnabledVariable(newStorage(tmp))

        assertEquals("setup.sshd.enabled", variable.key)
        assertTrue(variable.writable)
        assertEquals(false, variable.sensitive)
        assertEquals(listOf("true", "false"), variable.allowedValues())
    }

    @Test
    fun `timezone metadata is unbounded free text`(@TempDir tmp: Path) {
        val variable = SetupTimezoneVariable(newStorage(tmp))

        assertEquals("setup.timezone", variable.key)
        assertTrue(variable.writable)
        assertEquals(false, variable.sensitive)
        assertNull(variable.allowedValues())
    }

    @Test
    fun `ntp metadata bounds the value to the four supported clients`(@TempDir tmp: Path) {
        val variable = SetupNtpVariable(newStorage(tmp))

        assertEquals("setup.ntp", variable.key)
        assertTrue(variable.writable)
        assertEquals(false, variable.sensitive)
        assertEquals(listOf("busybox", "chrony", "openntpd", "none"), variable.allowedValues())
    }

    @Test
    fun `registry rejects ntp values outside allowedValues`(@TempDir tmp: Path) {
        val variable = SetupNtpVariable(newStorage(tmp))
        val registry = VariableRegistry(listOf(variable), emptyList())

        val ex = assertThrows(IllegalArgumentException::class.java) {
            registry.write("setup.ntp", "ntpd")
        }
        assertTrue(ex.message!!.contains("not allowed"))
        assertNull(variable.read())
    }

    @Test
    fun `registry rejects sshd_enabled values that are not boolean strings`(@TempDir tmp: Path) {
        val variable = SetupSshdEnabledVariable(newStorage(tmp))
        val registry = VariableRegistry(listOf(variable), emptyList())

        val ex = assertThrows(IllegalArgumentException::class.java) {
            registry.write("setup.sshd.enabled", "yes")
        }
        assertTrue(ex.message!!.contains("not allowed"))
    }

    @Test
    fun `registry round-trip persists keymap free text`(@TempDir tmp: Path) {
        val variable = SetupKeymapVariable(newStorage(tmp))
        val registry = VariableRegistry(listOf(variable), emptyList())

        registry.write("setup.keymap", "fr fr")

        assertEquals("fr fr", registry.read("setup.keymap")?.read())
    }

    private fun newStorage(tmp: Path): VariableStorage = VariableStorage(openDatabase(tmp))

    private fun openDatabase(tmp: Path): ConfigDatabase {
        val database = ConfigDatabase()
        database.open(tmp.resolve("kop.db"), OpenMode.READ_WRITE)
        return database
    }
}
