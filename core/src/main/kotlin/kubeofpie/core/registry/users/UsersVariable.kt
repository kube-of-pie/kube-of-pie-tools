package kubeofpie.core.registry.users

import jakarta.inject.Singleton
import kubeofpie.core.registry.ListableVariable
import kubeofpie.core.business.users.UserManager

/**
 * Cluster-wide list of Linux user accounts to create on every node. Adapter over
 * [UserManager]; the `users` SQLite table and all business logic (POSIX name
 * validation, lazy SSH key generation) live in the manager. Each user expands — via
 * [UsersFamily] — into its own `users.<name>.password`, `users.<name>.ssh.enabled`,
 * and `users.<name>.ssh.{private,public}_key` keys.
 *
 * Listable head — not user-writable. Names are added through
 * `config add users <name>` (validated by the manager) and removed through
 * `config remove users <name>`.
 */
@Singleton
class UsersVariable(private val manager: UserManager) : ListableVariable {

    override val key: String = "users"
    override val description: String =
        "Linux user names to create on each node. Manage with `config add users <name>` " +
            "and `config remove users <name>`."
    override val sensitive: Boolean = false

    override fun allowedValues(): List<String>? = null

    override fun read(): String? = null

    override fun identifiers(): List<String> = manager.list()

    override fun add(id: String?): String {
        val name = requireNotNull(id) { "$key requires a user name (got null)" }
        manager.add(name)
        return name
    }

    override fun remove(id: String) {
        manager.remove(id)
    }
}
