package kubeofpie.core.registry.nodes

import io.micronaut.context.ApplicationContext
import java.nio.file.Path
import kubeofpie.core.business.nodes.NodeManager
import kubeofpie.core.registry.VariableRegistry
import kubeofpie.core.storage.ConfigDatabase
import kubeofpie.core.storage.OpenMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class NodesNetworkFamilyTest {

    @Test
    fun `family is empty until at least one node is added`(@TempDir tmp: Path) = withFamily(tmp) { family, _ ->
        assertEquals(emptyList<String>(), family.keys())
        assertNull(family.variable("nodes.master.network.hostname"))
    }

    @Test
    fun `family enumerates five keys per node, in registration order`(@TempDir tmp: Path) =
        withFamily(tmp) { family, manager ->
            manager.add("master")
            manager.add("worker-1")

            assertEquals(
                listOf(
                    "nodes.master.network.hostname",
                    "nodes.master.network.dns",
                    "nodes.master.network.wifi.enabled",
                    "nodes.master.network.wifi.ssid",
                    "nodes.master.network.wifi.passphrase",
                    "nodes.worker-1.network.hostname",
                    "nodes.worker-1.network.dns",
                    "nodes.worker-1.network.wifi.enabled",
                    "nodes.worker-1.network.wifi.ssid",
                    "nodes.worker-1.network.wifi.passphrase",
                ),
                family.keys(),
            )
        }

    @Test
    fun `family resolves each per-node variable with the right metadata`(@TempDir tmp: Path) =
        withFamily(tmp) { family, manager ->
            manager.add("master")

            val hostname = family.variable("nodes.master.network.hostname")!!
            assertTrue(hostname.writable)
            assertEquals(false, hostname.sensitive)
            assertNull(hostname.allowedValues())

            val wifiEnabled = family.variable("nodes.master.network.wifi.enabled")!!
            assertEquals(listOf("true", "false"), wifiEnabled.allowedValues())

            val passphrase = family.variable("nodes.master.network.wifi.passphrase")!!
            assertTrue(passphrase.sensitive)
        }

    @Test
    fun `family rejects unknown node ids`(@TempDir tmp: Path) = withFamily(tmp) { family, manager ->
        manager.add("master")

        assertNull(family.variable("nodes.worker-1.network.hostname"))
        assertNull(family.variable("nodes.ghost.network.dns"))
    }

    @Test
    fun `family ignores unrelated keys`(@TempDir tmp: Path) = withFamily(tmp) { family, manager ->
        manager.add("master")

        assertNull(family.variable("setup.keymap"))
        assertNull(family.variable("nodes.master.network.bogus"))
        assertNull(family.variable("nodes.Master.network.hostname"))
        assertNull(family.variable("nodes.node_1.network.hostname"))
    }

    @Test
    fun `registry round-trips per-node values isolated by id`(@TempDir tmp: Path) =
        withFamily(tmp) { family, manager ->
            val nodesVariable = NodesVariable(manager)
            manager.add("master")
            manager.add("worker-1")
            val registry = VariableRegistry(listOf(nodesVariable), listOf(family))

            registry.write("nodes.master.network.hostname", "kop-master")
            registry.write("nodes.worker-1.network.hostname", "kop-worker-1")

            assertEquals("kop-master", registry.read("nodes.master.network.hostname")?.read())
            assertEquals("kop-worker-1", registry.read("nodes.worker-1.network.hostname")?.read())
        }

    @Test
    fun `registry rejects wifi enabled values that are not boolean strings`(@TempDir tmp: Path) =
        withFamily(tmp) { family, manager ->
            val nodesVariable = NodesVariable(manager)
            manager.add("master")
            val registry = VariableRegistry(listOf(nodesVariable), listOf(family))

            val ex = assertThrows(IllegalArgumentException::class.java) {
                registry.write("nodes.master.network.wifi.enabled", "yes")
            }
            assertTrue(ex.message!!.contains("not allowed"), ex.message)
        }

    private fun withFamily(tmp: Path, block: (NodesNetworkFamily, NodeManager) -> Unit) {
        ApplicationContext.run().use { ctx ->
            ctx.getBean(ConfigDatabase::class.java)
                .open(tmp.resolve("kop.db"), OpenMode.READ_WRITE)
            val manager = ctx.getBean(NodeManager::class.java)
            block(NodesNetworkFamily(manager), manager)
        }
    }
}
