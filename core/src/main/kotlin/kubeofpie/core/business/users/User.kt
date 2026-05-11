package kubeofpie.core.business.users

/**
 * Public DTO returned by [UserManager]. Mirrors [UserEntity]'s shape but kept distinct
 * so the persistence type stays internal to the module. `null` on any field means the
 * value has not been set yet; for `sshPrivateKey` / `sshPublicKey` it additionally
 * means the lazy generation has not yet been triggered.
 */
data class User(
    val name: String,
    val password: String? = null,
    val sshEnabled: Boolean? = null,
    val sshPrivateKey: String? = null,
    val sshPublicKey: String? = null,
)
