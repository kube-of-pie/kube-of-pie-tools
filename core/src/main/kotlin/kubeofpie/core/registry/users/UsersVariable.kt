package kubeofpie.core.registry.users

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.inject.Singleton
import kubeofpie.core.registry.ListableVariable
import kubeofpie.core.registry.VariableStorage

/**
 * Cluster-wide list of Linux user accounts to create on every node. Stored as a JSON array
 * of strings (e.g. `["root","kubeofpie"]`). Each entry expands — via [UsersFamily] — into
 * its own `users.<name>.password`, `users.<name>.ssh.enabled`, and
 * `users.<name>.ssh.{private,public}_key` keys.
 *
 * The variable is a [ListableVariable] family head: it is not user-writable, the CLI's
 * `list` and `get` render the identifiers one per line, and membership is changed through
 * [add] / [remove] (driven by `config add users <name>` / `config remove users <name>`).
 *
 * Names must match the conservative POSIX user-name shape used by `useradd`
 * (`[a-z_][a-z0-9_-]*`); anything else is rejected at [add] time so we never persist names
 * that would break the family's key parsing.
 */
@Singleton
class UsersVariable(private val storage: VariableStorage) : ListableVariable {

    override val key: String = "users"
    override val description: String =
        "Linux user names to create on each node. Manage with `config add users <name>` " +
            "and `config remove users <name>`."
    override val sensitive: Boolean = false

    override fun allowedValues(): List<String>? = null

    override fun read(): String? = storage.read(key)

    override fun identifiers(): List<String> {
        val raw = storage.read(key) ?: return emptyList()
        val node = mapper.readTree(raw)
        return node.map { it.textValue() }
    }

    override fun add(id: String?): String {
        val name = requireNotNull(id) { "$key requires a user name (got null)" }
        require(USER_NAME.matches(name)) {
            "invalid user name '$name' for $key: must match ${USER_NAME.pattern}"
        }
        val current = identifiers()
        require(name !in current) { "user '$name' already exists in $key" }
        storage.write(key, mapper.writeValueAsString(current + name))
        return name
    }

    override fun remove(id: String) {
        val current = identifiers()
        require(id in current) { "user '$id' is not in $key" }
        storage.write(key, mapper.writeValueAsString(current - id))
    }

    private companion object {
        private val mapper: ObjectMapper = ObjectMapper()
        private val USER_NAME = Regex("^[a-z_][a-z0-9_-]*$")
    }
}
