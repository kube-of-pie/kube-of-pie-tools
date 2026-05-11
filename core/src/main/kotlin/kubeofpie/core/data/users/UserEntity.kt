package kubeofpie.core.data.users

import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity

/**
 * Persistence row in the `users` SQLite table. Public callers see
 * [kubeofpie.core.business.users.User] (the DTO returned by
 * [kubeofpie.core.business.users.UserManager]); this entity lives next to its
 * [UserRepository] in the `data.users` package. Column mapping uses the global
 * underscore naming strategy configured in `application.yml`, so `sshPrivateKey`
 * resolves to the `ssh_private_key` column.
 */
@MappedEntity("users")
data class UserEntity(
    @field:Id val name: String,
    val password: String? = null,
    val sshEnabled: Boolean? = null,
    val sshPrivateKey: String? = null,
    val sshPublicKey: String? = null,
)
