package kubeofpie.core.business.nodes

/**
 * Public DTO returned by [NodeManager]. Mirrors [NodeEntity]'s shape but kept distinct
 * so the persistence type stays internal to the module. `null` on any field means the
 * value has not been set yet.
 */
data class Node(
    val id: String,
    val hostname: String? = null,
    val dns: String? = null,
    val model: String? = null,
    val wifiEnabled: Boolean? = null,
    val wifiSsid: String? = null,
    val wifiPassphrase: String? = null,
)
