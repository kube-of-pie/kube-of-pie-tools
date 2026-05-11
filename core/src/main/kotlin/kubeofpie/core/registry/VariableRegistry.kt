package kubeofpie.core.registry

import jakarta.inject.Singleton

/**
 * Public surface of the kube-of-pie configuration for the CLI. The web UI talks to
 * the entity managers (`NodeManager`, `UserManager`) directly; this registry is the
 * dotted-key view the CLI relies on. SQLite, Flyway, and other persistence details
 * sit behind individual [Variable] implementations and (for nodes/users) those
 * managers.
 *
 * Every `@Singleton Variable` bean on the classpath is collected via constructor
 * injection and exposed by its dotted key. [VariableFamily] beans contribute
 * dynamic keys (per-user, per-node, ...) on top. Writes are validated (writable
 * flag plus [Variable.allowedValues] membership) before being delegated to the
 * variable.
 */
@Singleton
class VariableRegistry(
    private val variables: List<Variable>,
    private val families: List<VariableFamily>,
) {
    private val staticByKey: Map<String, Variable> = variables.associateBy(Variable::key)

    /**
     * Variables in registration order, optionally filtered. Static variables come
     * first, followed by family-produced ones in family-registration order.
     *
     * @param prefix only include variables whose [Variable.key] starts with this string.
     * @param writableOnly drop derived, catalogue-pinned, and generated variables.
     */
    fun list(prefix: String? = null, writableOnly: Boolean = false): List<Variable> {
        val all = sequence {
            yieldAll(variables)
            for (family in families) {
                for (key in family.keys()) {
                    family.variable(key)?.let { yield(it) }
                }
            }
        }
        return all
            .filter { prefix == null || it.key.startsWith(prefix) }
            .filter { !writableOnly || it.writable }
            .toList()
    }

    /** The variable for [key], or `null` when no variable with that key is registered. */
    fun read(key: String): Variable? {
        staticByKey[key]?.let { return it }
        for (family in families) {
            family.variable(key)?.let { return it }
        }
        return null
    }

    /**
     * Validate and persist [value] for [key]. Throws [IllegalArgumentException] when the
     * key is unknown, the variable is not user-writable, or the value is outside the
     * variable's [Variable.allowedValues].
     */
    fun write(key: String, value: String) {
        val variable = read(key)
            ?: throw IllegalArgumentException("unknown variable: $key")
        require(variable.writable) {
            "variable is not user-writable: $key (${variable.description})"
        }
        val allowed = variable.allowedValues()
        require(allowed == null || value in allowed) {
            "value '$value' is not allowed for $key (${variable.description})"
        }
        variable.write(value)
    }
}
