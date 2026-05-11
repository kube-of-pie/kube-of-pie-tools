package kubeofpie.core.registry.nodes

import jakarta.inject.Singleton
import kubeofpie.core.business.nodes.NodeManager
import kubeofpie.core.registry.Variable
import kubeofpie.core.registry.VariableFamily

/**
 * Per-node network configuration under `nodes.<id>.network.…`. The set of IDs comes
 * from [NodeManager.list]; the family is empty until at least one node has been
 * added.
 *
 * For each node, the family exposes:
 *
 *  - `nodes.<id>.network.hostname` — writable free text.
 *  - `nodes.<id>.network.dns` — writable free text (IP address).
 *  - `nodes.<id>.network.wifi.enabled` — writable boolean (`true`/`false`).
 *  - `nodes.<id>.network.wifi.ssid` — writable free text.
 *  - `nodes.<id>.network.wifi.passphrase` — writable, sensitive.
 */
@Singleton
class NodesNetworkFamily(private val manager: NodeManager) : VariableFamily {

    override fun keys(): List<String> = manager.list().flatMap { id ->
        SUFFIXES.map { "nodes.$id.network.$it" }
    }

    override fun variable(key: String): Variable? {
        val match = PATTERN.matchEntire(key) ?: return null
        val (name, suffix) = match.destructured
        if (name !in manager.list()) return null
        return when (suffix) {
            "hostname" -> NodeHostnameVariable(manager, name)
            "dns" -> NodeDnsVariable(manager, name)
            "wifi.enabled" -> NodeWifiEnabledVariable(manager, name)
            "wifi.ssid" -> NodeWifiSsidVariable(manager, name)
            "wifi.passphrase" -> NodeWifiPassphraseVariable(manager, name)
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
            "^nodes\\.([a-z0-9](?:[a-z0-9-]*[a-z0-9])?)\\.network\\." +
                "(hostname|dns|wifi\\.enabled|wifi\\.ssid|wifi\\.passphrase)$",
        )
    }
}

internal class NodeHostnameVariable(
    private val manager: NodeManager,
    private val name: String,
) : Variable {
    override val key: String = "nodes.$name.network.hostname"
    override val description: String = "Hostname assigned to node '$name' at first boot."
    override val writable: Boolean = true
    override val sensitive: Boolean = false
    override fun allowedValues(): List<String>? = null
    override fun read(): String? = manager.get(name)?.hostname
    override fun write(value: String) = manager.setHostname(name, value)
}

internal class NodeDnsVariable(
    private val manager: NodeManager,
    private val name: String,
) : Variable {
    override val key: String = "nodes.$name.network.dns"
    override val description: String = "DNS server applied to node '$name' via setup-dns."
    override val writable: Boolean = true
    override val sensitive: Boolean = false
    override fun allowedValues(): List<String>? = null
    override fun read(): String? = manager.get(name)?.dns
    override fun write(value: String) = manager.setDns(name, value)
}

internal class NodeWifiEnabledVariable(
    private val manager: NodeManager,
    private val name: String,
) : Variable {
    override val key: String = "nodes.$name.network.wifi.enabled"
    override val description: String = "Whether node '$name' connects to a Wi-Fi network."
    override val writable: Boolean = true
    override val sensitive: Boolean = false
    override fun allowedValues(): List<String> = listOf("true", "false")
    override fun read(): String? = manager.get(name)?.wifiEnabled?.toString()
    override fun write(value: String) = manager.setWifiEnabled(name, value.toBooleanStrict())
}

internal class NodeWifiSsidVariable(
    private val manager: NodeManager,
    private val name: String,
) : Variable {
    override val key: String = "nodes.$name.network.wifi.ssid"
    override val description: String = "SSID of the Wi-Fi network node '$name' connects to."
    override val writable: Boolean = true
    override val sensitive: Boolean = false
    override fun allowedValues(): List<String>? = null
    override fun read(): String? = manager.get(name)?.wifiSsid
    override fun write(value: String) = manager.setWifiSsid(name, value)
}

internal class NodeWifiPassphraseVariable(
    private val manager: NodeManager,
    private val name: String,
) : Variable {
    override val key: String = "nodes.$name.network.wifi.passphrase"
    override val description: String = "Passphrase of the Wi-Fi network node '$name' connects to."
    override val writable: Boolean = true
    override val sensitive: Boolean = true
    override fun allowedValues(): List<String>? = null
    override fun read(): String? = manager.get(name)?.wifiPassphrase
    override fun write(value: String) = manager.setWifiPassphrase(name, value)
}
