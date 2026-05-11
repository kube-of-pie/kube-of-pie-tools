package kubeofpie.core.registry.users

import io.micronaut.context.ApplicationContext
import java.nio.file.Path
import kubeofpie.core.registry.VariableRegistry
import kubeofpie.core.storage.ConfigDatabase
import kubeofpie.core.storage.OpenMode
import kubeofpie.core.business.users.UserManager
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
    fun `users metadata is non-writable listable`(@TempDir tmp: Path) = withUsers(tmp) { users, _ ->
        assertEquals("users", users.key)
        assertFalse(users.writable)
        assertEquals(false, users.sensitive)
        assertNull(users.allowedValues())
        assertNull(users.read())
        assertEquals(emptyList<String>(), users.identifiers())
    }

    @Test
    fun `add delegates to the manager and identifiers reflects state`(@TempDir tmp: Path) =
        withUsers(tmp) { users, _ ->
            users.add("root")
            users.add("kubeofpie")
            assertEquals(listOf("root", "kubeofpie"), users.identifiers())
        }

    @Test
    fun `add rejects names that violate the POSIX shape`(@TempDir tmp: Path) =
        withUsers(tmp) { users, _ ->
            val ex = assertThrows(IllegalArgumentException::class.java) { users.add("Root") }
            assertTrue(ex.message!!.contains("invalid user name"), ex.message)
        }

    @Test
    fun `add rejects null id`(@TempDir tmp: Path) = withUsers(tmp) { users, _ ->
        val ex = assertThrows(IllegalArgumentException::class.java) { users.add(null) }
        assertTrue(ex.message!!.contains("requires a user name"), ex.message)
    }

    @Test
    fun `add rejects duplicates`(@TempDir tmp: Path) = withUsers(tmp) { users, _ ->
        users.add("root")
        val ex = assertThrows(IllegalArgumentException::class.java) { users.add("root") }
        assertTrue(ex.message!!.contains("already exists"), ex.message)
    }

    @Test
    fun `remove drops a known name and rejects unknown ones`(@TempDir tmp: Path) =
        withUsers(tmp) { users, _ ->
            users.add("root")
            users.add("kubeofpie")

            users.remove("root")
            assertEquals(listOf("kubeofpie"), users.identifiers())

            val ex = assertThrows(IllegalArgumentException::class.java) { users.remove("nobody") }
            assertTrue(ex.message!!.contains("not registered"), ex.message)
        }

    @Test
    fun `family enumerates four keys per user, in registration order`(@TempDir tmp: Path) =
        withFamily(tmp) { family, manager ->
            manager.add("root")
            manager.add("kubeofpie")

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
    fun `family resolves password, ssh enabled, and ssh key variables for a known user`(@TempDir tmp: Path) =
        withFamily(tmp) { family, manager ->
            manager.add("root")

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
    fun `family returns null for an unknown user`(@TempDir tmp: Path) = withFamily(tmp) { family, manager ->
        manager.add("root")

        assertNull(family.variable("users.nobody.password"))
    }

    @Test
    fun `family returns null for unrelated keys`(@TempDir tmp: Path) = withFamily(tmp) { family, _ ->
        assertNull(family.variable("setup.keymap"))
        assertNull(family.variable("users.root.unknown"))
    }

    @Test
    fun `registry rejects writes to the listable users head`(@TempDir tmp: Path) =
        withFamily(tmp) { family, manager ->
            val users = UsersVariable(manager)
            val registry = VariableRegistry(listOf(users), listOf(family))

            val ex = assertThrows(IllegalArgumentException::class.java) {
                registry.write("users", "[\"root\"]")
            }
            assertTrue(ex.message!!.contains("not user-writable"), ex.message)
        }

    @Test
    fun `registry routes writes to the per-user password variable`(@TempDir tmp: Path) =
        withFamily(tmp) { family, manager ->
            val users = UsersVariable(manager)
            manager.add("root")
            val registry = VariableRegistry(listOf(users), listOf(family))

            registry.write("users.root.password", "kubeofpie")

            assertEquals("kubeofpie", registry.read("users.root.password")?.read())
        }

    @Test
    fun `registry rejects writes to the read-only ssh private key`(@TempDir tmp: Path) =
        withFamily(tmp) { family, manager ->
            val users = UsersVariable(manager)
            manager.add("root")
            val registry = VariableRegistry(listOf(users), listOf(family))

            val ex = assertThrows(IllegalArgumentException::class.java) {
                registry.write("users.root.ssh.private_key", "fake key")
            }
            assertTrue(ex.message!!.contains("not user-writable"), ex.message)
        }

    @Test
    fun `registry rejects writes to the read-only ssh public key`(@TempDir tmp: Path) =
        withFamily(tmp) { family, manager ->
            val users = UsersVariable(manager)
            manager.add("root")
            val registry = VariableRegistry(listOf(users), listOf(family))

            val ex = assertThrows(IllegalArgumentException::class.java) {
                registry.write("users.root.ssh.public_key", "fake key")
            }
            assertTrue(ex.message!!.contains("not user-writable"), ex.message)
        }

    @Test
    fun `registry accepts ssh enabled writes and rejects values outside the allowed list`(@TempDir tmp: Path) =
        withFamily(tmp) { family, manager ->
            val users = UsersVariable(manager)
            manager.add("root")
            val registry = VariableRegistry(listOf(users), listOf(family))

            registry.write("users.root.ssh.enabled", "true")
            assertEquals("true", registry.read("users.root.ssh.enabled")?.read())

            registry.write("users.root.ssh.enabled", "false")
            assertEquals("false", registry.read("users.root.ssh.enabled")?.read())

            val ex = assertThrows(IllegalArgumentException::class.java) {
                registry.write("users.root.ssh.enabled", "maybe")
            }
            assertTrue(ex.message!!.contains("not allowed"), ex.message)
        }

    @Test
    fun `ssh keys are not generated while ssh enabled is unset`(@TempDir tmp: Path) =
        withFamily(tmp) { family, manager ->
            manager.add("root")

            assertNull(family.variable("users.root.ssh.private_key")!!.read())
            assertNull(family.variable("users.root.ssh.public_key")!!.read())
            assertNull(manager.get("root")?.sshPrivateKey)
            assertNull(manager.get("root")?.sshPublicKey)
        }

    @Test
    fun `ssh keys are not generated while ssh enabled is false`(@TempDir tmp: Path) =
        withFamily(tmp) { family, manager ->
            manager.add("root")
            family.variable("users.root.ssh.enabled")!!.write("false")

            assertNull(family.variable("users.root.ssh.private_key")!!.read())
            assertNull(family.variable("users.root.ssh.public_key")!!.read())
            assertNull(manager.get("root")?.sshPrivateKey)
            assertNull(manager.get("root")?.sshPublicKey)
        }

    @Test
    fun `setting ssh enabled to true generates and persists both halves`(@TempDir tmp: Path) =
        withFamily(tmp) { family, manager ->
            manager.add("root")
            family.variable("users.root.ssh.enabled")!!.write("true")

            val stored = manager.get("root")!!
            assertNotNull(stored.sshPrivateKey)
            assertTrue(
                stored.sshPrivateKey!!.startsWith("-----BEGIN OPENSSH PRIVATE KEY-----"),
                stored.sshPrivateKey,
            )
            assertNotNull(stored.sshPublicKey)
            assertTrue(stored.sshPublicKey!!.startsWith("ssh-ed25519 "), stored.sshPublicKey)
            assertTrue(stored.sshPublicKey.endsWith(" kube-of-pie:root"), stored.sshPublicKey)

            assertEquals(stored.sshPrivateKey, family.variable("users.root.ssh.private_key")!!.read())
            assertEquals(stored.sshPublicKey, family.variable("users.root.ssh.public_key")!!.read())
        }

    @Test
    fun `subsequent reads return the same persisted material`(@TempDir tmp: Path) =
        withFamily(tmp) { family, manager ->
            manager.add("root")
            family.variable("users.root.ssh.enabled")!!.write("true")

            val firstPrivate = family.variable("users.root.ssh.private_key")!!.read()
            val secondPrivate = family.variable("users.root.ssh.private_key")!!.read()
            assertEquals(firstPrivate, secondPrivate)

            val firstPublic = family.variable("users.root.ssh.public_key")!!.read()
            val secondPublic = family.variable("users.root.ssh.public_key")!!.read()
            assertEquals(firstPublic, secondPublic)
        }

    @Test
    fun `keys generated while enabled remain readable after disabling`(@TempDir tmp: Path) =
        withFamily(tmp) { family, manager ->
            manager.add("root")
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

    private fun withUsers(tmp: Path, block: (UsersVariable, UserManager) -> Unit) {
        ApplicationContext.run().use { ctx ->
            ctx.getBean(ConfigDatabase::class.java)
                .open(tmp.resolve("kop.db"), OpenMode.READ_WRITE)
            val manager = ctx.getBean(UserManager::class.java)
            block(UsersVariable(manager), manager)
        }
    }

    private fun withFamily(tmp: Path, block: (UsersFamily, UserManager) -> Unit) {
        ApplicationContext.run().use { ctx ->
            ctx.getBean(ConfigDatabase::class.java)
                .open(tmp.resolve("kop.db"), OpenMode.READ_WRITE)
            val manager = ctx.getBean(UserManager::class.java)
            block(UsersFamily(manager), manager)
        }
    }
}
