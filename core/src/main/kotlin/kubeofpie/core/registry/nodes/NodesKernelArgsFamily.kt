package kubeofpie.core.registry.nodes

import jakarta.inject.Singleton
import kubeofpie.core.business.nodes.NodeManager
import kubeofpie.core.registry.Variable
import kubeofpie.core.registry.VariableFamily

/**
 * Per-node `cmdline.txt` kernel arguments under `nodes.<id>.kernel_args`. The family
 * is empty until at least one node has been added.
 *
 * The value is read-only and derived: [NodeManager.kernelArgs] looks up the cluster's
 * `version.alpine` and joins the kernel-args list it pulls from the Alpine catalogue
 * (e.g. `cgroup_memory=1`, `cgroup_enable=memory` for the cgroup driver Kubernetes
 * needs). Today every node sees the same args, but routing through a per-node
 * variable keeps the door open for model-specific args without a key migration.
 */
@Singleton
class NodesKernelArgsFamily(private val manager: NodeManager) : VariableFamily {

    override fun keys(): List<String> =
        manager.list().map { id -> "nodes.$id.kernel_args" }

    override fun variable(key: String): Variable? {
        val match = PATTERN.matchEntire(key) ?: return null
        val (name) = match.destructured
        if (name !in manager.list()) return null
        return NodeKernelArgsVariable(manager, name)
    }

    private companion object {
        private val PATTERN =
            Regex("^nodes\\.([a-z0-9](?:[a-z0-9-]*[a-z0-9])?)\\.kernel_args$")
    }
}

internal class NodeKernelArgsVariable(
    private val manager: NodeManager,
    private val name: String,
) : Variable {
    override val key: String = "nodes.$name.kernel_args"
    override val description: String =
        "Required Linux kernel cmdline arguments for node '$name', derived from " +
            "version.alpine; space-separated as they appear in cmdline.txt."
    override val writable: Boolean = false
    override val sensitive: Boolean = false

    override fun allowedValues(): List<String>? = null

    override fun read(): String? = manager.kernelArgs(name)

    override fun write(value: String): Unit = throw UnsupportedOperationException(
        "$key is derived from version.alpine; set version.alpine instead.",
    )
}
