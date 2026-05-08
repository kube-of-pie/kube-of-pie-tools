package kubeofpie.core.secrets

/**
 * A freshly generated SSH key, in the OpenSSH on-disk formats:
 *
 *  - [privateKey] is a PEM blob framed with `-----BEGIN OPENSSH PRIVATE KEY-----`
 *    (the format `ssh-keygen` writes by default).
 *  - [publicKey] is a single line `ssh-ed25519 <base64> <comment>` suitable for
 *    pasting into an `authorized_keys` file.
 */
data class SshKeyPair(
    val privateKey: String,
    val publicKey: String,
)
