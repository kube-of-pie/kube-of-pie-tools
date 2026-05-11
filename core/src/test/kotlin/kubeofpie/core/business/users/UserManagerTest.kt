package kubeofpie.core.business.users

import io.micronaut.context.ApplicationContext
import java.nio.file.Path
import kubeofpie.core.storage.ConfigDatabase
import kubeofpie.core.storage.OpenMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class UserManagerTest {

    @Test
    fun `add stores a user with all fields null`(@TempDir tmp: Path) = withManager(tmp) { manager ->
        val user = manager.add("root")
        assertEquals(User(name = "root"), user)
        assertEquals(listOf("root"), manager.list())
        assertEquals(user, manager.get("root"))
    }

    @Test
    fun `add validates the POSIX user name shape`(@TempDir tmp: Path) = withManager(tmp) { manager ->
        for (bad in listOf("Root", "1user", "", "user.name")) {
            val ex = assertThrows(IllegalArgumentException::class.java) { manager.add(bad) }
            assertTrue(ex.message!!.contains("invalid user name"), ex.message)
        }
    }

    @Test
    fun `add rejects duplicates`(@TempDir tmp: Path) = withManager(tmp) { manager ->
        manager.add("root")
        val ex = assertThrows(IllegalArgumentException::class.java) { manager.add("root") }
        assertTrue(ex.message!!.contains("already exists"), ex.message)
    }

    @Test
    fun `list returns identifiers in insertion order`(@TempDir tmp: Path) = withManager(tmp) { manager ->
        manager.add("root")
        manager.add("kubeofpie")
        manager.add("alice")

        assertEquals(listOf("root", "kubeofpie", "alice"), manager.list())
    }

    @Test
    fun `remove drops the user and rejects unknown names`(@TempDir tmp: Path) = withManager(tmp) { manager ->
        manager.add("root")
        manager.add("kubeofpie")
        manager.remove("root")
        assertEquals(listOf("kubeofpie"), manager.list())

        val ex = assertThrows(IllegalArgumentException::class.java) { manager.remove("ghost") }
        assertTrue(ex.message!!.contains("not registered"), ex.message)
    }

    @Test
    fun `setPassword updates only its column`(@TempDir tmp: Path) = withManager(tmp) { manager ->
        manager.add("root")
        manager.setPassword("root", "secret")

        val user = manager.get("root")!!
        assertEquals("secret", user.password)
        assertNull(user.sshEnabled)
        assertNull(user.sshPrivateKey)
        assertNull(user.sshPublicKey)
    }

    @Test
    fun `setSshEnabled false leaves the keys untouched`(@TempDir tmp: Path) = withManager(tmp) { manager ->
        manager.add("root")
        manager.setSshEnabled("root", false)

        val user = manager.get("root")!!
        assertEquals(false, user.sshEnabled)
        assertNull(user.sshPrivateKey)
        assertNull(user.sshPublicKey)
    }

    @Test
    fun `setters reject missing users`(@TempDir tmp: Path) = withManager(tmp) { manager ->
        val ex = assertThrows(IllegalArgumentException::class.java) {
            manager.setPassword("ghost", "anything")
        }
        assertTrue(ex.message!!.contains("not registered"), ex.message)
    }

    @Test
    fun `ssh key getters return null when ssh_enabled is unset or false`(@TempDir tmp: Path) =
        withManager(tmp) { manager ->
            manager.add("root")
            assertNull(manager.getSshPrivateKey("root"))
            assertNull(manager.getSshPublicKey("root"))

            manager.setSshEnabled("root", false)
            assertNull(manager.getSshPrivateKey("root"))
            assertNull(manager.getSshPublicKey("root"))
        }

    @Test
    fun `setting ssh enabled to true generates and persists both halves`(@TempDir tmp: Path) =
        withManager(tmp) { manager ->
            manager.add("root")
            manager.setSshEnabled("root", true)

            val stored = manager.get("root")!!
            assertNotNull(stored.sshPrivateKey)
            assertNotNull(stored.sshPublicKey)
            assertTrue(stored.sshPrivateKey!!.startsWith("-----BEGIN OPENSSH PRIVATE KEY-----"), stored.sshPrivateKey)
            assertTrue(stored.sshPublicKey!!.startsWith("ssh-ed25519 "), stored.sshPublicKey)
            assertTrue(stored.sshPublicKey.endsWith(" kube-of-pie:root"), stored.sshPublicKey)

            assertEquals(stored.sshPrivateKey, manager.getSshPrivateKey("root"))
            assertEquals(stored.sshPublicKey, manager.getSshPublicKey("root"))
        }

    @Test
    fun `subsequent reads return the same persisted material`(@TempDir tmp: Path) =
        withManager(tmp) { manager ->
            manager.add("root")
            manager.setSshEnabled("root", true)

            val firstPriv = manager.getSshPrivateKey("root")
            val secondPriv = manager.getSshPrivateKey("root")
            assertEquals(firstPriv, secondPriv)

            val firstPub = manager.getSshPublicKey("root")
            val secondPub = manager.getSshPublicKey("root")
            assertEquals(firstPub, secondPub)
        }

    @Test
    fun `re-setting ssh enabled to true keeps the existing key pair`(@TempDir tmp: Path) =
        withManager(tmp) { manager ->
            manager.add("root")
            manager.setSshEnabled("root", true)
            val priv = manager.getSshPrivateKey("root")
            val pub = manager.getSshPublicKey("root")

            manager.setSshEnabled("root", true)

            assertEquals(priv, manager.getSshPrivateKey("root"))
            assertEquals(pub, manager.getSshPublicKey("root"))
        }

    @Test
    fun `generated keys remain readable after disabling ssh`(@TempDir tmp: Path) =
        withManager(tmp) { manager ->
            manager.add("root")
            manager.setSshEnabled("root", true)
            val priv = manager.getSshPrivateKey("root")
            val pub = manager.getSshPublicKey("root")
            assertNotNull(priv)
            assertNotNull(pub)

            manager.setSshEnabled("root", false)

            assertEquals(priv, manager.getSshPrivateKey("root"))
            assertEquals(pub, manager.getSshPublicKey("root"))
        }

    @Test
    fun `get returns null for unknown users`(@TempDir tmp: Path) = withManager(tmp) { manager ->
        assertNull(manager.get("ghost"))
    }

    @Test
    fun `getSshPrivateKey returns null for unknown users`(@TempDir tmp: Path) = withManager(tmp) { manager ->
        assertNull(manager.getSshPrivateKey("ghost"))
    }

    private fun withManager(tmp: Path, block: (UserManager) -> Unit) {
        ApplicationContext.run().use { ctx ->
            ctx.getBean(ConfigDatabase::class.java)
                .open(tmp.resolve("kop.db"), OpenMode.READ_WRITE)
            block(ctx.getBean(UserManager::class.java))
        }
    }
}
