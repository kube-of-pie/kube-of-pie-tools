package kubeofpie.core.secrets

import jakarta.inject.Singleton
import java.io.StringWriter
import java.security.SecureRandom
import java.util.Base64
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.util.OpenSSHPrivateKeyUtil
import org.bouncycastle.crypto.util.OpenSSHPublicKeyUtil
import org.bouncycastle.util.io.pem.PemObject
import org.bouncycastle.util.io.pem.PemWriter

/**
 * Generates Ed25519 SSH key pairs in the OpenSSH on-disk formats. Used by the
 * variable registry to lazily populate `setup.users.<name>.ssh.private_key`
 * and `…ssh.public_key` the first time either is read.
 *
 * Ed25519 is the only supported algorithm: modern, fixed-size, no parameter-
 * choice surface. Bouncy Castle's lightweight API owns both generation and the
 * OpenSSH private-key serialization (the JDK ships the algorithm but not the
 * `-----BEGIN OPENSSH PRIVATE KEY-----` framing).
 */
@Singleton
class SshKeyGenerator {

    fun generate(comment: String): SshKeyPair {
        val keyPair = Ed25519KeyPairGenerator()
            .apply { init(Ed25519KeyGenerationParameters(SecureRandom())) }
            .generateKeyPair()
        val privateParams = keyPair.private as Ed25519PrivateKeyParameters
        val publicParams = keyPair.public as Ed25519PublicKeyParameters

        return SshKeyPair(
            privateKey = encodeOpenSshPrivateKey(privateParams),
            publicKey = encodeOpenSshPublicKey(publicParams, comment),
        )
    }

    private fun encodeOpenSshPrivateKey(key: Ed25519PrivateKeyParameters): String {
        val blob = OpenSSHPrivateKeyUtil.encodePrivateKey(key)
        val sw = StringWriter()
        PemWriter(sw).use { it.writeObject(PemObject("OPENSSH PRIVATE KEY", blob)) }
        return sw.toString()
    }

    private fun encodeOpenSshPublicKey(key: Ed25519PublicKeyParameters, comment: String): String {
        val blob = OpenSSHPublicKeyUtil.encodePublicKey(key)
        val base64 = Base64.getEncoder().encodeToString(blob)
        return "ssh-ed25519 $base64 $comment"
    }
}
