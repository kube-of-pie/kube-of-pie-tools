package kubeofpie.core.registry.users

import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.inject.Singleton
import kubeofpie.core.registry.Variable
import kubeofpie.core.registry.VariableStorage

/**
 * Cluster-wide list of Linux user accounts to create on every node, stored as
 * a JSON array of strings (e.g. `["root","kubeofpie"]`). Each entry expands —
 * via [UsersFamily] — into its own `users.<name>.password`,
 * `users.<name>.ssh.enabled`, and `users.<name>.ssh.{private,public}_key` keys.
 *
 * Names are validated against the conservative POSIX user-name shape used by
 * `useradd` (`[a-z_][a-z0-9_-]*`). Anything else is rejected at write time so
 * we never end up persisting names that would break the family's key parsing.
 */
@Singleton
class UsersVariable(private val storage: VariableStorage) : Variable {

    override val key: String = "users"
    override val description: String =
        "JSON array of Linux user names to create on each node (e.g. " +
            "[\"root\",\"kubeofpie\"])."
    override val writable: Boolean = true
    override val sensitive: Boolean = false

    override fun allowedValues(): List<String>? = null

    override fun read(): String? = storage.read(key)

    override fun write(value: String) {
        val node = try {
            mapper.readTree(value)
        } catch (e: JacksonException) {
            throw IllegalArgumentException(badShape(value), e)
        }
        if (!node.isArray || !node.all { it.isTextual }) {
            throw IllegalArgumentException(badShape(value))
        }
        val names = node.map { it.textValue() }
        names.forEach { name ->
            require(USER_NAME.matches(name)) {
                "invalid user name '$name' for $key: must match ${USER_NAME.pattern}"
            }
        }
        require(names.size == names.toSet().size) {
            "duplicate user name in $key: $names"
        }
        storage.write(key, mapper.writeValueAsString(names))
    }

    /** Parsed list of user names, or empty if unset. */
    fun list(): List<String> {
        val raw = storage.read(key) ?: return emptyList()
        val node = mapper.readTree(raw)
        return node.map { it.textValue() }
    }

    private fun badShape(value: String): String =
        "value for $key must be a JSON array of strings, got: $value"

    private companion object {
        private val mapper: ObjectMapper = ObjectMapper()
        private val USER_NAME = Regex("^[a-z_][a-z0-9_-]*$")
    }
}
