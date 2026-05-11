package kubeofpie.core.registry

/**
 * A *family head* — a non-writable [Variable] whose value is the set of identifiers of a
 * dynamic-key [VariableFamily]. The identifier list (user names, node indices, ...) is
 * mutated through [add] and [remove] rather than the generic `Variable.write` path; the
 * registry rejects writes to listable heads (they are always `writable = false`) so the
 * CLI must call [add]/[remove] directly via the dedicated subcommands.
 *
 * Listable heads are rendered specially by the CLI's `list` and `get`: their value is
 * shown as one identifier per line instead of as a single string.
 */
interface ListableVariable : Variable {
    /** Listable heads are never user-writable; mutation goes through [add]/[remove]. */
    override val writable: Boolean
        get() = false

    /**
     * Listable heads are never user-writable; the registry rejects writes before this is
     * called. The default impl throws — overriding it would be a bug.
     */
    override fun write(value: String): Unit = throw UnsupportedOperationException(
        "$key is listable; use `config add $key [<id>]` / `config remove $key <id>` instead.",
    )

    /** Current identifiers, in registration order. */
    fun identifiers(): List<String>

    /**
     * Add a new identifier and return the value that was actually stored. Both nodes and
     * users are name-keyed: [id] must be supplied and pass the family's validation regex.
     *
     * Throws [IllegalArgumentException] if [id] is missing, malformed, or already present.
     */
    fun add(id: String?): String

    /**
     * Remove [id] from the family. Throws [IllegalArgumentException] if [id] is unknown.
     */
    fun remove(id: String)
}
