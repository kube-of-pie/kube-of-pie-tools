package kubeofpie.core.registry.setup

import jakarta.inject.Singleton
import kubeofpie.core.registry.Variable
import kubeofpie.core.registry.VariableFamily
import kubeofpie.core.registry.VariableStorage

/**
 * Per-user variables under `setup.users.<name>.…`. The set of usernames is
 * read from [SetupUsersVariable] on every call so the family follows changes
 * to the user list without restart.
 *
 * For each user in the list, two keys are exposed:
 *
 *  - `setup.users.<name>.password` — writable, sensitive free text (the
 *    plain-text password set on the account at first boot).
 *  - `setup.users.<name>.ssh.private_key` — read-only, sensitive. Holds the
 *    SSH private key material the toolchain installs for the user. Population
 *    of this secret is out of scope for the registry today; until a generator
 *    is wired in, the value is whatever was pre-loaded into the DB (or null).
 */
@Singleton
class SetupUsersFamily(
    private val storage: VariableStorage,
    private val users: SetupUsersVariable,
) : VariableFamily {

    override fun keys(): List<String> = users.list().flatMap { name ->
        listOf(
            passwordKey(name),
            sshPrivateKeyKey(name),
        )
    }

    override fun variable(key: String): Variable? {
        val match = PATTERN.matchEntire(key) ?: return null
        val (name, suffix) = match.destructured
        if (name !in users.list()) return null
        return when (suffix) {
            "password" -> UserPasswordVariable(storage, name)
            "ssh.private_key" -> UserSshPrivateKeyVariable(storage, name)
            else -> null
        }
    }

    private companion object {
        private val PATTERN = Regex("^setup\\.users\\.([a-z_][a-z0-9_-]*)\\.(password|ssh\\.private_key)$")

        fun passwordKey(name: String) = "setup.users.$name.password"
        fun sshPrivateKeyKey(name: String) = "setup.users.$name.ssh.private_key"
    }
}

internal class UserPasswordVariable(
    private val storage: VariableStorage,
    private val user: String,
) : Variable {

    override val key: String = "setup.users.$user.password"
    override val description: String =
        "Plain-text password set for user '$user' at first boot."
    override val writable: Boolean = true
    override val sensitive: Boolean = true

    override fun allowedValues(): List<String>? = null

    override fun read(): String? = storage.read(key)

    override fun write(value: String) = storage.write(key, value)
}

internal class UserSshPrivateKeyVariable(
    private val storage: VariableStorage,
    private val user: String,
) : Variable {

    override val key: String = "setup.users.$user.ssh.private_key"
    override val description: String =
        "Generated SSH private key the toolchain installs for user '$user'."
    override val writable: Boolean = false
    override val sensitive: Boolean = true

    override fun allowedValues(): List<String>? = null

    override fun read(): String? = storage.read(key)

    override fun write(value: String) = storage.write(key, value)
}
