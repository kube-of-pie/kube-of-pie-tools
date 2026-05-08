package kubeofpie.core.registry.cluster

import jakarta.inject.Singleton
import kubeofpie.core.registry.Variable
import kubeofpie.core.registry.VariableStorage

/**
 * Number of nodes in the cluster. Drives the per-node variable families
 * (`nodes.<i>.…`) — they enumerate keys for indices `0` through
 * [read]`.toInt() - 1`.
 */
@Singleton
class ClusterNodesCountVariable(private val storage: VariableStorage) : Variable {

    override val key: String = "cluster.nodes.count"
    override val description: String =
        "Total number of Raspberry Pi nodes in the cluster (positive integer)."
    override val writable: Boolean = true
    override val sensitive: Boolean = false

    override fun allowedValues(): List<String>? = null

    override fun read(): String? = storage.read(key)

    override fun write(value: String) {
        val parsed = value.toIntOrNull()
            ?: throw IllegalArgumentException(
                "value for $key must be a positive integer, got: $value"
            )
        require(parsed > 0) {
            "value for $key must be a positive integer, got: $value"
        }
        storage.write(key, parsed.toString())
    }
}
