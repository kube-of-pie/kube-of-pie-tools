package kubeofpie.imagegenerator

/**
 * Typed input to the `unattended.sh.ftl` FreeMarker template. Every string field is
 * expected to be **already shell-escaped** ([ShellQuote.escape]) — the template
 * embeds values inside double-quoted shell strings without further processing, so
 * unescaped `"`, `` ` ``, `$`, or `\` would break the boot script on the Pi.
 *
 * Construction lives in [GenerateCommand]; the renderer
 * ([UnattendedRenderer]) is intentionally dumb so unit tests can pin its golden
 * output without setting up the rest of the application.
 */
data class UnattendedModel(
    val keymap: String?,
    val hostname: String,
    val interfaces: String,
    val wifiEnabled: Boolean,
    val wifiSsid: String?,
    val wifiPassphrase: String?,
    val dns: String?,
    val users: List<UserEntry>,
    val sshdEnabled: Boolean,
    val timezone: String?,
    val ntp: String?,
    val additionalKernelArgs: String?,
)

data class UserEntry(
    val name: String,
    val password: String?,
    val sshPublicKey: String?,
)
