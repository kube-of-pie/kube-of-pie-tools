package kubeofpie.imagegenerator

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UnattendedRendererTest {

    private val renderer = UnattendedRenderer()

    private fun model(
        keymap: String? = null,
        hostname: String = "rpi-master",
        wifiEnabled: Boolean = false,
        wifiSsid: String? = null,
        wifiPassphrase: String? = null,
        dns: String? = null,
        users: List<UserEntry> = emptyList(),
        sshdEnabled: Boolean = false,
        timezone: String? = null,
        ntp: String? = null,
        additionalKernelArgs: String? = null,
    ) = UnattendedModel(
        keymap = keymap,
        hostname = hostname,
        interfaces = "auto lo\niface lo inet loopback\n",
        wifiEnabled = wifiEnabled,
        wifiSsid = wifiSsid,
        wifiPassphrase = wifiPassphrase,
        dns = dns,
        users = users,
        sshdEnabled = sshdEnabled,
        timezone = timezone,
        ntp = ntp,
        additionalKernelArgs = additionalKernelArgs,
    )

    @Test
    fun `fills every placeholder when all model fields are set`() {
        val out = renderer.render(
            model(
                keymap = "fr fr",
                hostname = "rpi-master",
                wifiEnabled = true,
                wifiSsid = "homewifi",
                wifiPassphrase = "s3cret",
                dns = "1.1.1.1",
                users = listOf(
                    UserEntry("alice", password = "pw1", sshPublicKey = "ssh-ed25519 AAA alice"),
                    UserEntry("bob", password = "pw2", sshPublicKey = "ssh-ed25519 BBB bob"),
                ),
                sshdEnabled = true,
                timezone = "Europe/Paris",
                ntp = "chrony",
                additionalKernelArgs = "cgroup_memory=1 cgroup_enable=memory",
            ),
        )
        assertContains(out, "KEYMAP=\"fr fr\"")
        assertContains(out, "HOSTNAME=\"rpi-master\"")
        assertContains(out, "WIFI_ENABLED=\"1\"")
        assertContains(out, "WPA_SUPPLICANT_SSID=\"homewifi\"")
        assertContains(out, "WPA_SUPPLICANT_PASSPHRASE=\"s3cret\"")
        assertContains(out, "DNS=\"1.1.1.1\"")
        assertContains(out, "USERS=\"alice bob\"")
        assertContains(out, "USER_alice_PASSWORD=\"pw1\"")
        assertContains(out, "USER_alice_SSH_KEY=\"ssh-ed25519 AAA alice\"")
        assertContains(out, "USER_bob_PASSWORD=\"pw2\"")
        assertContains(out, "USER_bob_SSH_KEY=\"ssh-ed25519 BBB bob\"")
        assertContains(out, "SSHD_ENABLED=\"1\"")
        assertContains(out, "TIMEZONE=\"Europe/Paris\"")
        assertContains(out, "NTP=\"chrony\"")
        assertContains(out, "ADDITIONAL_KERNEL_ARGS=\"cgroup_memory=1 cgroup_enable=memory\"")
    }

    @Test
    fun `leaves disabled flags as empty strings (safe no-op in the script)`() {
        val out = renderer.render(model(wifiEnabled = false, sshdEnabled = false))
        assertContains(out, "WIFI_ENABLED=\"\"")
        assertContains(out, "WPA_SUPPLICANT_SSID=\"\"")
        assertContains(out, "WPA_SUPPLICANT_PASSPHRASE=\"\"")
        assertContains(out, "SSHD_ENABLED=\"\"")
    }

    @Test
    fun `unset optional fields render as empty strings`() {
        val out = renderer.render(model())
        assertContains(out, "KEYMAP=\"\"")
        assertContains(out, "DNS=\"\"")
        assertContains(out, "TIMEZONE=\"\"")
        assertContains(out, "NTP=\"\"")
        assertContains(out, "ADDITIONAL_KERNEL_ARGS=\"\"")
    }

    @Test
    fun `empty user list keeps USERS empty and adds no per-user lines`() {
        val out = renderer.render(model(users = emptyList()))
        assertContains(out, "USERS=\"\"")
        assertNoPerUserAssignment(out)
    }

    @Test
    fun `omits per-user password line when password is null`() {
        val out = renderer.render(
            model(users = listOf(UserEntry("root", password = null, sshPublicKey = "ssh-ed25519 AAA"))),
        )
        assertContains(out, "USERS=\"root\"")
        assertContains(out, "USER_root_SSH_KEY=\"ssh-ed25519 AAA\"")
        assertFalse(perUserAssignment("root", "PASSWORD").containsMatchIn(out), "no PASSWORD line expected: $out")
    }

    @Test
    fun `omits per-user ssh key line when sshPublicKey is null`() {
        val out = renderer.render(
            model(users = listOf(UserEntry("root", password = "secret", sshPublicKey = null))),
        )
        assertContains(out, "USERS=\"root\"")
        assertContains(out, "USER_root_PASSWORD=\"secret\"")
        assertFalse(perUserAssignment("root", "SSH_KEY").containsMatchIn(out), "no SSH_KEY line expected: $out")
    }

    @Test
    fun `embeds the interfaces blob verbatim, preserving newlines`() {
        val out = renderer.render(model())
        assertContains(out, "INTERFACES=\"auto lo\niface lo inet loopback\n\"")
    }

    @Test
    fun `function bodies leave shell variable expansions intact (noparse block)`() {
        // The function bodies live inside <#noparse> so the shell-side ${...}
        // patterns (which would otherwise be interpreted as FreeMarker
        // interpolations) survive verbatim and the script remains executable.
        val out = renderer.render(model())
        assertContains(out, "logger -st \"\${0##*/}\" \"\$1\"")
        assertContains(out, "USER_PASSWORD=\$(eval \"echo \\\$USER_\${USER}_PASSWORD\")")
        assertContains(out, "USER_SSH_KEY=\$(eval \"echo \\\$USER_\${USER}_SSH_KEY\")")
    }

    private fun assertContains(haystack: String, needle: String) {
        assertTrue(haystack.contains(needle), "expected to find '$needle' in:\n$haystack")
    }

    private fun perUserAssignment(name: String, suffix: String): Regex =
        Regex("""^USER_${Regex.escape(name)}_$suffix=""", RegexOption.MULTILINE)

    private fun assertNoPerUserAssignment(out: String) {
        val pattern = Regex("""^USER_[a-z][a-z0-9_-]*_(PASSWORD|SSH_KEY)=""", RegexOption.MULTILINE)
        val match = pattern.find(out)
        assertTrue(match == null, "unexpected per-user assignment line: ${match?.value}")
    }
}
