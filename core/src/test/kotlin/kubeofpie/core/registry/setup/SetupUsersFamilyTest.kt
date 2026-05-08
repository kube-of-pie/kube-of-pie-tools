package kubeofpie.core.registry.setup

import java.nio.file.Path
import kubeofpie.core.registry.VariableRegistry
import kubeofpie.core.registry.VariableStorage
import kubeofpie.core.secrets.SshKeyGenerator
import kubeofpie.core.storage.ConfigDatabase
import kubeofpie.core.storage.OpenMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
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
    fun `family enumerates three keys per user, in registration order`(@TempDir tmp: Path) {
        val storage = newStorage(tmp)
        val users = SetupUsersVariable(storage).also { it.write("[\"root\",\"kubeofpie\"]") }
        val family = SetupUsersFamily(storage, users, SshKeyGenerator())

        assertEquals(
            listOf(
                "setup.users.root.password",
                "setup.users.root.ssh.private_key",
                "setup.users.root.ssh.public_key",
                "setup.users.kubeofpie.password",
                "setup.users.kubeofpie.ssh.private_key",
                "setup.users.kubeofpie.ssh.public_key",
            ),
            family.keys(),
        )
    }

    @Test
    fun `family resolves password and ssh key variables for a known user`(@TempDir tmp: Path) {
        val storage = newStorage(tmp)
        val users = SetupUsersVariable(storage).also { it.write("[\"root\"]") }
        val family = SetupUsersFamily(storage, users, SshKeyGenerator())

        val password = family.variable("setup.users.root.password")
        assertNotNull(password)
        assertTrue(password!!.writable)
        assertTrue(password.sensitive)

        val sshPrivate = family.variable("setup.users.root.ssh.private_key")
        assertNotNull(sshPrivate)
        assertFalse(sshPrivate!!.writable)
        assertTrue(sshPrivate.sensitive)

        val sshPublic = family.variable("setup.users.root.ssh.public_key")
        assertNotNull(sshPublic)
        assertFalse(sshPublic!!.writable)
        assertFalse(sshPublic.sensitive)
    }

    @Test
    fun `family returns null for an unknown user`(@TempDir tmp: Path) {
        val storage = newStorage(tmp)
        val users = SetupUsersVariable(storage).also { it.write("[\"root\"]") }
        val family = SetupUsersFamily(storage, users, SshKeyGenerator())

        assertNull(family.variable("setup.users.nobody.password"))
    }

    @Test
    fun `family returns null for unrelated keys`(@TempDir tmp: Path) {
        val family = SetupUsersFamily(newStorage(tmp), SetupUsersVariable(newStorage(tmp)), SshKeyGenerator())

        assertNull(family.variable("setup.keymap"))
        assertNull(family.variable("setup.users.root.unknown"))
    }

    @Test
    fun `registry routes writes to the per-user password variable`(@TempDir tmp: Path) {
        val storage = newStorage(tmp)
        val users = SetupUsersVariable(storage).also { it.write("[\"root\"]") }
        val family = SetupUsersFamily(storage, users, SshKeyGenerator())
        val registry = VariableRegistry(listOf(users), listOf(family))

        registry.write("setup.users.root.password", "kubeofpie")

        assertEquals("kubeofpie", registry.read("setup.users.root.password")?.read())
    }

    @Test
    fun `registry rejects writes to the read-only ssh private key`(@TempDir tmp: Path) {
        val storage = newStorage(tmp)
        val users = SetupUsersVariable(storage).also { it.write("[\"root\"]") }
        val family = SetupUsersFamily(storage, users, SshKeyGenerator())
        val registry = VariableRegistry(listOf(users), listOf(family))

        val ex = assertThrows(IllegalArgumentException::class.java) {
            registry.write("setup.users.root.ssh.private_key", "fake key")
        }
        assertTrue(ex.message!!.contains("not user-writable"))
    }

    @Test
    fun `registry rejects writes to the read-only ssh public key`(@TempDir tmp: Path) {
        val storage = newStorage(tmp)
        val users = SetupUsersVariable(storage).also { it.write("[\"root\"]") }
        val family = SetupUsersFamily(storage, users, SshKeyGenerator())
        val registry = VariableRegistry(listOf(users), listOf(family))

        val ex = assertThrows(IllegalArgumentException::class.java) {
            registry.write("setup.users.root.ssh.public_key", "fake key")
        }
        assertTrue(ex.message!!.contains("not user-writable"))
    }

    @Test
    fun `first read of the ssh private key generates and persists material`(@TempDir tmp: Path) {
        val storage = newStorage(tmp)
        val users = SetupUsersVariable(storage).also { it.write("[\"root\"]") }
        val family = SetupUsersFamily(storage, users, SshKeyGenerator())

        assertNull(storage.read("setup.users.root.ssh.private_key"))
        assertNull(storage.read("setup.users.root.ssh.public_key"))

        val generated = family.variable("setup.users.root.ssh.private_key")!!.read()
        assertNotNull(generated)
        assertTrue(generated!!.startsWith("-----BEGIN OPENSSH PRIVATE KEY-----"))

        // Both halves persisted on the same call.
        assertEquals(generated, storage.read("setup.users.root.ssh.private_key"))
        val publicStored = storage.read("setup.users.root.ssh.public_key")
        assertNotNull(publicStored)
        assertTrue(publicStored!!.startsWith("ssh-ed25519 "))
        assertTrue(publicStored.endsWith(" kube-of-pie:root"))
    }

    @Test
    fun `subsequent reads return the same persisted material`(@TempDir tmp: Path) {
        val storage = newStorage(tmp)
        val users = SetupUsersVariable(storage).also { it.write("[\"root\"]") }
        val family = SetupUsersFamily(storage, users, SshKeyGenerator())

        val firstPrivate = family.variable("setup.users.root.ssh.private_key")!!.read()
        val secondPrivate = family.variable("setup.users.root.ssh.private_key")!!.read()
        assertEquals(firstPrivate, secondPrivate)

        val firstPublic = family.variable("setup.users.root.ssh.public_key")!!.read()
        val secondPublic = family.variable("setup.users.root.ssh.public_key")!!.read()
        assertEquals(firstPublic, secondPublic)
    }

    @Test
    fun `reading public key first generates both halves and the private read matches`(@TempDir tmp: Path) {
        val storage = newStorage(tmp)
        val users = SetupUsersVariable(storage).also { it.write("[\"root\"]") }
        val family = SetupUsersFamily(storage, users, SshKeyGenerator())

        val publicFirst = family.variable("setup.users.root.ssh.public_key")!!.read()
        assertNotNull(publicFirst)
        assertTrue(publicFirst!!.startsWith("ssh-ed25519 "))

        val privateAfter = family.variable("setup.users.root.ssh.private_key")!!.read()
        assertEquals(storage.read("setup.users.root.ssh.private_key"), privateAfter)
        assertEquals(publicFirst, storage.read("setup.users.root.ssh.public_key"))
    }

    private fun newStorage(tmp: Path): VariableStorage = VariableStorage(openDatabase(tmp))

    private fun openDatabase(tmp: Path): ConfigDatabase {
        val database = ConfigDatabase()
        database.open(tmp.resolve("kop.db"), OpenMode.READ_WRITE)
        return database
    }
}
