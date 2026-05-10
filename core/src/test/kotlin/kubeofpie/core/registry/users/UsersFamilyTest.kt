package kubeofpie.core.registry.users

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

class UsersFamilyTest {

    @Test
    fun `users metadata is non-writable listable`(@TempDir tmp: Path) {
        val users = UsersVariable(newStorage(tmp))

        assertEquals("users", users.key)
        assertFalse(users.writable)
        assertEquals(false, users.sensitive)
        assertNull(users.allowedValues())
        assertEquals(emptyList<String>(), users.identifiers())
    }

    @Test
    fun `add appends names and identifiers reflects them in registration order`(@TempDir tmp: Path) {
        val users = UsersVariable(newStorage(tmp))

        users.add("root")
        users.add("kubeofpie")

        assertEquals(listOf("root", "kubeofpie"), users.identifiers())
    }

    @Test
    fun `add rejects names that do not match the POSIX shape`(@TempDir tmp: Path) {
        val users = UsersVariable(newStorage(tmp))

        val ex = assertThrows(IllegalArgumentException::class.java) { users.add("Root") }
        assertTrue(ex.message!!.contains("invalid user name"))
    }

    @Test
    fun `add rejects null id`(@TempDir tmp: Path) {
        val users = UsersVariable(newStorage(tmp))

        val ex = assertThrows(IllegalArgumentException::class.java) { users.add(null) }
        assertTrue(ex.message!!.contains("requires a user name"))
    }

    @Test
    fun `add rejects duplicates`(@TempDir tmp: Path) {
        val users = UsersVariable(newStorage(tmp)).also { it.add("root") }

        val ex = assertThrows(IllegalArgumentException::class.java) { users.add("root") }
        assertTrue(ex.message!!.contains("already exists"))
    }

    @Test
    fun `remove drops a known name and rejects unknown ones`(@TempDir tmp: Path) {
        val users = UsersVariable(newStorage(tmp)).also {
            it.add("root")
            it.add("kubeofpie")
        }

        users.remove("root")
        assertEquals(listOf("kubeofpie"), users.identifiers())

        val ex = assertThrows(IllegalArgumentException::class.java) { users.remove("nobody") }
        assertTrue(ex.message!!.contains("not in users"))
    }

    @Test
    fun `family enumerates four keys per user, in registration order`(@TempDir tmp: Path) {
        val storage = newStorage(tmp)
        val users = UsersVariable(storage).also { it.add("root"); it.add("kubeofpie") }
        val family = UsersFamily(storage, users, SshKeyGenerator())

        assertEquals(
            listOf(
                "users.root.password",
                "users.root.ssh.enabled",
                "users.root.ssh.private_key",
                "users.root.ssh.public_key",
                "users.kubeofpie.password",
                "users.kubeofpie.ssh.enabled",
                "users.kubeofpie.ssh.private_key",
                "users.kubeofpie.ssh.public_key",
            ),
            family.keys(),
        )
    }

    @Test
    fun `family resolves password, ssh enabled, and ssh key variables for a known user`(@TempDir tmp: Path) {
        val storage = newStorage(tmp)
        val users = UsersVariable(storage).also { it.add("root") }
        val family = UsersFamily(storage, users, SshKeyGenerator())

        val password = family.variable("users.root.password")
        assertNotNull(password)
        assertTrue(password!!.writable)
        assertTrue(password.sensitive)

        val sshEnabled = family.variable("users.root.ssh.enabled")
        assertNotNull(sshEnabled)
        assertTrue(sshEnabled!!.writable)
        assertFalse(sshEnabled.sensitive)
        assertEquals(listOf("true", "false"), sshEnabled.allowedValues())
        assertNull(sshEnabled.read())

        val sshPrivate = family.variable("users.root.ssh.private_key")
        assertNotNull(sshPrivate)
        assertFalse(sshPrivate!!.writable)
        assertTrue(sshPrivate.sensitive)

        val sshPublic = family.variable("users.root.ssh.public_key")
        assertNotNull(sshPublic)
        assertFalse(sshPublic!!.writable)
        assertFalse(sshPublic.sensitive)
    }

    @Test
    fun `family returns null for an unknown user`(@TempDir tmp: Path) {
        val storage = newStorage(tmp)
        val users = UsersVariable(storage).also { it.add("root") }
        val family = UsersFamily(storage, users, SshKeyGenerator())

        assertNull(family.variable("users.nobody.password"))
    }

    @Test
    fun `family returns null for unrelated keys`(@TempDir tmp: Path) {
        val family = UsersFamily(newStorage(tmp), UsersVariable(newStorage(tmp)), SshKeyGenerator())

        assertNull(family.variable("setup.keymap"))
        assertNull(family.variable("users.root.unknown"))
    }

    @Test
    fun `registry rejects writes to the listable users head`(@TempDir tmp: Path) {
        val storage = newStorage(tmp)
        val users = UsersVariable(storage)
        val family = UsersFamily(storage, users, SshKeyGenerator())
        val registry = VariableRegistry(listOf(users), listOf(family))

        val ex = assertThrows(IllegalArgumentException::class.java) {
            registry.write("users", "[\"root\"]")
        }
        assertTrue(ex.message!!.contains("not user-writable"))
    }

    @Test
    fun `registry routes writes to the per-user password variable`(@TempDir tmp: Path) {
        val storage = newStorage(tmp)
        val users = UsersVariable(storage).also { it.add("root") }
        val family = UsersFamily(storage, users, SshKeyGenerator())
        val registry = VariableRegistry(listOf(users), listOf(family))

        registry.write("users.root.password", "kubeofpie")

        assertEquals("kubeofpie", registry.read("users.root.password")?.read())
    }

    @Test
    fun `registry rejects writes to the read-only ssh private key`(@TempDir tmp: Path) {
        val storage = newStorage(tmp)
        val users = UsersVariable(storage).also { it.add("root") }
        val family = UsersFamily(storage, users, SshKeyGenerator())
        val registry = VariableRegistry(listOf(users), listOf(family))

        val ex = assertThrows(IllegalArgumentException::class.java) {
            registry.write("users.root.ssh.private_key", "fake key")
        }
        assertTrue(ex.message!!.contains("not user-writable"))
    }

    @Test
    fun `registry rejects writes to the read-only ssh public key`(@TempDir tmp: Path) {
        val storage = newStorage(tmp)
        val users = UsersVariable(storage).also { it.add("root") }
        val family = UsersFamily(storage, users, SshKeyGenerator())
        val registry = VariableRegistry(listOf(users), listOf(family))

        val ex = assertThrows(IllegalArgumentException::class.java) {
            registry.write("users.root.ssh.public_key", "fake key")
        }
        assertTrue(ex.message!!.contains("not user-writable"))
    }

    @Test
    fun `registry accepts ssh enabled writes and rejects values outside the allowed list`(@TempDir tmp: Path) {
        val storage = newStorage(tmp)
        val users = UsersVariable(storage).also { it.add("root") }
        val family = UsersFamily(storage, users, SshKeyGenerator())
        val registry = VariableRegistry(listOf(users), listOf(family))

        registry.write("users.root.ssh.enabled", "true")
        assertEquals("true", registry.read("users.root.ssh.enabled")?.read())

        registry.write("users.root.ssh.enabled", "false")
        assertEquals("false", registry.read("users.root.ssh.enabled")?.read())

        val ex = assertThrows(IllegalArgumentException::class.java) {
            registry.write("users.root.ssh.enabled", "maybe")
        }
        assertTrue(ex.message!!.contains("not allowed"))
    }

    @Test
    fun `ssh keys are not generated while ssh enabled is unset`(@TempDir tmp: Path) {
        val storage = newStorage(tmp)
        val users = UsersVariable(storage).also { it.add("root") }
        val family = UsersFamily(storage, users, SshKeyGenerator())

        assertNull(family.variable("users.root.ssh.private_key")!!.read())
        assertNull(family.variable("users.root.ssh.public_key")!!.read())
        assertNull(storage.read("users.root.ssh.private_key"))
        assertNull(storage.read("users.root.ssh.public_key"))
    }

    @Test
    fun `ssh keys are not generated while ssh enabled is false`(@TempDir tmp: Path) {
        val storage = newStorage(tmp)
        val users = UsersVariable(storage).also { it.add("root") }
        val family = UsersFamily(storage, users, SshKeyGenerator())
        family.variable("users.root.ssh.enabled")!!.write("false")

        assertNull(family.variable("users.root.ssh.private_key")!!.read())
        assertNull(family.variable("users.root.ssh.public_key")!!.read())
        assertNull(storage.read("users.root.ssh.private_key"))
        assertNull(storage.read("users.root.ssh.public_key"))
    }

    @Test
    fun `setting ssh enabled to true triggers lazy generation on first read`(@TempDir tmp: Path) {
        val storage = newStorage(tmp)
        val users = UsersVariable(storage).also { it.add("root") }
        val family = UsersFamily(storage, users, SshKeyGenerator())
        family.variable("users.root.ssh.enabled")!!.write("true")

        val generated = family.variable("users.root.ssh.private_key")!!.read()
        assertNotNull(generated)
        assertTrue(generated!!.startsWith("-----BEGIN OPENSSH PRIVATE KEY-----"))

        // Both halves persisted on the same call.
        assertEquals(generated, storage.read("users.root.ssh.private_key"))
        val publicStored = storage.read("users.root.ssh.public_key")
        assertNotNull(publicStored)
        assertTrue(publicStored!!.startsWith("ssh-ed25519 "))
        assertTrue(publicStored.endsWith(" kube-of-pie:root"))
    }

    @Test
    fun `subsequent reads return the same persisted material`(@TempDir tmp: Path) {
        val storage = newStorage(tmp)
        val users = UsersVariable(storage).also { it.add("root") }
        val family = UsersFamily(storage, users, SshKeyGenerator())
        family.variable("users.root.ssh.enabled")!!.write("true")

        val firstPrivate = family.variable("users.root.ssh.private_key")!!.read()
        val secondPrivate = family.variable("users.root.ssh.private_key")!!.read()
        assertEquals(firstPrivate, secondPrivate)

        val firstPublic = family.variable("users.root.ssh.public_key")!!.read()
        val secondPublic = family.variable("users.root.ssh.public_key")!!.read()
        assertEquals(firstPublic, secondPublic)
    }

    @Test
    fun `reading public key first generates both halves and the private read matches`(@TempDir tmp: Path) {
        val storage = newStorage(tmp)
        val users = UsersVariable(storage).also { it.add("root") }
        val family = UsersFamily(storage, users, SshKeyGenerator())
        family.variable("users.root.ssh.enabled")!!.write("true")

        val publicFirst = family.variable("users.root.ssh.public_key")!!.read()
        assertNotNull(publicFirst)
        assertTrue(publicFirst!!.startsWith("ssh-ed25519 "))

        val privateAfter = family.variable("users.root.ssh.private_key")!!.read()
        assertEquals(storage.read("users.root.ssh.private_key"), privateAfter)
        assertEquals(publicFirst, storage.read("users.root.ssh.public_key"))
    }

    @Test
    fun `keys generated while enabled remain readable after disabling`(@TempDir tmp: Path) {
        val storage = newStorage(tmp)
        val users = UsersVariable(storage).also { it.add("root") }
        val family = UsersFamily(storage, users, SshKeyGenerator())
        val sshEnabled = family.variable("users.root.ssh.enabled")!!
        sshEnabled.write("true")
        val privateGenerated = family.variable("users.root.ssh.private_key")!!.read()
        val publicGenerated = family.variable("users.root.ssh.public_key")!!.read()
        assertNotNull(privateGenerated)
        assertNotNull(publicGenerated)

        sshEnabled.write("false")

        assertEquals(privateGenerated, family.variable("users.root.ssh.private_key")!!.read())
        assertEquals(publicGenerated, family.variable("users.root.ssh.public_key")!!.read())
    }

    private fun newStorage(tmp: Path): VariableStorage = VariableStorage(openDatabase(tmp))

    private fun openDatabase(tmp: Path): ConfigDatabase {
        val database = ConfigDatabase()
        database.open(tmp.resolve("kop.db"), OpenMode.READ_WRITE)
        return database
    }
}
