package kubeofpie.core.registry.users

import jakarta.inject.Singleton
import kubeofpie.core.registry.Variable
import kubeofpie.core.registry.VariableFamily
import kubeofpie.core.registry.VariableStorage
import kubeofpie.core.secrets.SshKeyGenerator

/**
 * Per-user variables under `users.<name>.…`. The set of usernames is read
 * from [UsersVariable] on every call so the family follows changes to the
 * user list without restart.
 *
 * For each user in the list, four keys are exposed:
 *
 *  - `users.<name>.password` — writable, sensitive free text (the
 *    plain-text password set on the account at first boot).
 *  - `users.<name>.ssh.enabled` — writable boolean (`true`/`false`). When
 *    `true`, the toolchain lazily generates and persists an Ed25519 SSH
 *    key pair for the user the first time `ssh.private_key` or
 *    `ssh.public_key` is read.
 *  - `users.<name>.ssh.private_key` — read-only, sensitive. Holds the
 *    OpenSSH-formatted Ed25519 private key. Returns `null` until both
 *    `ssh.enabled` is `true` and a read has triggered generation, after
 *    which the persisted material is returned regardless of `ssh.enabled`.
 *  - `users.<name>.ssh.public_key` — read-only, not sensitive. The
 *    matching `ssh-ed25519 …` line. Generated and persisted in the same
 *    step as the private key.
 */
@Singleton
class UsersFamily(
    private val storage: VariableStorage,
    private val users: UsersVariable,
    private val sshKeyGenerator: SshKeyGenerator,
) : VariableFamily {

    override fun keys(): List<String> = users.list().flatMap { name ->
        listOf(
            passwordKey(name),
            sshEnabledKey(name),
            sshPrivateKeyKey(name),
            sshPublicKeyKey(name),
        )
    }

    override fun variable(key: String): Variable? {
        val match = PATTERN.matchEntire(key) ?: return null
        val (name, suffix) = match.destructured
        if (name !in users.list()) return null
        return when (suffix) {
            "password" -> UserPasswordVariable(storage, name)
            "ssh.enabled" -> UserSshEnabledVariable(storage, name)
            "ssh.private_key" -> UserSshPrivateKeyVariable(storage, sshKeyGenerator, name)
            "ssh.public_key" -> UserSshPublicKeyVariable(storage, sshKeyGenerator, name)
            else -> null
        }
    }

    private companion object {
        private val PATTERN = Regex(
            "^users\\.([a-z_][a-z0-9_-]*)\\.(password|ssh\\.enabled|ssh\\.private_key|ssh\\.public_key)$"
        )

        fun passwordKey(name: String) = "users.$name.password"
        fun sshEnabledKey(name: String) = "users.$name.ssh.enabled"
        fun sshPrivateKeyKey(name: String) = "users.$name.ssh.private_key"
        fun sshPublicKeyKey(name: String) = "users.$name.ssh.public_key"
    }
}

internal class UserPasswordVariable(
    private val storage: VariableStorage,
    private val user: String,
) : Variable {

    override val key: String = "users.$user.password"
    override val description: String =
        "Plain-text password set for user '$user' at first boot."
    override val writable: Boolean = true
    override val sensitive: Boolean = true

    override fun allowedValues(): List<String>? = null

    override fun read(): String? = storage.read(key)

    override fun write(value: String) = storage.write(key, value)
}

internal class UserSshEnabledVariable(
    private val storage: VariableStorage,
    private val user: String,
) : Variable {

    override val key: String = "users.$user.ssh.enabled"
    override val description: String =
        "Whether the toolchain generates an Ed25519 SSH key pair for user '$user'."
    override val writable: Boolean = true
    override val sensitive: Boolean = false

    override fun allowedValues(): List<String> = listOf("true", "false")

    override fun read(): String? = storage.read(key)

    override fun write(value: String) = storage.write(key, value)
}

internal class UserSshPrivateKeyVariable(
    private val storage: VariableStorage,
    private val generator: SshKeyGenerator,
    private val user: String,
) : Variable {

    override val key: String = "users.$user.ssh.private_key"
    override val description: String =
        "OpenSSH-formatted Ed25519 private key the toolchain installs for user '$user'."
    override val writable: Boolean = false
    override val sensitive: Boolean = true

    override fun allowedValues(): List<String>? = null

    override fun read(): String? = ensureUserSshKeys(storage, generator, user).first

    override fun write(value: String) = storage.write(key, value)
}

internal class UserSshPublicKeyVariable(
    private val storage: VariableStorage,
    private val generator: SshKeyGenerator,
    private val user: String,
) : Variable {

    override val key: String = "users.$user.ssh.public_key"
    override val description: String =
        "OpenSSH `ssh-ed25519 …` public key matching `users.$user.ssh.private_key`."
    override val writable: Boolean = false
    override val sensitive: Boolean = false

    override fun allowedValues(): List<String>? = null

    override fun read(): String? = ensureUserSshKeys(storage, generator, user).second

    override fun write(value: String) = storage.write(key, value)
}

/**
 * Reads `users.<user>.ssh.{private,public}_key` from storage. If both halves
 * are already persisted, returns them as-is. Otherwise, generates and persists
 * a fresh Ed25519 pair only when `users.<user>.ssh.enabled` reads as `"true"`;
 * when generation is gated off, both halves of the pair are returned as `null`.
 */
internal fun ensureUserSshKeys(
    storage: VariableStorage,
    generator: SshKeyGenerator,
    user: String,
): Pair<String?, String?> {
    val privateKeyKey = "users.$user.ssh.private_key"
    val publicKeyKey = "users.$user.ssh.public_key"
    val existingPrivate = storage.read(privateKeyKey)
    val existingPublic = storage.read(publicKeyKey)
    if (existingPrivate != null && existingPublic != null) {
        return existingPrivate to existingPublic
    }
    val enabled = storage.read("users.$user.ssh.enabled")
    if (enabled != "true") {
        return null to null
    }
    val pair = generator.generate("kube-of-pie:$user")
    storage.write(privateKeyKey, pair.privateKey)
    storage.write(publicKeyKey, pair.publicKey)
    return pair.privateKey to pair.publicKey
}
