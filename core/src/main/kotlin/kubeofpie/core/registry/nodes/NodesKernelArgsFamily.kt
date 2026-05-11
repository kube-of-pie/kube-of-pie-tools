package kubeofpie.core.registry.nodes

import jakarta.inject.Singleton
import kubeofpie.core.catalogue.AlpineCatalogue
import kubeofpie.core.registry.Variable
import kubeofpie.core.registry.VariableFamily
import kubeofpie.core.registry.VersionAlpineVariable

/**
 * Per-node `cmdline.txt` kernel arguments under `nodes.<i>.kernel_args`. The index range
 * is `0` to [NodesVariable]`.identifiers().size - 1`; the family is empty until at
 * least one node has been added.
 *
 * The value is read-only and derived from the configured `version.alpine` via
 * [AlpineCatalogue.kernelArgs] — the kernel that ships with each Alpine release dictates
 * which args are required (e.g. `cgroup_memory=1`, `cgroup_enable=memory` for the cgroup
 * driver Kubernetes needs). Today every node sees the same args, but routing through a
 * per-node variable keeps the door open for model-specific args without a key migration.
 */
@Singleton
class NodesKernelArgsFamily(
    private val nodes: NodesVariable,
    private val alpine: VersionAlpineVariable,
    private val catalogue: AlpineCatalogue,
) : VariableFamily {

    override fun keys(): List<String> =
        nodes.identifiers().map { i -> "nodes.$i.kernel_args" }

    override fun variable(key: String): Variable? {
        val match = PATTERN.matchEntire(key) ?: return null
        val (rawIndex) = match.destructured
        if (rawIndex !in nodes.identifiers()) return null
        return NodeKernelArgsVariable(alpine, catalogue, rawIndex.toInt())
    }

    private companion object {
        private val PATTERN = Regex("^nodes\\.(\\d+)\\.kernel_args$")
    }
}

internal class NodeKernelArgsVariable(
    private val alpine: VersionAlpineVariable,
    private val catalogue: AlpineCatalogue,
    index: Int,
) : Variable {
    override val key: String = "nodes.$index.kernel_args"
    override val description: String =
        "Required Linux kernel cmdline arguments for node $index, derived from " +
            "version.alpine; space-separated as they appear in cmdline.txt."
    override val writable: Boolean = false
    override val sensitive: Boolean = false

    override fun allowedValues(): List<String>? = null

    override fun read(): String? {
        val version = alpine.read() ?: return null
        val args = catalogue.kernelArgs(version) ?: return null
        return args.joinToString(" ")
    }

    override fun write(value: String): Unit = throw UnsupportedOperationException(
        "$key is derived from version.alpine; set version.alpine instead.",
    )
}
