package kubeofpie.core.registry.nodes

import jakarta.inject.Singleton
import kubeofpie.core.registry.ListableVariable
import kubeofpie.core.registry.VariableStorage

/**
 * Cluster nodes, identified by their positional index (`0`, `1`, ...). The count of
 * indices drives the per-node families (`nodes.<i>.network.…`, ...): they enumerate
 * keys for indices `0` through `identifiers().size - 1`.
 *
 * Stored as the count of nodes; identifiers are derived as `0..count-1`. The variable
 * is a non-writable [ListableVariable]: indices grow only through [add] (which
 * auto-assigns the next index) and shrink through [remove] (which only accepts the
 * highest current index, to avoid renumbering existing per-node data).
 */
@Singleton
class NodesVariable(private val storage: VariableStorage) : ListableVariable {

    override val key: String = "nodes"
    override val description: String =
        "Cluster node indices. Manage with `config add nodes` and " +
            "`config remove nodes <index>`."
    override val sensitive: Boolean = false

    override fun allowedValues(): List<String>? = null

    override fun read(): String? = storage.read(key)

    override fun identifiers(): List<String> = (0 until count()).map { it.toString() }

    override fun add(id: String?): String {
        val current = count()
        if (id != null) {
            require(id == current.toString()) {
                "$key.add accepts no id or the next index ($current); got '$id'"
            }
        }
        storage.write(key, (current + 1).toString())
        return current.toString()
    }

    override fun remove(id: String) {
        val current = count()
        require(current > 0) { "no nodes to remove from $key" }
        val last = (current - 1).toString()
        require(id == last) {
            "only the highest index ($last) can be removed from $key; got '$id'"
        }
        storage.write(key, (current - 1).toString())
    }

    private fun count(): Int = storage.read(key)?.toIntOrNull() ?: 0
}
