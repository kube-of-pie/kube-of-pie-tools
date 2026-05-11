package kubeofpie.core.business.users

import jakarta.inject.Provider
import jakarta.inject.Singleton
import kubeofpie.core.data.users.UserEntity
import kubeofpie.core.data.users.UserRepository
import kubeofpie.core.secrets.SshKeyGenerator

/**
 * Owns the cluster's user entities and the business logic around them — POSIX name
 * validation, uniqueness, and the generation of an Ed25519 SSH key pair triggered by
 * setting `sshEnabled = true`. Keys are produced and persisted in the same write that
 * flips the flag, so later reads (including the read-only path through `config get`)
 * never need to write to the database. Both the CLI's variable adapters and any future
 * web UI go through this manager rather than the generic variable registry; the manager
 * talks to a Micronaut Data JDBC repository underneath.
 *
 * `repositoryProvider` is a [Provider] so the repository — and the `DataSource` it
 * depends on — is resolved lazily, after the CLI has called
 * [kubeofpie.core.storage.ConfigDatabase.open]. Resolving eagerly would fail because
 * the data source factory throws "database not opened" until then.
 */
@Singleton
class UserManager(
    private val repositoryProvider: Provider<UserRepository>,
    private val sshKeyGenerator: SshKeyGenerator,
) {

    private val repository: UserRepository get() = repositoryProvider.get()

    /** Existing user names in insertion order. */
    fun list(): List<String> = repository.listAllInInsertionOrder().map { it.name }

    /** Full DTO for [name], or `null` when the user is not registered. */
    fun get(name: String): User? = repository.findById(name).orElse(null)?.toUser()

    /**
     * Validate [name] against the POSIX `useradd` shape and create an empty row for it.
     * Throws [IllegalArgumentException] if [name] is malformed or already exists.
     */
    fun add(name: String): User {
        validateName(name)
        require(!repository.existsById(name)) { "user '$name' already exists" }
        repository.save(UserEntity(name = name))
        return User(name = name)
    }

    /** Remove [name]. Throws [IllegalArgumentException] when no such user is registered. */
    fun remove(name: String) {
        require(repository.existsById(name)) { "user '$name' is not registered" }
        repository.deleteById(name)
    }

    fun setPassword(name: String, value: String?) = mutate(name) { it.copy(password = value) }

    /**
     * Set the SSH-enabled flag for [name]. When [value] is `true` and the user does not
     * already have a persisted key pair, generates and persists one in the same write
     * — so subsequent reads (including read-only ones via `config get`) can return the
     * material without needing a write. Setting `false` or `null` leaves any
     * previously-generated keys in place; they remain readable.
     */
    fun setSshEnabled(name: String, value: Boolean?) {
        val current = repository.findById(name).orElse(null)
            ?: throw IllegalArgumentException("user '$name' is not registered")
        val updated = current.copy(sshEnabled = value)
        val withKeys =
            if (value == true && (updated.sshPrivateKey == null || updated.sshPublicKey == null)) {
                val pair = sshKeyGenerator.generate("kube-of-pie:$name")
                updated.copy(sshPrivateKey = pair.privateKey, sshPublicKey = pair.publicKey)
            } else {
                updated
            }
        repository.update(withKeys)
    }

    /** Returns the user's OpenSSH-formatted Ed25519 private key, or `null` when not generated. */
    fun getSshPrivateKey(name: String): String? =
        repository.findById(name).orElse(null)?.sshPrivateKey

    /** Public-key counterpart to [getSshPrivateKey]. */
    fun getSshPublicKey(name: String): String? =
        repository.findById(name).orElse(null)?.sshPublicKey

    /**
     * Throws [IllegalArgumentException] if [name] is not a valid POSIX user name shape.
     * Exposed so the future web UI can validate form input without a round-trip.
     */
    fun validateName(name: String) {
        require(USER_NAME.matches(name)) {
            "invalid user name '$name': must match ${USER_NAME.pattern}"
        }
    }

    private fun mutate(name: String, transform: (UserEntity) -> UserEntity) {
        val current = repository.findById(name).orElse(null)
            ?: throw IllegalArgumentException("user '$name' is not registered")
        repository.update(transform(current))
    }

    private fun UserEntity.toUser() = User(
        name = name,
        password = password,
        sshEnabled = sshEnabled,
        sshPrivateKey = sshPrivateKey,
        sshPublicKey = sshPublicKey,
    )

    private companion object {
        private val USER_NAME = Regex("^[a-z_][a-z0-9_-]*$")
    }
}
