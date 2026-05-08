package kubeofpie.core.registry.setup

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

class SetupUsersFamilyTest {

    @Test
    fun `setup_users metadata is unbounded JSON-array writable`(@TempDir tmp: Path) {
        val storage = newStorage(tmp)
        val users = SetupUsersVariable(storage)

        assertEquals("setup.users", users.key)
        assertTrue(users.writable)
        assertEquals(false, users.sensitive)
        assertNull(users.allowedValues())
    }

    @Test
    fun `setup_users rejects non-array values`(@TempDir tmp: Path) {
        val users = SetupUsersVariable(newStorage(tmp))

        val ex = assertThrows(IllegalArgumentException::class.java) {
            users.write("\"root\"")
        }
        assertTrue(ex.message!!.contains("JSON array of strings"))
    }

    @Test
    fun `setup_users rejects names that do not match the POSIX shape`(@TempDir tmp: Path) {
        val users = SetupUsersVariable(newStorage(tmp))

        val ex = assertThrows(IllegalArgumentException::class.java) {
            users.write("[\"Root\"]")
        }
        assertTrue(ex.message!!.contains("invalid user name"))
    }

    @Test
    fun `setup_users rejects duplicates`(@TempDir tmp: Path) {
        val users = SetupUsersVariable(newStorage(tmp))

        val ex = assertThrows(IllegalArgumentException::class.java) {
            users.write("[\"root\",\"root\"]")
        }
        assertTrue(ex.message!!.contains("duplicate user name"))
    }

    @Test
    fun `family enumerates two keys per user, in registration order`(@TempDir tmp: Path) {
        val storage = newStorage(tmp)
        val users = SetupUsersVariable(storage).also { it.write("[\"root\",\"kubeofpie\"]") }
        val family = SetupUsersFamily(storage, users)

        assertEquals(
            listOf(
                "setup.users.root.password",
                "setup.users.root.ssh.private_key",
                "setup.users.kubeofpie.password",
                "setup.users.kubeofpie.ssh.private_key",
            ),
            family.keys(),
        )
    }

    @Test
    fun `family resolves password and ssh key variables for a known user`(@TempDir tmp: Path) {
        val storage = newStorage(tmp)
        val users = SetupUsersVariable(storage).also { it.write("[\"root\"]") }
        val family = SetupUsersFamily(storage, users)

        val password = family.variable("setup.users.root.password")
        assertTrue(password != null)
        assertTrue(password!!.writable)
        assertTrue(password.sensitive)

        val sshKey = family.variable("setup.users.root.ssh.private_key")
        assertTrue(sshKey != null)
        assertFalse(sshKey!!.writable)
        assertTrue(sshKey.sensitive)
    }

    @Test
    fun `family returns null for an unknown user`(@TempDir tmp: Path) {
        val storage = newStorage(tmp)
        val users = SetupUsersVariable(storage).also { it.write("[\"root\"]") }
        val family = SetupUsersFamily(storage, users)

        assertNull(family.variable("setup.users.nobody.password"))
    }

    @Test
    fun `family returns null for unrelated keys`(@TempDir tmp: Path) {
        val family = SetupUsersFamily(newStorage(tmp), SetupUsersVariable(newStorage(tmp)))

        assertNull(family.variable("setup.keymap"))
        assertNull(family.variable("setup.users.root.unknown"))
    }

    @Test
    fun `registry routes writes to the per-user password variable`(@TempDir tmp: Path) {
        val storage = newStorage(tmp)
        val users = SetupUsersVariable(storage).also { it.write("[\"root\"]") }
        val family = SetupUsersFamily(storage, users)
        val registry = VariableRegistry(listOf(users), listOf(family))

        registry.write("setup.users.root.password", "kubeofpie")

        assertEquals("kubeofpie", registry.read("setup.users.root.password")?.read())
    }

    @Test
    fun `registry rejects writes to the read-only ssh key`(@TempDir tmp: Path) {
        val storage = newStorage(tmp)
        val users = SetupUsersVariable(storage).also { it.write("[\"root\"]") }
        val family = SetupUsersFamily(storage, users)
        val registry = VariableRegistry(listOf(users), listOf(family))

        val ex = assertThrows(IllegalArgumentException::class.java) {
            registry.write("setup.users.root.ssh.private_key", "fake key")
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
