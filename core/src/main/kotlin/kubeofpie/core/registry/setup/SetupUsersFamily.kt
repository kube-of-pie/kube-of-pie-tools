package kubeofpie.core.registry.setup

import jakarta.inject.Singleton
import kubeofpie.core.registry.Variable
import kubeofpie.core.registry.VariableFamily
import kubeofpie.core.registry.VariableStorage
import kubeofpie.core.secrets.SshKeyGenerator

/**
 * Per-user variables under `setup.users.<name>.…`. The set of usernames is
 * read from [SetupUsersVariable] on every call so the family follows changes
 * to the user list without restart.
 *
 * For each user in the list, three keys are exposed:
 *
 *  - `setup.users.<name>.password` — writable, sensitive free text (the
 *    plain-text password set on the account at first boot).
 *  - `setup.users.<name>.ssh.private_key` — read-only, sensitive. Holds the
 *    OpenSSH-formatted Ed25519 private key. Generated lazily on first read
 *    and persisted so subsequent reads return the same material.
 *  - `setup.users.<name>.ssh.public_key` — read-only, not sensitive. The
 *    matching `ssh-ed25519 …` line. Generated and persisted in the same
 *    step as the private key.
 */
@Singleton
class SetupUsersFamily(
    private val storage: VariableStorage,
    private val users: SetupUsersVariable,
    private val sshKeyGenerator: SshKeyGenerator,
) : VariableFamily {

    override fun keys(): List<String> = users.list().flatMap { name ->
        listOf(
            passwordKey(name),
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
            "ssh.private_key" -> UserSshPrivateKeyVariable(storage, sshKeyGenerator, name)
            "ssh.public_key" -> UserSshPublicKeyVariable(storage, sshKeyGenerator, name)
            else -> null
        }
    }

    private companion object {
        private val PATTERN = Regex(
            "^setup\\.users\\.([a-z_][a-z0-9_-]*)\\.(password|ssh\\.private_key|ssh\\.public_key)$"
        )

        fun passwordKey(name: String) = "setup.users.$name.password"
        fun sshPrivateKeyKey(name: String) = "setup.users.$name.ssh.private_key"
        fun sshPublicKeyKey(name: String) = "setup.users.$name.ssh.public_key"
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
    private val generator: SshKeyGenerator,
    private val user: String,
) : Variable {

    override val key: String = "setup.users.$user.ssh.private_key"
    override val description: String =
        "OpenSSH-formatted Ed25519 private key the toolchain installs for user '$user'."
    override val writable: Boolean = false
    override val sensitive: Boolean = true

    override fun allowedValues(): List<String>? = null

    override fun read(): String = ensureUserSshKeys(storage, generator, user).first

    override fun write(value: String) = storage.write(key, value)
}

internal class UserSshPublicKeyVariable(
    private val storage: VariableStorage,
    private val generator: SshKeyGenerator,
    private val user: String,
) : Variable {

    override val key: String = "setup.users.$user.ssh.public_key"
    override val description: String =
        "OpenSSH `ssh-ed25519 …` public key matching `setup.users.$user.ssh.private_key`."
    override val writable: Boolean = false
    override val sensitive: Boolean = false

    override fun allowedValues(): List<String>? = null

    override fun read(): String = ensureUserSshKeys(storage, generator, user).second

    override fun write(value: String) = storage.write(key, value)
}

/**
 * Reads `setup.users.<user>.ssh.{private,public}_key` from storage; if either
 * is missing, generates a fresh Ed25519 pair, persists both halves, and
 * returns them. Idempotent on subsequent calls.
 */
internal fun ensureUserSshKeys(
    storage: VariableStorage,
    generator: SshKeyGenerator,
    user: String,
): Pair<String, String> {
    val privateKeyKey = "setup.users.$user.ssh.private_key"
    val publicKeyKey = "setup.users.$user.ssh.public_key"
    val existingPrivate = storage.read(privateKeyKey)
    val existingPublic = storage.read(publicKeyKey)
    if (existingPrivate != null && existingPublic != null) {
        return existingPrivate to existingPublic
    }
    val pair = generator.generate("kube-of-pie:$user")
    storage.write(privateKeyKey, pair.privateKey)
    storage.write(publicKeyKey, pair.publicKey)
    return pair.privateKey to pair.publicKey
}
