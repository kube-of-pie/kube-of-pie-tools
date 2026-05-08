package kubeofpie.core.registry

/**
 * One entry in the kube-of-pie configuration namespace, identified by a dotted [key]
 * (`version.alpine`, `nodes.0.hostname`, ...).
 *
 * Each implementation is a Micronaut `@Singleton`; [VariableRegistry] collects every bean
 * and presents them through a uniform read/write surface. Storage is the variable's own
 * concern — user-writable values may sit in the SQLite config DB, derived ones may come
 * from the static catalogues, generated secrets may be produced on first read.
 */
interface Variable {
    /** Dotted identifier, unique across the registry. */
    val key: String

    /** Human-readable explanation, surfaced by the CLI and web UI alongside the value. */
    val description: String

    /**
     * Whether the user may set this variable directly. Derived, catalogue-pinned, and
     * generated variables are read-only; the registry rejects writes to them before
     * calling [write].
     */
    val writable: Boolean

    /**
     * Whether the value must be redacted in default output (SSH private keys, passwords,
     * Wi-Fi passphrases, ...). Revealing such values requires an explicit caller opt-in.
     */
    val sensitive: Boolean

    /**
     * Admissible values in the current registry state, or `null` when the domain is
     * unbounded. The set may depend on other variables (e.g. `version.kubernetes` is
     * constrained by the apk repositories of the selected `version.alpine`), so callers
     * must not cache the result across writes.
     */
    fun allowedValues(): List<String>?

    /** Current value, or `null` when the variable is unset. */
    fun read(): String?

    /**
     * Persist [value]. The registry validates [writable] and [allowedValues] membership
     * before calling this, so implementations may assume those checks have passed.
     */
    fun write(value: String)
}
