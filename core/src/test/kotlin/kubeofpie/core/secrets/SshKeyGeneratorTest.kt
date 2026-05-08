package kubeofpie.core.secrets

import java.util.Base64
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.util.OpenSSHPrivateKeyUtil
import org.bouncycastle.util.io.pem.PemReader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SshKeyGeneratorTest {

    @Test
    fun `private key is OpenSSH-PEM framed`() {
        val pair = SshKeyGenerator().generate("kube-of-pie:test")

        assertTrue(
            pair.privateKey.startsWith("-----BEGIN OPENSSH PRIVATE KEY-----"),
            "expected OpenSSH PEM header, got: ${pair.privateKey.take(60)}"
        )
        assertTrue(
            pair.privateKey.trimEnd().endsWith("-----END OPENSSH PRIVATE KEY-----"),
            "expected OpenSSH PEM footer, got: ${pair.privateKey.takeLast(60)}"
        )
    }

    @Test
    fun `public key is an ssh-ed25519 line carrying the requested comment`() {
        val pair = SshKeyGenerator().generate("kube-of-pie:test")

        val parts = pair.publicKey.split(" ")
        assertEquals(3, parts.size, "expected 'ssh-ed25519 <base64> <comment>': ${pair.publicKey}")
        assertEquals("ssh-ed25519", parts[0])
        assertEquals("kube-of-pie:test", parts[2])
        // base64 of an Ed25519 OpenSSH public-key blob decodes without throwing
        Base64.getDecoder().decode(parts[1])
    }

    @Test
    fun `private key parses back to the same public key`() {
        val pair = SshKeyGenerator().generate("kube-of-pie:test")

        val privateBlob = PemReader(pair.privateKey.reader()).use { it.readPemObject().content }
        val parsed = OpenSSHPrivateKeyUtil.parsePrivateKeyBlob(privateBlob) as Ed25519PrivateKeyParameters
        val derivedPublicBlob = parsed.generatePublicKey().encoded
        val derivedPublicBase64 = Base64.getEncoder().encodeToString(
            org.bouncycastle.crypto.util.OpenSSHPublicKeyUtil.encodePublicKey(parsed.generatePublicKey())
        )

        val emittedPublicBase64 = pair.publicKey.split(" ")[1]
        assertEquals(emittedPublicBase64, derivedPublicBase64)
        // sanity-check the raw 32-byte Ed25519 public key round-trips too
        assertEquals(32, derivedPublicBlob.size)
    }

    @Test
    fun `successive generations produce distinct material`() {
        val generator = SshKeyGenerator()

        val first = generator.generate("kube-of-pie:test")
        val second = generator.generate("kube-of-pie:test")

        assertNotEquals(first.privateKey, second.privateKey)
        assertNotEquals(first.publicKey, second.publicKey)
    }
}
