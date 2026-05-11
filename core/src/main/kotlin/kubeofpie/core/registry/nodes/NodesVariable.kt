package kubeofpie.core.registry.nodes

import jakarta.inject.Singleton
import kubeofpie.core.business.nodes.NodeManager
import kubeofpie.core.registry.ListableVariable

/**
 * Cluster nodes, identified by a user-supplied DNS / RFC-1123 label (e.g. `master`,
 * `worker-1`, `pi5-east`). Adapter over [NodeManager]; the `nodes` SQLite table and
 * all business logic live in the manager. Per-node families
 * (`nodes.<id>.network.…`, `nodes.<id>.model`, `nodes.<id>.kernel_args`) enumerate
 * keys for the IDs returned by [identifiers].
 *
 * Listable head — not user-writable. IDs are added through `config add nodes <id>`
 * (validated by the manager) and removed through `config remove nodes <id>`.
 */
@Singleton
class NodesVariable(private val manager: NodeManager) : ListableVariable {

    override val key: String = "nodes"
    override val description: String =
        "Cluster node identifiers. Manage with `config add nodes <id>` and " +
            "`config remove nodes <id>`."
    override val sensitive: Boolean = false

    override fun allowedValues(): List<String>? = null

    override fun read(): String? = null

    override fun identifiers(): List<String> = manager.list()

    override fun add(id: String?): String {
        val name = requireNotNull(id) { "$key requires a node id (got null)" }
        manager.add(name)
        return name
    }

    override fun remove(id: String) {
        manager.remove(id)
    }
}
