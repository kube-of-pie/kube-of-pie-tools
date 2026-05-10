package kubeofpie.core.registry.nodes

import jakarta.inject.Singleton
import kubeofpie.core.registry.Variable
import kubeofpie.core.registry.VariableFamily
import kubeofpie.core.registry.VariableStorage

/**
 * Per-node network configuration under `nodes.<i>.network.…`. The index range is
 * `0` to [NodesVariable]`.identifiers().size - 1`; the family is empty until at
 * least one node has been added.
 *
 * For each node, the family exposes:
 *
 *  - `nodes.<i>.network.hostname` — writable free text.
 *  - `nodes.<i>.network.dns` — writable free text (IP address).
 *  - `nodes.<i>.network.wifi.enabled` — writable boolean (`true`/`false`).
 *  - `nodes.<i>.network.wifi.ssid` — writable free text.
 *  - `nodes.<i>.network.wifi.passphrase` — writable, sensitive.
 */
@Singleton
class NodesNetworkFamily(
    private val storage: VariableStorage,
    private val nodes: NodesVariable,
) : VariableFamily {

    override fun keys(): List<String> = nodes.identifiers().flatMap { i ->
        SUFFIXES.map { "nodes.$i.network.$it" }
    }

    override fun variable(key: String): Variable? {
        val match = PATTERN.matchEntire(key) ?: return null
        val (rawIndex, suffix) = match.destructured
        if (rawIndex !in nodes.identifiers()) return null
        val index = rawIndex.toInt()
        return when (suffix) {
            "hostname" -> NodeHostnameVariable(storage, index)
            "dns" -> NodeDnsVariable(storage, index)
            "wifi.enabled" -> NodeWifiEnabledVariable(storage, index)
            "wifi.ssid" -> NodeWifiSsidVariable(storage, index)
            "wifi.passphrase" -> NodeWifiPassphraseVariable(storage, index)
            else -> null
        }
    }

    private companion object {
        private val SUFFIXES = listOf(
            "hostname",
            "dns",
            "wifi.enabled",
            "wifi.ssid",
            "wifi.passphrase",
        )
        private val PATTERN = Regex(
            "^nodes\\.(\\d+)\\.network\\.(hostname|dns|wifi\\.enabled|wifi\\.ssid|wifi\\.passphrase)$"
        )
    }
}

internal class NodeHostnameVariable(
    private val storage: VariableStorage,
    index: Int,
) : Variable {
    override val key: String = "nodes.$index.network.hostname"
    override val description: String = "Hostname assigned to node $index at first boot."
    override val writable: Boolean = true
    override val sensitive: Boolean = false
    override fun allowedValues(): List<String>? = null
    override fun read(): String? = storage.read(key)
    override fun write(value: String) = storage.write(key, value)
}

internal class NodeDnsVariable(
    private val storage: VariableStorage,
    index: Int,
) : Variable {
    override val key: String = "nodes.$index.network.dns"
    override val description: String = "DNS server applied to node $index via setup-dns."
    override val writable: Boolean = true
    override val sensitive: Boolean = false
    override fun allowedValues(): List<String>? = null
    override fun read(): String? = storage.read(key)
    override fun write(value: String) = storage.write(key, value)
}

internal class NodeWifiEnabledVariable(
    private val storage: VariableStorage,
    index: Int,
) : Variable {
    override val key: String = "nodes.$index.network.wifi.enabled"
    override val description: String = "Whether node $index connects to a Wi-Fi network."
    override val writable: Boolean = true
    override val sensitive: Boolean = false
    override fun allowedValues(): List<String> = listOf("true", "false")
    override fun read(): String? = storage.read(key)
    override fun write(value: String) = storage.write(key, value)
}

internal class NodeWifiSsidVariable(
    private val storage: VariableStorage,
    index: Int,
) : Variable {
    override val key: String = "nodes.$index.network.wifi.ssid"
    override val description: String = "SSID of the Wi-Fi network node $index connects to."
    override val writable: Boolean = true
    override val sensitive: Boolean = false
    override fun allowedValues(): List<String>? = null
    override fun read(): String? = storage.read(key)
    override fun write(value: String) = storage.write(key, value)
}

internal class NodeWifiPassphraseVariable(
    private val storage: VariableStorage,
    index: Int,
) : Variable {
    override val key: String = "nodes.$index.network.wifi.passphrase"
    override val description: String = "Passphrase of the Wi-Fi network node $index connects to."
    override val writable: Boolean = true
    override val sensitive: Boolean = true
    override fun allowedValues(): List<String>? = null
    override fun read(): String? = storage.read(key)
    override fun write(value: String) = storage.write(key, value)
}
