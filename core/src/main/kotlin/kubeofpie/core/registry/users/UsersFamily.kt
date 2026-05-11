package kubeofpie.core.registry.users

import jakarta.inject.Singleton
import kubeofpie.core.registry.Variable
import kubeofpie.core.registry.VariableFamily
import kubeofpie.core.business.users.UserManager

/**
 * Per-user variables under `users.<name>.…`. The set of usernames is read from
 * [UserManager.list] on every call so the family follows changes to the user list
 * without restart.
 *
 * For each user in the list, four keys are exposed:
 *
 *  - `users.<name>.password` — writable, sensitive free text (the plain-text password
 *    set on the account at first boot).
 *  - `users.<name>.ssh.enabled` — writable boolean (`true`/`false`). When set to
 *    `true`, [UserManager] generates and persists an Ed25519 SSH key pair for the user
 *    in the same write (skipped if a pair is already stored).
 *  - `users.<name>.ssh.private_key` — read-only, sensitive. Holds the OpenSSH-formatted
 *    Ed25519 private key. Returns `null` until `ssh.enabled` has been set to `true`
 *    at least once; the persisted material is returned thereafter, regardless of the
 *    current `ssh.enabled` state.
 *  - `users.<name>.ssh.public_key` — read-only, not sensitive. The matching
 *    `ssh-ed25519 …` line. Generated and persisted in the same step as the private key.
 */
@Singleton
class UsersFamily(private val manager: UserManager) : VariableFamily {

    override fun keys(): List<String> = manager.list().flatMap { name ->
        listOf(
            "users.$name.password",
            "users.$name.ssh.enabled",
            "users.$name.ssh.private_key",
            "users.$name.ssh.public_key",
        )
    }

    override fun variable(key: String): Variable? {
        val match = PATTERN.matchEntire(key) ?: return null
        val (name, suffix) = match.destructured
        if (name !in manager.list()) return null
        return when (suffix) {
            "password" -> UserPasswordVariable(manager, name)
            "ssh.enabled" -> UserSshEnabledVariable(manager, name)
            "ssh.private_key" -> UserSshPrivateKeyVariable(manager, name)
            "ssh.public_key" -> UserSshPublicKeyVariable(manager, name)
            else -> null
        }
    }

    private companion object {
        private val PATTERN = Regex(
            "^users\\.([a-z_][a-z0-9_-]*)\\.(password|ssh\\.enabled|ssh\\.private_key|ssh\\.public_key)$",
        )
    }
}

internal class UserPasswordVariable(
    private val manager: UserManager,
    private val name: String,
) : Variable {
    override val key: String = "users.$name.password"
    override val description: String =
        "Plain-text password set for user '$name' at first boot."
    override val writable: Boolean = true
    override val sensitive: Boolean = true
    override fun allowedValues(): List<String>? = null
    override fun read(): String? = manager.get(name)?.password
    override fun write(value: String) = manager.setPassword(name, value)
}

internal class UserSshEnabledVariable(
    private val manager: UserManager,
    private val name: String,
) : Variable {
    override val key: String = "users.$name.ssh.enabled"
    override val description: String =
        "Whether the toolchain generates an Ed25519 SSH key pair for user '$name'."
    override val writable: Boolean = true
    override val sensitive: Boolean = false
    override fun allowedValues(): List<String> = listOf("true", "false")
    override fun read(): String? = manager.get(name)?.sshEnabled?.toString()
    override fun write(value: String) = manager.setSshEnabled(name, value.toBooleanStrict())
}

internal class UserSshPrivateKeyVariable(
    private val manager: UserManager,
    private val name: String,
) : Variable {
    override val key: String = "users.$name.ssh.private_key"
    override val description: String =
        "OpenSSH-formatted Ed25519 private key the toolchain installs for user '$name'."
    override val writable: Boolean = false
    override val sensitive: Boolean = true
    override fun allowedValues(): List<String>? = null
    override fun read(): String? = manager.getSshPrivateKey(name)
    override fun write(value: String): Unit = throw UnsupportedOperationException(
        "$key is generated; toggle users.$name.ssh.enabled instead.",
    )
}

internal class UserSshPublicKeyVariable(
    private val manager: UserManager,
    private val name: String,
) : Variable {
    override val key: String = "users.$name.ssh.public_key"
    override val description: String =
        "OpenSSH `ssh-ed25519 …` public key matching `users.$name.ssh.private_key`."
    override val writable: Boolean = false
    override val sensitive: Boolean = false
    override fun allowedValues(): List<String>? = null
    override fun read(): String? = manager.getSshPublicKey(name)
    override fun write(value: String): Unit = throw UnsupportedOperationException(
        "$key is generated; toggle users.$name.ssh.enabled instead.",
    )
}
