package kubeofpie.core.registry.nodes

import jakarta.inject.Singleton
import kubeofpie.core.business.nodes.NodeManager
import kubeofpie.core.registry.Variable
import kubeofpie.core.registry.VariableFamily

/**
 * Per-node Raspberry Pi model assignment under `nodes.<id>.model`. The family is
 * empty until at least one node has been added.
 *
 * Allowed values for each `nodes.<id>.model` are drawn live from
 * [NodeManager.allowedModels] (which itself delegates to the Raspberry Pi catalogue) —
 * dropping a new model file under `classpath:raspberrypi/<id>.yaml` is enough to make
 * it selectable. The chosen model drives the Alpine architecture, which in turn drives
 * the download URL resolved through
 * [kubeofpie.core.catalogue.AlpineCatalogue.downloadUrl].
 */
@Singleton
class NodesModelFamily(private val manager: NodeManager) : VariableFamily {

    override fun keys(): List<String> =
        manager.list().map { id -> "nodes.$id.model" }

    override fun variable(key: String): Variable? {
        val match = PATTERN.matchEntire(key) ?: return null
        val (name) = match.destructured
        if (name !in manager.list()) return null
        return NodeModelVariable(manager, name)
    }

    private companion object {
        private val PATTERN = Regex("^nodes\\.([a-z0-9](?:[a-z0-9-]*[a-z0-9])?)\\.model$")
    }
}

internal class NodeModelVariable(
    private val manager: NodeManager,
    private val name: String,
) : Variable {
    override val key: String = "nodes.$name.model"
    override val description: String =
        "Raspberry Pi model assigned to node '$name'; selects the Alpine architecture."
    override val writable: Boolean = true
    override val sensitive: Boolean = false

    override fun allowedValues(): List<String> = manager.allowedModels()

    override fun read(): String? = manager.get(name)?.model

    override fun write(value: String) = manager.setModel(name, value)
}
