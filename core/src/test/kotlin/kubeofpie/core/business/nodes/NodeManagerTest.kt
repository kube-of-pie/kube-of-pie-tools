package kubeofpie.core.business.nodes

import io.micronaut.context.ApplicationContext
import java.nio.file.Path
import kubeofpie.core.registry.VersionAlpineVariable
import kubeofpie.core.storage.ConfigDatabase
import kubeofpie.core.storage.OpenMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class NodeManagerTest {

    @Test
    fun `add stores a node with all fields null`(@TempDir tmp: Path) = withManager(tmp) { manager ->
        val node = manager.add("master")
        assertEquals(Node(id = "master"), node)
        assertEquals(listOf("master"), manager.list())
        assertEquals(node, manager.get("master"))
    }

    @Test
    fun `add validates the DNS label shape`(@TempDir tmp: Path) = withManager(tmp) { manager ->
        for (bad in listOf("Master", "node_1", "-worker", "worker-", "", "worker.1")) {
            val ex = assertThrows(IllegalArgumentException::class.java) { manager.add(bad) }
            assertTrue(ex.message!!.contains("invalid node id"), ex.message)
        }
    }

    @Test
    fun `add rejects duplicates`(@TempDir tmp: Path) = withManager(tmp) { manager ->
        manager.add("master")
        val ex = assertThrows(IllegalArgumentException::class.java) { manager.add("master") }
        assertTrue(ex.message!!.contains("already exists"), ex.message)
    }

    @Test
    fun `list returns identifiers in insertion order`(@TempDir tmp: Path) = withManager(tmp) { manager ->
        manager.add("worker-2")
        manager.add("master")
        manager.add("worker-1")

        assertEquals(listOf("worker-2", "master", "worker-1"), manager.list())
    }

    @Test
    fun `remove drops the node and rejects unknown ids`(@TempDir tmp: Path) = withManager(tmp) { manager ->
        manager.add("master")
        manager.add("worker-1")
        manager.remove("master")
        assertEquals(listOf("worker-1"), manager.list())

        val ex = assertThrows(IllegalArgumentException::class.java) { manager.remove("ghost") }
        assertTrue(ex.message!!.contains("not registered"), ex.message)
    }

    @Test
    fun `per-field setters update only their column`(@TempDir tmp: Path) = withManager(tmp) { manager ->
        manager.add("master")
        manager.setHostname("master", "kop-master")
        manager.setDns("master", "8.8.8.8")
        manager.setWifiEnabled("master", true)
        manager.setWifiSsid("master", "kop-wifi")
        manager.setWifiPassphrase("master", "secret")

        val node = manager.get("master")!!
        assertEquals("kop-master", node.hostname)
        assertEquals("8.8.8.8", node.dns)
        assertEquals(true, node.wifiEnabled)
        assertEquals("kop-wifi", node.wifiSsid)
        assertEquals("secret", node.wifiPassphrase)
        assertNull(node.model)
    }

    @Test
    fun `setters reject missing nodes`(@TempDir tmp: Path) = withManager(tmp) { manager ->
        val ex = assertThrows(IllegalArgumentException::class.java) {
            manager.setHostname("ghost", "anything")
        }
        assertTrue(ex.message!!.contains("not registered"), ex.message)
    }

    @Test
    fun `setModel accepts catalogue ids and rejects others`(@TempDir tmp: Path) = withManager(tmp) { manager ->
        manager.add("master")
        manager.setModel("master", "pi5")
        assertEquals("pi5", manager.get("master")?.model)

        val ex = assertThrows(IllegalArgumentException::class.java) {
            manager.setModel("master", "pi-not-a-thing")
        }
        assertTrue(ex.message!!.contains("not in the supported set"), ex.message)
    }

    @Test
    fun `setModel accepts null to clear`(@TempDir tmp: Path) = withManager(tmp) { manager ->
        manager.add("master")
        manager.setModel("master", "pi5")
        manager.setModel("master", null)
        assertNull(manager.get("master")?.model)
    }

    @Test
    fun `allowedModels mirrors the catalogue`(@TempDir tmp: Path) = withManager(tmp) { manager ->
        assertEquals(listOf("pi4", "pi5"), manager.allowedModels())
    }

    @Test
    fun `kernelArgs returns null when version_alpine is unset`(@TempDir tmp: Path) = withManager(tmp) { manager ->
        manager.add("master")
        assertNull(manager.kernelArgs("master"))
    }

    @Test
    fun `kernelArgs joins the alpine catalogue args with spaces when version is set`(@TempDir tmp: Path) =
        withManager(tmp) { manager, ctx ->
            manager.add("master")
            ctx.getBean(VersionAlpineVariable::class.java).write("3.21")

            assertEquals("cgroup_memory=1 cgroup_enable=memory", manager.kernelArgs("master"))
        }

    @Test
    fun `kernelArgs returns null for unknown nodes`(@TempDir tmp: Path) = withManager(tmp) { manager ->
        assertNull(manager.kernelArgs("ghost"))
    }

    @Test
    fun `get returns null for unknown nodes`(@TempDir tmp: Path) = withManager(tmp) { manager ->
        assertNull(manager.get("ghost"))
    }

    private fun withManager(tmp: Path, block: (NodeManager) -> Unit) {
        withManager(tmp) { manager, _ -> block(manager) }
    }

    private fun withManager(tmp: Path, block: (NodeManager, ApplicationContext) -> Unit) {
        ApplicationContext.run().use { ctx ->
            ctx.getBean(ConfigDatabase::class.java)
                .open(tmp.resolve("kop.db"), OpenMode.READ_WRITE)
            val manager = ctx.getBean(NodeManager::class.java)
            block(manager, ctx)
        }
    }
}
