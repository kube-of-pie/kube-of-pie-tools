package kubeofpie.core.registry.nodes

import java.nio.file.Path
import kubeofpie.core.registry.VariableRegistry
import kubeofpie.core.registry.VariableStorage
import kubeofpie.core.registry.cluster.ClusterNodesCountVariable
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
    fun `family is empty until cluster_nodes_count is set`(@TempDir tmp: Path) {
        val storage = newStorage(tmp)
        val count = ClusterNodesCountVariable(storage)
        val family = NodesNetworkFamily(storage, count)

        assertEquals(emptyList<String>(), family.keys())
        assertNull(family.variable("nodes.0.network.hostname"))
    }

    @Test
    fun `family enumerates five keys per node, one entry per index up to count`(@TempDir tmp: Path) {
        val storage = newStorage(tmp)
        val count = ClusterNodesCountVariable(storage).also { it.write("2") }
        val family = NodesNetworkFamily(storage, count)

        assertEquals(
            listOf(
                "nodes.0.network.hostname",
                "nodes.0.network.dns",
                "nodes.0.network.wifi.enabled",
                "nodes.0.network.wifi.ssid",
                "nodes.0.network.wifi.passphrase",
                "nodes.1.network.hostname",
                "nodes.1.network.dns",
                "nodes.1.network.wifi.enabled",
                "nodes.1.network.wifi.ssid",
                "nodes.1.network.wifi.passphrase",
            ),
            family.keys(),
        )
    }

    @Test
    fun `family resolves each per-node variable with the right metadata`(@TempDir tmp: Path) {
        val storage = newStorage(tmp)
        val count = ClusterNodesCountVariable(storage).also { it.write("1") }
        val family = NodesNetworkFamily(storage, count)

        val hostname = family.variable("nodes.0.network.hostname")!!
        assertTrue(hostname.writable)
        assertEquals(false, hostname.sensitive)
        assertNull(hostname.allowedValues())

        val wifiEnabled = family.variable("nodes.0.network.wifi.enabled")!!
        assertEquals(listOf("true", "false"), wifiEnabled.allowedValues())

        val passphrase = family.variable("nodes.0.network.wifi.passphrase")!!
        assertTrue(passphrase.sensitive)
    }

    @Test
    fun `family rejects out-of-range indices`(@TempDir tmp: Path) {
        val storage = newStorage(tmp)
        val count = ClusterNodesCountVariable(storage).also { it.write("1") }
        val family = NodesNetworkFamily(storage, count)

        assertNull(family.variable("nodes.1.network.hostname"))
        assertNull(family.variable("nodes.5.network.dns"))
    }

    @Test
    fun `family ignores unrelated keys`(@TempDir tmp: Path) {
        val storage = newStorage(tmp)
        val count = ClusterNodesCountVariable(storage).also { it.write("1") }
        val family = NodesNetworkFamily(storage, count)

        assertNull(family.variable("setup.keymap"))
        assertNull(family.variable("nodes.0.network.bogus"))
        assertNull(family.variable("nodes.x.network.hostname"))
    }

    @Test
    fun `registry round-trips per-node values isolated by index`(@TempDir tmp: Path) {
        val storage = newStorage(tmp)
        val count = ClusterNodesCountVariable(storage).also { it.write("2") }
        val family = NodesNetworkFamily(storage, count)
        val registry = VariableRegistry(listOf(count), listOf(family))

        registry.write("nodes.0.network.hostname", "kop-0")
        registry.write("nodes.1.network.hostname", "kop-1")

        assertEquals("kop-0", registry.read("nodes.0.network.hostname")?.read())
        assertEquals("kop-1", registry.read("nodes.1.network.hostname")?.read())
    }

    @Test
    fun `registry rejects wifi enabled values that are not boolean strings`(@TempDir tmp: Path) {
        val storage = newStorage(tmp)
        val count = ClusterNodesCountVariable(storage).also { it.write("1") }
        val family = NodesNetworkFamily(storage, count)
        val registry = VariableRegistry(listOf(count), listOf(family))

        val ex = assertThrows(IllegalArgumentException::class.java) {
            registry.write("nodes.0.network.wifi.enabled", "yes")
        }
        assertTrue(ex.message!!.contains("not allowed"))
    }

    private fun newStorage(tmp: Path): VariableStorage = VariableStorage(openDatabase(tmp))

    private fun openDatabase(tmp: Path): ConfigDatabase {
        val database = ConfigDatabase()
        database.open(tmp.resolve("kop.db"), OpenMode.READ_WRITE)
        return database
    }
}
