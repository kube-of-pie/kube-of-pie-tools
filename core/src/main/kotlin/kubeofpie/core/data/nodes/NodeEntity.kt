package kubeofpie.core.data.nodes

import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity

/**
 * Persistence row in the `nodes` SQLite table. Public callers see
 * [kubeofpie.core.business.nodes.Node] (the DTO returned by
 * [kubeofpie.core.business.nodes.NodeManager]); this entity lives next to its
 * [NodeRepository] in the `data.nodes` package. Column mapping uses the global
 * underscore naming strategy configured in `application.yml`, so `wifiEnabled`
 * resolves to the `wifi_enabled` column.
 */
@MappedEntity("nodes")
data class NodeEntity(
    @field:Id val id: String,
    val hostname: String? = null,
    val dns: String? = null,
    val model: String? = null,
    val wifiEnabled: Boolean? = null,
    val wifiSsid: String? = null,
    val wifiPassphrase: String? = null,
)
