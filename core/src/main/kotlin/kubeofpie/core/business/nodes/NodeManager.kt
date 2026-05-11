package kubeofpie.core.business.nodes

import jakarta.inject.Provider
import jakarta.inject.Singleton
import kubeofpie.core.business.versions.AlpineVersionManager
import kubeofpie.core.catalogue.AlpineCatalogue
import kubeofpie.core.catalogue.RaspberryPiCatalogue
import kubeofpie.core.data.nodes.NodeEntity
import kubeofpie.core.data.nodes.NodeRepository

/**
 * Owns the cluster's node entities and the business logic around them — ID validation,
 * uniqueness, allowed Raspberry Pi models, and derivation of per-node kernel arguments
 * from the cluster's `version.alpine`. Both the CLI's variable adapters and any future
 * web UI go through this manager rather than the generic variable registry; the
 * manager talks to a Micronaut Data JDBC repository underneath.
 *
 * `repositoryProvider` is a [Provider] so the repository — and the `DataSource` it
 * depends on — is resolved lazily, after the CLI has called
 * [kubeofpie.core.storage.ConfigDatabase.open]. Resolving eagerly would fail because
 * the data source factory throws "database not opened" until then.
 */
@Singleton
class NodeManager(
    private val repositoryProvider: Provider<NodeRepository>,
    private val raspberryPiCatalogue: RaspberryPiCatalogue,
    private val alpineCatalogue: AlpineCatalogue,
    private val alpineVersion: AlpineVersionManager,
) {

    private val repository: NodeRepository get() = repositoryProvider.get()

    /** Existing node IDs in insertion order. */
    fun list(): List<String> = repository.listAllInInsertionOrder().map { it.id }

    /** Full DTO for [id], or `null` when the node is not registered. */
    fun get(id: String): Node? = repository.findById(id).orElse(null)?.toNode()

    /**
     * Validate [id] against the DNS / RFC-1123 label shape and create an empty row for it.
     * Throws [IllegalArgumentException] if [id] is malformed or already exists.
     */
    fun add(id: String): Node {
        validateId(id)
        require(!repository.existsById(id)) { "node '$id' already exists" }
        repository.save(NodeEntity(id = id))
        return Node(id = id)
    }

    /** Remove [id]. Throws [IllegalArgumentException] when no such node is registered. */
    fun remove(id: String) {
        require(repository.existsById(id)) { "node '$id' is not registered" }
        repository.deleteById(id)
    }

    fun setHostname(id: String, value: String?) = mutate(id) { it.copy(hostname = value) }

    fun setDns(id: String, value: String?) = mutate(id) { it.copy(dns = value) }

    /**
     * Set the Raspberry Pi model for [id]. When [value] is non-null it must be a member of
     * [allowedModels]; null clears the assignment.
     */
    fun setModel(id: String, value: String?) {
        if (value != null) {
            val allowed = allowedModels()
            require(value in allowed) {
                "model '$value' is not in the supported set $allowed"
            }
        }
        mutate(id) { it.copy(model = value) }
    }

    fun setWifiEnabled(id: String, value: Boolean?) = mutate(id) { it.copy(wifiEnabled = value) }

    fun setWifiSsid(id: String, value: String?) = mutate(id) { it.copy(wifiSsid = value) }

    fun setWifiPassphrase(id: String, value: String?) =
        mutate(id) { it.copy(wifiPassphrase = value) }

    /** Supported Raspberry Pi model identifiers, sourced from [RaspberryPiCatalogue]. */
    fun allowedModels(): List<String> = raspberryPiCatalogue.supportedModelIds()

    /**
     * `cmdline.txt` kernel arguments derived from the cluster-wide `version.alpine`,
     * space-joined as they would appear on the kernel command line. Returns `null` when
     * the node is unknown, when `version.alpine` is unset, or when the alpine catalogue
     * does not know about the configured version. Today every node receives the same
     * args; this routes through a per-node accessor so model-specific args can be added
     * later without a key migration.
     */
    fun kernelArgs(id: String): String? {
        if (!repository.existsById(id)) return null
        val version = alpineVersion.get() ?: return null
        val args = alpineCatalogue.kernelArgs(version) ?: return null
        return args.joinToString(" ")
    }

    /**
     * Throws [IllegalArgumentException] if [id] is not a valid DNS / RFC-1123 label.
     * Exposed so the future web UI can validate form input without a round-trip.
     */
    fun validateId(id: String) {
        require(NODE_ID.matches(id)) {
            "invalid node id '$id': must match ${NODE_ID.pattern}"
        }
    }

    private fun mutate(id: String, transform: (NodeEntity) -> NodeEntity) {
        val current = repository.findById(id).orElse(null)
            ?: throw IllegalArgumentException("node '$id' is not registered")
        repository.update(transform(current))
    }

    private fun NodeEntity.toNode() = Node(
        id = id,
        hostname = hostname,
        dns = dns,
        model = model,
        wifiEnabled = wifiEnabled,
        wifiSsid = wifiSsid,
        wifiPassphrase = wifiPassphrase,
    )

    private companion object {
        private val NODE_ID = Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?$")
    }
}
