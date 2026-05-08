package kubeofpie.core.registry

/**
 * A *family* produces multiple [Variable] entries whose keys share a common shape
 * but vary in a dynamic component — a username (`setup.users.<name>.password`),
 * a node index (`nodes.<i>.network.hostname`), or a list element. Concrete
 * variables can only be enumerated after looking at the current registry state
 * (e.g. the configured user list, the current `cluster.nodes.count`), so they
 * cannot be expressed as static `@Singleton Variable` beans.
 *
 * Each family is itself a Micronaut `@Singleton`. [VariableRegistry] collects
 * every family bean and consults them alongside the static [Variable] beans for
 * `list`, `read`, and `write`.
 */
interface VariableFamily {
    /**
     * The set of keys this family currently exposes, in registration order.
     * Recomputed on every call so the result reflects the latest state of the
     * variables this family depends on.
     */
    fun keys(): List<String>

    /**
     * The [Variable] for [key], or `null` if [key] does not belong to this
     * family. Implementations may return a fresh instance per call; the
     * registry does not cache them.
     */
    fun variable(key: String): Variable?
}
