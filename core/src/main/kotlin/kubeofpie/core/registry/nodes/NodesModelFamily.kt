package kubeofpie.core.registry.nodes

import jakarta.inject.Singleton
import kubeofpie.core.catalogue.RaspberryPiCatalogue
import kubeofpie.core.registry.Variable
import kubeofpie.core.registry.VariableFamily
import kubeofpie.core.registry.VariableStorage

/**
 * Per-node Raspberry Pi model assignment under `nodes.<i>.model`. The index range
 * is `0` to [NodesVariable]`.identifiers().size - 1`; the family is empty until at
 * least one node has been added.
 *
 * Allowed values for each `nodes.<i>.model` are drawn live from
 * [RaspberryPiCatalogue.supportedModelIds] — adding a new model file under
 * `classpath:raspberrypi/<id>.yaml` is enough to make it selectable. The chosen
 * model determines the Alpine architecture, which in turn drives the download URL
 * resolved through [kubeofpie.core.catalogue.AlpineCatalogue.downloadUrl].
 */
@Singleton
class NodesModelFamily(
    private val storage: VariableStorage,
    private val nodes: NodesVariable,
    private val catalogue: RaspberryPiCatalogue,
) : VariableFamily {

    override fun keys(): List<String> =
        nodes.identifiers().map { i -> "nodes.$i.model" }

    override fun variable(key: String): Variable? {
        val match = PATTERN.matchEntire(key) ?: return null
        val (rawIndex) = match.destructured
        if (rawIndex !in nodes.identifiers()) return null
        return NodeModelVariable(storage, catalogue, rawIndex.toInt())
    }

    private companion object {
        private val PATTERN = Regex("^nodes\\.(\\d+)\\.model$")
    }
}

internal class NodeModelVariable(
    private val storage: VariableStorage,
    private val catalogue: RaspberryPiCatalogue,
    index: Int,
) : Variable {
    override val key: String = "nodes.$index.model"
    override val description: String =
        "Raspberry Pi model assigned to node $index; selects the Alpine architecture."
    override val writable: Boolean = true
    override val sensitive: Boolean = false

    override fun allowedValues(): List<String> = catalogue.supportedModelIds()

    override fun read(): String? = storage.read(key)

    override fun write(value: String) = storage.write(key, value)
}
